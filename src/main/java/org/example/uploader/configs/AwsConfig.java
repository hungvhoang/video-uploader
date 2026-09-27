package org.example.uploader.configs;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;

import java.time.Clock;

@Configuration
@EnableConfigurationProperties(S3Properties.class)
public class AwsConfig {

    @Bean
    S3Client s3Client(S3Properties properties) {
        return S3Client.builder()
                .endpointOverride(properties.endpoint())
                .region(Region.of(properties.region()))
                .credentialsProvider(credentials(properties))
                .serviceConfiguration(s3Configuration(properties))
                .build();
    }

    @Bean
    S3Presigner s3Presigner(S3Properties properties) {
        return S3Presigner.builder()
                .endpointOverride(properties.endpoint())
                .region(Region.of(properties.region()))
                .credentialsProvider(credentials(properties))
                .serviceConfiguration(s3Configuration(properties))
                .build();
    }

    @Bean
    Clock clock() {
        return Clock.systemUTC();
    }

    private StaticCredentialsProvider credentials(S3Properties properties) {
        return StaticCredentialsProvider.create(
                AwsBasicCredentials.create(properties.accessKey(), properties.secretKey()));
    }

    private software.amazon.awssdk.services.s3.S3Configuration s3Configuration(S3Properties properties) {
        return software.amazon.awssdk.services.s3.S3Configuration.builder()
                .pathStyleAccessEnabled(properties.pathStyleAccessEnabled())
                .build();
    }
}
