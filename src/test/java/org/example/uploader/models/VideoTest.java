package org.example.uploader.models;

import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatIllegalStateException;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;

class VideoTest {

    private static final Instant CREATED_AT = Instant.parse("2026-09-27T10:00:00Z");

    @Test
    void initiatesVideoWithMetadataAndTimestamps() {
        Video video = initiatedVideo();

        assertThat(video.getId()).isNotBlank();
        assertThat(video.getOriginalFilename()).isEqualTo("clip.mp4");
        assertThat(video.getObjectKey()).isEqualTo("videos/clip.mp4");
        assertThat(video.getContentType()).isEqualTo("video/mp4");
        assertThat(video.getSize()).isEqualTo(21_000_000L);
        assertThat(video.getPartSize()).isEqualTo(10_485_760L);
        assertThat(video.getPartCount()).isEqualTo(3);
        assertThat(video.getUploadId()).isEqualTo("upload-123");
        assertThat(video.getStatus()).isEqualTo(VideoStatus.INITIATED);
        assertThat(video.getCreatedAt()).isEqualTo(CREATED_AT);
        assertThat(video.getUpdatedAt()).isEqualTo(CREATED_AT);
        assertThat(video.getCompletedAt()).isNull();
        assertThat(video.getFailureMessage()).isNull();
    }

    @Test
    void rejectsBlankOriginalFilename() {
        assertThatIllegalArgumentException().isThrownBy(() -> initiate(null, "key", "video/mp4", 1, 1, 1, "upload"));
        assertThatIllegalArgumentException().isThrownBy(() -> initiate(" ", "key", "video/mp4", 1, 1, 1, "upload"));
    }

    @Test
    void rejectsBlankObjectKey() {
        assertThatIllegalArgumentException().isThrownBy(() -> initiate("clip.mp4", null, "video/mp4", 1, 1, 1, "upload"));
        assertThatIllegalArgumentException().isThrownBy(() -> initiate("clip.mp4", " ", "video/mp4", 1, 1, 1, "upload"));
    }

    @Test
    void rejectsBlankContentType() {
        assertThatIllegalArgumentException().isThrownBy(() -> initiate("clip.mp4", "key", null, 1, 1, 1, "upload"));
        assertThatIllegalArgumentException().isThrownBy(() -> initiate("clip.mp4", "key", "\t", 1, 1, 1, "upload"));
    }

    @Test
    void rejectsBlankUploadId() {
        assertThatIllegalArgumentException().isThrownBy(() -> initiate("clip.mp4", "key", "video/mp4", 1, 1, 1, null));
        assertThatIllegalArgumentException().isThrownBy(() -> initiate("clip.mp4", "key", "video/mp4", 1, 1, 1, " "));
    }

    @Test
    void rejectsNonPositiveSizesAndPartCount() {
        assertThatIllegalArgumentException().isThrownBy(() -> initiate("clip.mp4", "key", "video/mp4", 0, 1, 1, "upload"));
        assertThatIllegalArgumentException().isThrownBy(() -> initiate("clip.mp4", "key", "video/mp4", -1, 1, 1, "upload"));
        assertThatIllegalArgumentException().isThrownBy(() -> initiate("clip.mp4", "key", "video/mp4", 1, 0, 1, "upload"));
        assertThatIllegalArgumentException().isThrownBy(() -> initiate("clip.mp4", "key", "video/mp4", 1, -1, 1, "upload"));
        assertThatIllegalArgumentException().isThrownBy(() -> initiate("clip.mp4", "key", "video/mp4", 1, 1, 0, "upload"));
        assertThatIllegalArgumentException().isThrownBy(() -> initiate("clip.mp4", "key", "video/mp4", 1, 1, -1, "upload"));
    }

    @Test
    void rejectsPartCountThatDoesNotMatchCeilingDivision() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> initiate("clip.mp4", "key", "video/mp4", 11, 10, 1, "upload"));
    }

    @Test
    void rejectsNullInitiatedTimestamp() {
        assertThatNullPointerException().isThrownBy(() -> Video.initiate(
                "clip.mp4", "key", "video/mp4", 1, 1, 1, "upload", null));
    }

    @Test
    void marksInitiatedVideoReady() {
        Video video = initiatedVideo();
        Instant completedAt = CREATED_AT.plusSeconds(60);

        video.markReady(completedAt);

        assertThat(video.getStatus()).isEqualTo(VideoStatus.READY);
        assertThat(video.getUpdatedAt()).isEqualTo(completedAt);
        assertThat(video.getCompletedAt()).isEqualTo(completedAt);
    }

    @Test
    void markReadyRejectsNullTimestamp() {
        Video video = initiatedVideo();

        assertThatNullPointerException().isThrownBy(() -> video.markReady(null));
        assertThat(video.getStatus()).isEqualTo(VideoStatus.INITIATED);
    }

    @Test
    void markReadyRejectsTimestampBeforeCreation() {
        Video video = initiatedVideo();

        assertThatIllegalArgumentException()
                .isThrownBy(() -> video.markReady(CREATED_AT.minusNanos(1)));
        assertThat(video.getStatus()).isEqualTo(VideoStatus.INITIATED);
    }

    @Test
    void marksInitiatedVideoAbortedWithFailure() {
        Video video = initiatedVideo();
        Instant abortedAt = CREATED_AT.plusSeconds(30);

        video.markAborted("Upload expired", abortedAt);

        assertThat(video.getStatus()).isEqualTo(VideoStatus.ABORTED);
        assertThat(video.getUpdatedAt()).isEqualTo(abortedAt);
        assertThat(video.getCompletedAt()).isNull();
        assertThat(video.getFailureMessage()).isEqualTo("Upload expired");
    }

    @Test
    void markAbortedRejectsNullTimestamp() {
        Video video = initiatedVideo();

        assertThatNullPointerException().isThrownBy(() -> video.markAborted("Failure", null));
        assertThat(video.getStatus()).isEqualTo(VideoStatus.INITIATED);
    }

    @Test
    void markAbortedRejectsTimestampBeforeCreation() {
        Video video = initiatedVideo();

        assertThatIllegalArgumentException()
                .isThrownBy(() -> video.markAborted("Failure", CREATED_AT.minusNanos(1)));
        assertThat(video.getStatus()).isEqualTo(VideoStatus.INITIATED);
    }

    @Test
    void readyVideoCannotBeAborted() {
        Video video = initiatedVideo();
        video.markReady(CREATED_AT.plusSeconds(60));

        assertThatIllegalStateException()
                .isThrownBy(() -> video.markAborted("Too late", CREATED_AT.plusSeconds(120)));
    }

    @Test
    void abortedVideoCannotBecomeReady() {
        Video video = initiatedVideo();
        video.markAborted("Upload expired", CREATED_AT.plusSeconds(30));

        assertThatIllegalStateException()
                .isThrownBy(() -> video.markReady(CREATED_AT.plusSeconds(60)));
    }

    @Test
    void repeatedTerminalTransitionIsIdempotent() {
        Video ready = initiatedVideo();
        Instant readyAt = CREATED_AT.plusSeconds(60);
        ready.markReady(readyAt);

        ready.markReady(CREATED_AT.plusSeconds(120));

        assertThat(ready.getUpdatedAt()).isEqualTo(readyAt);
        assertThat(ready.getCompletedAt()).isEqualTo(readyAt);

        Video aborted = initiatedVideo();
        Instant abortedAt = CREATED_AT.plusSeconds(30);
        aborted.markAborted("First failure", abortedAt);

        aborted.markAborted("Second failure", CREATED_AT.plusSeconds(90));

        assertThat(aborted.getUpdatedAt()).isEqualTo(abortedAt);
        assertThat(aborted.getFailureMessage()).isEqualTo("First failure");
    }

    private static Video initiatedVideo() {
        return Video.initiate(
                "clip.mp4",
                "videos/clip.mp4",
                "video/mp4",
                21_000_000L,
                10_485_760L,
                3,
                "upload-123",
                CREATED_AT
        );
    }

    private static Video initiate(
            String originalFilename,
            String objectKey,
            String contentType,
            long size,
            long partSize,
            int partCount,
            String uploadId
    ) {
        return Video.initiate(
                originalFilename,
                objectKey,
                contentType,
                size,
                partSize,
                partCount,
                uploadId,
                CREATED_AT
        );
    }
}
