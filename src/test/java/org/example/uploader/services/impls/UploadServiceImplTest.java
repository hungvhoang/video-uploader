package org.example.uploader.services.impls;

import org.example.uploader.dtos.CompletedPart;
import org.example.uploader.models.Video;
import org.example.uploader.models.VideoStatus;
import org.example.uploader.repos.VideoRepository;
import org.example.uploader.services.S3MultipartService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.ArgumentCaptor;
import org.mockito.junit.jupiter.MockitoExtension;

import java.net.URI;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.startsWith;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UploadServiceImplTest {

    @Mock
    private VideoRepository repository;
    @Mock
    private S3MultipartService s3;

    private UploadServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new UploadServiceImpl(repository, s3,
                Clock.fixed(Instant.parse("2026-09-27T10:00:00Z"), ZoneOffset.UTC));
        when(repository.save(any(Video.class))).thenAnswer(invocation -> invocation.getArgument(0));
    }

    @Test
    void initiatesAndCompletesMultipartUpload() {
        when(s3.initiate(startsWith("videos/"), eq("video/mp4"))).thenReturn("upload-123");

        var started = service.initiate("clip.mp4", "video/mp4", 21_000_000L);

        assertThat(started.videoId()).isNotBlank();
        assertThat(started.partSize()).isEqualTo(10L * 1024 * 1024);
        assertThat(started.partCount()).isEqualTo(3);

        var videoCaptor = ArgumentCaptor.forClass(Video.class);
        verify(repository).save(videoCaptor.capture());
        Video video = videoCaptor.getValue();
        when(repository.findById(started.videoId())).thenReturn(Optional.of(video));
        when(s3.presignPart(video.getObjectKey(), "upload-123", 1))
                .thenReturn(URI.create("http://localhost:4566/part-1"));

        assertThat(service.presignParts(started.videoId(), List.of(1)).getFirst().url())
                .isEqualTo("http://localhost:4566/part-1");

        var completedParts = List.of(
                new CompletedPart(1, "etag-1"),
                new CompletedPart(2, "etag-2"),
                new CompletedPart(3, "etag-3"));
        service.complete(started.videoId(), completedParts);

        assertThat(video.getStatus()).isEqualTo(VideoStatus.READY);
        verify(s3).complete(video.getObjectKey(), "upload-123", completedParts);
    }
}
