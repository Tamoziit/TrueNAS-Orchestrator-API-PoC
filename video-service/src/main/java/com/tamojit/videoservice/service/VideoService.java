package com.tamojit.videoservice.service;

import com.tamojit.videoservice.client.NasOrchestratorClient;
import com.tamojit.videoservice.event.VideoUploadedEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;

@Service
@Slf4j
@RequiredArgsConstructor
public class VideoService {
    private final KafkaTemplate<String, VideoUploadedEvent> kafkaTemplate;
    private final NasOrchestratorClient nasOrchestratorClient;

    private static final String VIDEO_UPLOADED_TOPIC = "video.uploaded"; // kafka topic

    public String uploadVideo(String movieId, MultipartFile file) throws IOException {
        log.info("Uploading video {} to NAS : file - {}", movieId, file.getOriginalFilename());

        // format: raw/{movieId}   (nas-orchestrator names the file after the original filename)
        String dirPath = "raw/" + movieId;
        String nasPath = nasOrchestratorClient.uploadFile(dirPath, file);
        log.info("Video uploaded to NAS successfully: path = {}", nasPath);

        // Publishing upload event to kafka - for encoding-service to start ffmpeg encoding
        VideoUploadedEvent videoUploadedEvent = new VideoUploadedEvent(
            movieId,
            nasPath,
            file.getOriginalFilename(),
            file.getSize()
        );

        kafkaTemplate.send(VIDEO_UPLOADED_TOPIC, movieId, videoUploadedEvent);
        log.info("Video uploaded event published to Kafka: key = {}", nasPath);

        return nasPath;
    }
}
