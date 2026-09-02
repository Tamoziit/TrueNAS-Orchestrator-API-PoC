package com.tamojit.streamingservice.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class StreamingResponseDto {
    private String movieId;
    private String streamingURL; // pre-signed HLS master playlist URL
    private String quality; // available qualities
    private long expiredInMinutes; // URL expiry time
}
