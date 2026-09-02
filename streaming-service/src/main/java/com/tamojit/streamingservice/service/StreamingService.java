package com.tamojit.streamingservice.service;

import com.tamojit.streamingservice.dto.StreamingResponseDto;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.core.ResponseInputStream;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectResponse;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.GetObjectPresignRequest;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.time.Duration;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class StreamingService {
    private final S3Client s3Client;
    private final S3Presigner s3Presigner;
    private final RedisTemplate<String, String> redisTemplate;

    @Value("${aws.s3.bucket-name}")
    private String bucketName;

    @Value("${aws.s3.presigned-url-expiry}")
    private long presignedUrlExpiry; // 60 mins

    // Redis key for caching streaming URLs
    private final static String STREAMING_URL_CACHE_PREFIX = "streaming:url:";

    /*
     * Getting streaming URL for a movie
     * FLOW:
     * -> Check redis cache for existing pre-signed URL
     * -> If cached - return immediately
     * -> If not cached - generate new pre-signed URL from S3
     * -> Caching the URL in Redis
     * -> Return streaming URL
     */
    public StreamingResponseDto getStreamingUrl(String movieId, String playlistKey) {
        log.info("Getting streaming URL for movie: {}", movieId);

        String cacheKey = STREAMING_URL_CACHE_PREFIX + movieId;

        // Checking redis cache first
        String cachedUrl = redisTemplate.opsForValue().get(cacheKey);
        if (cachedUrl != null) {
            log.info("Returning streaming url for movie: {}", movieId);
            return new StreamingResponseDto(
                movieId,
                cachedUrl,
                "1080p, 720p, 480p, 360p",
                presignedUrlExpiry
            );
        }

        // If not in cache - generating new presigned url
        log.info("Generating new presigned url for movie: {}", movieId);
        String presignedUrl = generatePresignedUrl(playlistKey);

        // caching in Redis for 55 mins
        // 5 mins less than actual expiry time to avoid overloaded cache misses [avoids Cache Stampede/Dog-piling]
        redisTemplate.opsForValue().set(
            cacheKey,
            presignedUrl,
            55,
            TimeUnit.MINUTES
        );

        log.info("Streaming URL generated and cached for movie: {}", movieId);

        return new StreamingResponseDto(
            movieId,
            presignedUrl,
            "1080p, 720p, 480p, 360p",
            presignedUrlExpiry
        );
    }

    // Key Method to control security of Signed Streaming private URLs
    public String getSignedPlaylist(String movieId, String playlistPath) {
        // getting base path for the playlist
        String basePath = playlistPath.substring(0, playlistPath.lastIndexOf("/") + 1);

        // reading m3u8 content from S3
        String m3u8Content = readFromS3(playlistPath);

        // rewriting each line that is a segment or playlist reference
        return rewriteM3U8SignedUrls(m3u8Content, basePath);
    }

    // Generating presigned URL for private S3 Object/Video
    private String generatePresignedUrl(String playlistKey) {
        GetObjectRequest getObjectRequest = GetObjectRequest.builder()
            .bucket(bucketName)
            .key(playlistKey)
            .build();

        GetObjectPresignRequest presignRequest = GetObjectPresignRequest.builder()
            .signatureDuration(Duration.ofMinutes(presignedUrlExpiry))
            .getObjectRequest(getObjectRequest)
            .build();

        return s3Presigner.presignGetObject(presignRequest)
            .url()
            .toString();
    }

    // Invalidating cached streaming URL when video is re-encoded/updated
    public void inValidateCache(String movieId) {
        String cacheKey = STREAMING_URL_CACHE_PREFIX + movieId;
        redisTemplate.delete(cacheKey);
        log.info("Cached streaming URL invalidated for movie: {}", movieId);
    }

    private String readFromS3(String s3Key) {
        GetObjectRequest request = GetObjectRequest.builder()
            .bucket(bucketName)
            .key(s3Key)
            .build();

        ResponseInputStream<GetObjectResponse> responseInputStream = s3Client.getObject(request);

        return new BufferedReader(new InputStreamReader(responseInputStream))
            .lines()
            .collect(Collectors.joining("\n"));
    }

    private String rewriteM3U8SignedUrls(String m3u8Content, String basePath) {
        StringBuilder rewritten = new StringBuilder();

        for (String line : m3u8Content.split("\n")) {
            String trimmed = line.trim();

            // skip empty lines & comments
            if (trimmed.isEmpty() || trimmed.startsWith("#")) {
                rewritten.append(line).append("\n");
                continue;
            }

            // segment/playlist reference - building full S3 key & signing it
            String fullKey = basePath + trimmed;
            String signedUrl = generatePresignedUrl(fullKey);

            rewritten.append(signedUrl).append("\n");
        }

        return rewritten.toString();
    }
}
