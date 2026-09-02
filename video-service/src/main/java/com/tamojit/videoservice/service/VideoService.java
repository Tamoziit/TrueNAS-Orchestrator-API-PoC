package com.tamojit.videoservice.service;

import com.tamojit.videoservice.event.VideoUploadedEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;

import java.io.IOException;
import java.util.UUID;

@Service
@Slf4j
@RequiredArgsConstructor
public class VideoService {
    private final S3Client s3Client;
    private final KafkaTemplate<String, VideoUploadedEvent> kafkaTemplate;

    @Value("${aws.s3.bucket-name}")
    private String bucketName;

    private static final String VIDEO_UPLOADED_TOPIC = "video.uploaded"; // kafka topic

    /*
     * Uploading video to S3 and publishing VideoUploadedEvent to Kafka
     * FLOW:
     * -> Receive multipart video file
     * -> Generate unique S3 Key
     * -> Upload file to S3
     * -> Publish VideoUploadedEvent to Kafka
     * -> Encoding service picks up & starts ffmpeg encoding
     */
    public String uploadVideo(String movieId, MultipartFile file) throws IOException {
        log.info("Uploading video {} to S3 : file - {}", movieId, file.getOriginalFilename());

        // Generating unique S3 key for raw video
        // format: raw/movieId/uuid_filename
        String videoKey = "raw/" + movieId + "/" + UUID.randomUUID() + "_" + file.getOriginalFilename();

        // Sending file to S3
        PutObjectRequest putObjectRequest = PutObjectRequest.builder()
            .bucket(bucketName)
            .key(videoKey)
            .contentType(file.getContentType())
            .contentLength(file.getSize())
            .build();

        s3Client.putObject(putObjectRequest,
            RequestBody.fromBytes(file.getBytes()));
        log.info("Video uploaded to S3 successfully: key = {}", videoKey);

        // Publishing upload event to kafka - for encoding-service to start ffmpeg encoding
        VideoUploadedEvent videoUploadedEvent = new VideoUploadedEvent(
            movieId,
            videoKey,
            bucketName,
            file.getOriginalFilename(),
            file.getSize()
        );

        kafkaTemplate.send(VIDEO_UPLOADED_TOPIC, movieId, videoUploadedEvent);
        log.info("Video uploaded event published to Kafka: key = {}", videoKey);

        return videoKey;
    }
}
