package org.example.uploader.controllers;

import org.example.uploader.dtos.PlaybackUrl;
import org.example.uploader.dtos.VideoView;
import org.example.uploader.services.VideoService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/videos")
public class VideoApiController {

    private final VideoService videoService;

    public VideoApiController(VideoService videoService) {
        this.videoService = videoService;
    }

    @GetMapping
    List<VideoView> list() {
        return videoService.listReady();
    }

    @GetMapping("/{videoId}")
    VideoView get(@PathVariable String videoId) {
        return videoService.get(videoId);
    }

    @GetMapping("/{videoId}/playback-url")
    PlaybackUrl playbackUrl(@PathVariable String videoId) {
        return videoService.createPlaybackUrl(videoId);
    }
}
