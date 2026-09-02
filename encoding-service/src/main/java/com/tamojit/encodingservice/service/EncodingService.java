package com.tamojit.encodingservice.service;

import com.tamojit.encodingservice.event.VideoEncodedEvent;
import com.tamojit.encodingservice.event.VideoUploadedEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.List;

@Service
@Slf4j
@RequiredArgsConstructor
public class EncodingService {
    private final S3Client s3Client;
    private final KafkaTemplate<String, VideoEncodedEvent> kafkaTemplate;

    @Value("${aws.s3.bucket-name}")
    private String bucketName;

    @Value("${ffmpeg.path}")
    private String ffmpegPath;

    @Value("${encoding.base-path}")
    private String basePath;

    private static final String VIDEO_ENCODED_TOPIC = "video.encoded";

    // Video qualities to encode
    // format: resolution, bitrate, height
    private static final List<int[]> VIDEO_QUALITIES = Arrays.asList(
        new int[]{1920, 5000, 1080}, // 1920px resolution, 1080p, 5000 kbps bitrate
        new int[]{1280, 2800, 720}, // 720p
        new int[]{854, 1200, 480}, // 480p
        new int[]{640, 800, 360} // 360p
    );
    // higher bitrate: better quality, lower bitrate: lesser quality (but better for low-bandwidth network connections)

    /*
     * Encoding Pipeline
     * FLOW:
     * -> Download raw video from S3
     * -> Encode to multiple qualities using ffmpeg
     * -> Generate HLS playlist (.ts segments) for each quality
     * -> Create master playlist (master.m3u8 playlist) having pointers to segments for each quality
     * -> Upload all encoded files back to S3
     * -> Publish video.encoded event to Kafka
     */
    public void encodeVideo(VideoUploadedEvent event) {
        log.info("Video encoded event received: {}", event.getMovieId());

        // creating unique path for temp download of video
        String jobPath = basePath + "/" + event.getMovieId();

        try {
            // creating temp directories
            Files.createDirectories(Paths.get(jobPath));
            Files.createDirectories(Paths.get(jobPath + "/encoded"));

            // S1: downloading raw file from S3
            String localVideoPath = jobPath + "/raw_video.mp4";
            downloadFromS3(event.getVideoKey(), localVideoPath);
            log.info("Raw Video downloaded to: {}", localVideoPath);

            // S2, S3: Encoding to multiple qualities & generating HLS playlist
            for (int[] qualities : VIDEO_QUALITIES) {
                int width = qualities[0];
                int bitrate = qualities[1];
                int height = qualities[2];

                String qualityDir = jobPath + "/encoded/" + height + "p";
                Files.createDirectories(Paths.get(qualityDir));

                encodeToHls(localVideoPath, qualityDir, width, height, bitrate);
                log.info("Encoded {}p successfully", height);
            }

            // S4: generating master playlist
            String masterPlaylistPath = jobPath + "/encoded/master.m3u8";
            generateMasterPlaylist(masterPlaylistPath);
            log.info("Master playlist generated successfully");

            // S5: uploading all encoded files back to S3
            String encodedPrefix = "encoded/" + event.getMovieId() + "/";
            uploadEncodedFilesToS3(jobPath + "/encoded", encodedPrefix);
            log.info("All encoded files uploaded to S3 successfully");

            // S6: publishing video.encoded event
            String masterPlaylistKey = encodedPrefix + "master.m3u8";
            String hlsUrl = "https://" + bucketName + ".s3.amazonaws.com/" + masterPlaylistKey;

            VideoEncodedEvent encodedEvent = new VideoEncodedEvent(
                event.getMovieId(),
                hlsUrl,
                masterPlaylistKey,
                true,
                null
            );

            kafkaTemplate.send(VIDEO_ENCODED_TOPIC, event.getMovieId(), encodedEvent);
            log.info("Video encoded event published for movie: {}", event.getMovieId());
        } catch (Exception e) {
            log.error("Encoding failed for movie: {} - {}", event.getMovieId(), e.getMessage());

            // publishing failure event
            VideoEncodedEvent failureEvent = new VideoEncodedEvent(
                event.getMovieId(),
                null,
                null,
                false,
                e.getMessage()
            );

            kafkaTemplate.send(VIDEO_ENCODED_TOPIC, event.getMovieId(), failureEvent);
        } finally {
            // cleanup job
            cleanupTempFiles(jobPath);
        }
    }

    // download from S3 to local path
    private void downloadFromS3(String s3Key, String localVideoPath) {
        GetObjectRequest getObjectRequest = GetObjectRequest.builder()
            .bucket(bucketName)
            .key(s3Key)
            .build();

        s3Client.getObject(getObjectRequest, Paths.get(localVideoPath));
    }

    // encoding raw file to separate qualities with ffmpeg
    private void encodeToHls(String inputPath, String outputDir, int width, int height, int bitrate) throws IOException, InterruptedException {
        String playlistPath = outputDir + "/playlist.m3u8";
        String segmentPattern = outputDir + "/segment_%03d.ts";

        // ffmpeg cmd for HLS encoding
        List<String> command = Arrays.asList(
            ffmpegPath,
            "-i", inputPath, // input file
            "-vf", "scale=" + width + ":" + height, // scale to resolution
            "-c:v", "libx264", // video codec
            "-b:v", bitrate + "k", // video bitrate
            "-c:a", "aac", // audio codec
            "-b:a", "128k", // audio bitrate
            "-hls_time", "10", // 10 second segments
            "-hls_list_size", "0", // keep all segments
            "-hls_segment_filename", segmentPattern, // segment naming
            "-f", "hls", // output format - HLS
            playlistPath // output playlist path
        );

        ProcessBuilder processBuilder = new ProcessBuilder(command);
        processBuilder.redirectErrorStream(true);
        processBuilder.inheritIO();
        Process process = processBuilder.start();

        int exitCode = process.waitFor();
        if (exitCode != 0) {
            throw new RuntimeException("ffmpeg Encoding failed for playlist: " + playlistPath + " with exit code: " + exitCode);
        }
    }

    // generating master playlist
    private void generateMasterPlaylist(String masterPlaylistPath) throws IOException {
        StringBuilder master = new StringBuilder();
        master.append("#EXTM3U\n"); // extended m3us8 playlist
        master.append("EXT-X-VERSION:3\n\n");

        // adding each quality to master playlist
        int[][] qualities = {
            {1920, 5000, 1080},
            {1280, 2800, 720},
            {854, 1200, 480},
            {640, 800, 360}
        };

        for (int[] q : qualities) {
            int width = q[0];
            int bitrate = q[1];
            int height = q[2];

            master.append("#EXT-X-STREAM-INF:BANDWIDTH=")
                .append(bitrate * 1000)
                .append(", RESOLUTION=").append(width).append("x").append(height)
                .append(",CODECS=\"avc1.42e01e,mp4a.40.2\"\n");
            master.append(height).append("p/playlist.m3u8\n\n");
        }

        Files.writeString(Paths.get(masterPlaylistPath), master.toString());
    }

    // uploading local files to S3
    private void uploadEncodedFilesToS3(String localDir, String s3Prefix) throws IOException {
        File directory = new File(localDir);
        uploadDirectoryToS3(directory, localDir, s3Prefix);
    }

    private void uploadDirectoryToS3(File dir, String baseDir, String s3Prefix) throws IOException {
        for (File file : dir.listFiles()) {
            if (file.isDirectory()) {
                uploadDirectoryToS3(file, baseDir, s3Prefix);
            } else {
                String relativePath = file.getAbsolutePath()
                    .substring(baseDir.length() + 1)
                    .replace("\\", "/");

                String s3Key = s3Prefix + relativePath;
                String contentType = file.getName()
                    .endsWith(".m3u8") ? "application/x-mpegURL" : "video/MP2T";

                PutObjectRequest putObjectRequest = PutObjectRequest.builder()
                    .bucket(bucketName)
                    .key(s3Key)
                    .contentType(contentType)
                    .build();

                s3Client.putObject(putObjectRequest, RequestBody.fromFile(file));
                log.info("Uploaded : {}", s3Key);
            }
        }
    }

    // cleanup job after encoding
    private void cleanupTempFiles(String jobPath) {
        try {
            Path dirPath = Paths.get(jobPath);
            if (Files.exists(dirPath)) {
                Files.walk(dirPath)
                    .sorted(java.util.Comparator.reverseOrder())
                    .map(Path::toFile)
                    .forEach(File::delete);

                log.info("Cleaned up temp files at path: {}", jobPath);
            }
        } catch (IOException e) {
            log.warn("Failed to clean up temp files at path: {} - {}", jobPath, e.getMessage());
        }
    }
}