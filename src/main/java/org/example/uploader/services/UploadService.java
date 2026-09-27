package org.example.uploader.services;

import org.example.uploader.dtos.CompletedPart;
import org.example.uploader.dtos.InitiatedUpload;
import org.example.uploader.dtos.PresignedPart;
import org.example.uploader.models.Video;

import java.util.List;

public interface UploadService {
    InitiatedUpload initiate(String filename, String contentType, long size);

    List<PresignedPart> presignParts(String videoId, List<Integer> partNumbers);

    Video complete(String videoId, List<CompletedPart> parts);

    void abort(String videoId);

    Video requireVideo(String videoId);
}
