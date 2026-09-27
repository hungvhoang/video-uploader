package org.example.uploader.services;

import org.example.uploader.dtos.CompletedPart;

import java.net.URI;
import java.util.List;

public interface S3MultipartService {
    String initiate(String objectKey, String contentType);

    URI presignPart(String objectKey, String uploadId, int partNumber);

    void complete(String objectKey, String uploadId, List<CompletedPart> parts);

    void abort(String objectKey, String uploadId);

    URI presignPlayback(String objectKey);
}
