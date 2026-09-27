package org.example.uploader.configs;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.net.URI;
import java.time.Duration;

@ConfigurationProperties("uploader.s3")
public record S3Properties(
        URI endpoint,
        String region,
        String accessKey,
        String secretKey,
        String bucket,
        boolean pathStyleAccessEnabled,
        Duration presignDuration
) {
}
