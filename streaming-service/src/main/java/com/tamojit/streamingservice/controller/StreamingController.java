package com.tamojit.streamingservice.controller;

import com.tamojit.streamingservice.dto.StreamingResponseDto;
import com.tamojit.streamingservice.service.StreamingService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
@Slf4j
@RequestMapping("/api/v1/stream")
public class StreamingController {
    private final StreamingService streamingService;
    private final RedisTemplate<String, String> redisTemplate;

    private static final String MASTER_PLAYLIST_KEY_PREFIX = "streaming:playlist:";

    @GetMapping("/{movieId}")
    public ResponseEntity<StreamingResponseDto> getStreamingUrl(@PathVariable String movieId) {
        log.info("Getting streaming url for {}", movieId);

        // Get master playlist key from Redis
        String playlistKey = redisTemplate.opsForValue()
            .get(MASTER_PLAYLIST_KEY_PREFIX + movieId);

        log.info(playlistKey);
        if (playlistKey == null) {
            return ResponseEntity.notFound().build();
        }

        StreamingResponseDto responseDto = streamingService.getStreamingUrl(movieId, playlistKey);

        return ResponseEntity.ok(responseDto);
    }

    // getting signed m3u8 HLS playlist for each quality
    @GetMapping("/{movieId}/playlist")
    public ResponseEntity<String> getSignedPlaylistUrl(
        @PathVariable String movieId,
        @RequestParam String path
    ) {
        log.info("Getting playlist url for {}", movieId);

        String signedPlaylist = streamingService.getSignedPlaylist(movieId, path);

        return ResponseEntity.ok()
            .header("Content-Type", "application/x-mpegURL")
            .body(signedPlaylist);
    }
}
