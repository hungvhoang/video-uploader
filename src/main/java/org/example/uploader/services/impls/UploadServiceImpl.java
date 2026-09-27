package org.example.uploader.services.impls;

import org.example.uploader.dtos.CompletedPart;
import org.example.uploader.dtos.InitiatedUpload;
import org.example.uploader.dtos.PresignedPart;
import org.example.uploader.exceptions.VideoNotFoundException;
import org.example.uploader.models.Video;
import org.example.uploader.models.VideoStatus;
import org.example.uploader.repos.VideoRepository;
import org.example.uploader.services.S3MultipartService;
import org.example.uploader.services.UploadService;
import org.example.uploader.utils.PartSizePolicy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import java.util.stream.IntStream;

@Service
public class UploadServiceImpl implements UploadService {

    private static final Logger LOGGER = LoggerFactory.getLogger(UploadServiceImpl.class);
    private static final int PRESIGN_BATCH_SIZE = 20;

    private final VideoRepository repository;
    private final S3MultipartService s3;
    private final Clock clock;

    public UploadServiceImpl(VideoRepository repository, S3MultipartService s3, Clock clock) {
        this.repository = repository;
        this.s3 = s3;
        this.clock = clock;
    }

    @Override
    public InitiatedUpload initiate(String filename, String contentType, long size) {
        if (filename == null || filename.isBlank()) {
            throw new IllegalArgumentException("Filename is required");
        }
        if (contentType == null || !contentType.startsWith("video/")) {
            throw new IllegalArgumentException("Only video files are supported");
        }

        var plan = PartSizePolicy.calculate(size);
        String safeFilename = filename.replaceAll("[^a-zA-Z0-9._-]", "_");
        String objectKey = "videos/" + UUID.randomUUID() + "/" + safeFilename;
        LOGGER.info("Creating multipart upload: size={}, partSize={}, partCount={}",
                size, plan.partSize(), plan.partCount());
        String uploadId = s3.initiate(objectKey, contentType);
        Video video = Video.initiate(filename, objectKey, contentType, size,
                plan.partSize(), plan.partCount(), uploadId, Instant.now(clock));
        repository.save(video);
        List<Integer> initialPartNumbers = IntStream.rangeClosed(1, Math.min(plan.partCount(), PRESIGN_BATCH_SIZE))
                .boxed()
                .toList();
        List<PresignedPart> initialParts = presignParts(video, initialPartNumbers);
        LOGGER.info("Multipart upload initiated: videoId={}, objectKey={}, initialPresignedParts={}",
                video.getId(), objectKey, initialParts.size());
        return new InitiatedUpload(video.getId(), plan.partSize(), plan.partCount(), initialParts);
    }

    @Override
    public List<PresignedPart> presignParts(String videoId, List<Integer> partNumbers) {
        Video video = requireInitiated(videoId);
        if (partNumbers == null || partNumbers.isEmpty() || partNumbers.size() > PRESIGN_BATCH_SIZE) {
            throw new IllegalArgumentException("Request between 1 and 20 part URLs");
        }
        return presignParts(video, partNumbers);
    }

    private List<PresignedPart> presignParts(Video video, List<Integer> partNumbers) {
        List<PresignedPart> parts = partNumbers.stream().distinct().map(partNumber -> {
            if (partNumber < 1 || partNumber > video.getPartCount()) {
                throw new IllegalArgumentException("Invalid part number: " + partNumber);
            }
            return new PresignedPart(partNumber,
                    s3.presignPart(video.getObjectKey(), video.getUploadId(), partNumber).toString());
        }).toList();
        LOGGER.info("Generated presigned part URLs: videoId={}, partNumbers={}",
                video.getId(), parts.stream().map(PresignedPart::partNumber).toList());
        return parts;
    }

    @Override
    public Video complete(String videoId, List<CompletedPart> parts) {
        Video video = requireVideo(videoId);
        if (video.getStatus() == VideoStatus.READY) {
            return video;
        }
        requireInitiated(video);
        if (parts == null || parts.size() != video.getPartCount()) {
            throw new IllegalArgumentException("All uploaded parts are required");
        }
        List<CompletedPart> ordered = parts.stream()
                .sorted(Comparator.comparingInt(CompletedPart::partNumber))
                .toList();
        for (int index = 0; index < ordered.size(); index++) {
            CompletedPart part = ordered.get(index);
            if (part.partNumber() != index + 1 || part.eTag() == null || part.eTag().isBlank()) {
                throw new IllegalArgumentException("Invalid completed parts");
            }
        }
        LOGGER.info("Completing multipart upload: videoId={}, parts={}", videoId, ordered.size());
        s3.complete(video.getObjectKey(), video.getUploadId(), ordered);
        video.markReady(Instant.now(clock));
        Video completed = repository.save(video);
        LOGGER.info("Multipart upload completed: videoId={}, status={}", videoId, completed.getStatus());
        return completed;
    }

    @Override
    public void abort(String videoId) {
        Video video = requireVideo(videoId);
        if (video.getStatus() == VideoStatus.ABORTED) {
            LOGGER.info("Multipart upload already aborted: videoId={}", videoId);
            return;
        }
        requireInitiated(video);
        LOGGER.info("Aborting multipart upload: videoId={}", videoId);
        s3.abort(video.getObjectKey(), video.getUploadId());
        video.markAborted("Cancelled", Instant.now(clock));
        repository.save(video);
        LOGGER.info("Multipart upload aborted: videoId={}", videoId);
    }

    @Override
    public Video requireVideo(String videoId) {
        return repository.findById(videoId)
                .orElseThrow(() -> new VideoNotFoundException(videoId));
    }

    private Video requireInitiated(String videoId) {
        return requireInitiated(requireVideo(videoId));
    }

    private Video requireInitiated(Video video) {
        if (video.getStatus() != VideoStatus.INITIATED) {
            throw new IllegalStateException("Upload is not active");
        }
        return video;
    }
}
