package org.example.uploader.controllers;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Positive;
import org.example.uploader.dtos.CompletedPart;
import org.example.uploader.dtos.InitiatedUpload;
import org.example.uploader.dtos.PresignedPart;
import org.example.uploader.models.Video;
import org.example.uploader.services.UploadService;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/uploads")
public class UploadController {

    private final UploadService uploadService;

    public UploadController(UploadService uploadService) {
        this.uploadService = uploadService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    InitiatedUpload initiate(@Valid @RequestBody InitiateRequest request) {
        return uploadService.initiate(request.fileName(), request.contentType(), request.size());
    }

    @PostMapping("/{videoId}/parts/presign")
    List<PresignedPart> presign(@PathVariable String videoId, @Valid @RequestBody PresignRequest request) {
        return uploadService.presignParts(videoId, request.partNumbers());
    }

    @PostMapping("/{videoId}/complete")
    VideoResponse complete(@PathVariable String videoId, @Valid @RequestBody CompleteRequest request) {
        return VideoResponse.from(uploadService.complete(videoId, request.parts()));
    }

    @DeleteMapping("/{videoId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void abort(@PathVariable String videoId) {
        uploadService.abort(videoId);
    }

    public record InitiateRequest(@NotBlank String fileName, @NotBlank String contentType, @Positive long size) {
    }

    public record PresignRequest(@NotEmpty List<@Positive Integer> partNumbers) {
    }

    public record CompleteRequest(@NotEmpty List<@Valid CompletedPart> parts) {
    }

    public record VideoResponse(String id, String fileName, String contentType, long size, String status) {
        static VideoResponse from(Video video) {
            return new VideoResponse(video.getId(), video.getOriginalFilename(), video.getContentType(),
                    video.getSize(), video.getStatus().name());
        }
    }
}
