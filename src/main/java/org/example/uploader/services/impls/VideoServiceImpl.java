package org.example.uploader.services.impls;

import org.example.uploader.dtos.PlaybackUrl;
import org.example.uploader.dtos.VideoView;
import org.example.uploader.models.Video;
import org.example.uploader.models.VideoStatus;
import org.example.uploader.repos.VideoRepository;
import org.example.uploader.services.S3MultipartService;
import org.example.uploader.services.UploadService;
import org.example.uploader.services.VideoService;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class VideoServiceImpl implements VideoService {

    private final VideoRepository repository;
    private final UploadService uploadService;
    private final S3MultipartService s3;

    public VideoServiceImpl(VideoRepository repository, UploadService uploadService, S3MultipartService s3) {
        this.repository = repository;
        this.uploadService = uploadService;
        this.s3 = s3;
    }

    @Override
    public List<VideoView> listReady() {
        return repository.findReadyNewestFirst().stream().map(VideoView::from).toList();
    }

    @Override
    public VideoView get(String videoId) {
        return VideoView.from(uploadService.requireVideo(videoId));
    }

    @Override
    public PlaybackUrl createPlaybackUrl(String videoId) {
        Video video = uploadService.requireVideo(videoId);
        if (video.getStatus() != VideoStatus.READY) {
            throw new IllegalStateException("Video is not ready");
        }
        return new PlaybackUrl(s3.presignPlayback(video.getObjectKey()).toString());
    }
}
