package com.tamojit.encodingservice.service;

import com.tamojit.encodingservice.event.VideoUploadedEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Service;

@Service
@Slf4j
@RequiredArgsConstructor
public class VideoEventConsumer {
    private final EncodingService encodingService;

    /*
     * Listens to 'video.uploaded' Kafka topic
     * Triggered when video-service uploads a raw video to s3
     * FLOW:
     * video-service --> S3 upload --> Kafka (video.uploaded) --> encoding-service consumer --> ffmpeg encoding --> S3 encoded upload --> Kafka (video.encoded)
     */
    @KafkaListener(
        topics = "video.uploaded",
        groupId = "encoding-service-group"
    )
    public void consumeVideoUploadedEvent(VideoUploadedEvent event) {
        log.info("Consumed VideoUploadedEvent for movie: {}, file: {}", event.getMovieId(), event.getOriginalFileName());

        try {
            encodingService.encodeVideo(event);
        } catch (Exception e) {
            log.error("Failed to process encoding for movie: {} - {}", event.getMovieId(), e.getMessage());
        }
    }
}
