package org.example.uploader.services.impls;

import org.example.uploader.repos.VideoRepository;
import org.example.uploader.services.S3MultipartService;
import org.example.uploader.services.UploadMaintenanceService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;

@Service
public class UploadMaintenanceServiceImpl implements UploadMaintenanceService {

    private static final Logger LOGGER = LoggerFactory.getLogger(UploadMaintenanceServiceImpl.class);

    private final VideoRepository repository;
    private final S3MultipartService s3;
    private final Clock clock;
    private final Duration staleTimeout;

    public UploadMaintenanceServiceImpl(
            VideoRepository repository,
            S3MultipartService s3,
            Clock clock,
            @Value("${uploader.upload.stale-timeout:24h}") Duration staleTimeout
    ) {
        this.repository = repository;
        this.s3 = s3;
        this.clock = clock;
        this.staleTimeout = staleTimeout;
    }

    @Override
    @Scheduled(fixedDelayString = "${uploader.upload.cleanup-interval:1h}")
    public void abortStaleUploads() {
        Instant now = Instant.now(clock);
        repository.findStaleInitiatedBefore(now.minus(staleTimeout)).forEach(video -> {
            try {
                LOGGER.info("Cleaning up stale multipart upload: videoId={}", video.getId());
                s3.abort(video.getObjectKey(), video.getUploadId());
                video.markAborted("Upload expired", now);
                repository.save(video);
                LOGGER.info("Stale multipart upload aborted: videoId={}", video.getId());
            } catch (RuntimeException exception) {
                LOGGER.warn("Could not clean up stale upload {}", video.getId(), exception);
            }
        });
    }
}
