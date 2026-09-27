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
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

@Service
public class UploadServiceImpl implements UploadService {

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
        String uploadId = s3.initiate(objectKey, contentType);
        Video video = Video.initiate(filename, objectKey, contentType, size,
                plan.partSize(), plan.partCount(), uploadId, Instant.now(clock));
        repository.save(video);
        return new InitiatedUpload(video.getId(), plan.partSize(), plan.partCount());
    }

    @Override
    public List<PresignedPart> presignParts(String videoId, List<Integer> partNumbers) {
        Video video = requireInitiated(videoId);
        if (partNumbers == null || partNumbers.isEmpty() || partNumbers.size() > 20) {
            throw new IllegalArgumentException("Request between 1 and 20 part URLs");
        }
        return partNumbers.stream().distinct().map(partNumber -> {
            if (partNumber < 1 || partNumber > video.getPartCount()) {
                throw new IllegalArgumentException("Invalid part number: " + partNumber);
            }
            return new PresignedPart(partNumber,
                    s3.presignPart(video.getObjectKey(), video.getUploadId(), partNumber).toString());
        }).toList();
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
        s3.complete(video.getObjectKey(), video.getUploadId(), ordered);
        video.markReady(Instant.now(clock));
        return repository.save(video);
    }

    @Override
    public void abort(String videoId) {
        Video video = requireVideo(videoId);
        if (video.getStatus() == VideoStatus.ABORTED) {
            return;
        }
        requireInitiated(video);
        s3.abort(video.getObjectKey(), video.getUploadId());
        video.markAborted("Cancelled", Instant.now(clock));
        repository.save(video);
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
