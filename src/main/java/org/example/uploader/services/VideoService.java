package org.example.uploader.services;

import org.example.uploader.dtos.PlaybackUrl;
import org.example.uploader.dtos.VideoView;

import java.util.List;

public interface VideoService {
    List<VideoView> listReady();

    VideoView get(String videoId);

    PlaybackUrl createPlaybackUrl(String videoId);
}
