package org.example.uploader.services.impls;

import org.example.uploader.configs.S3Properties;
import org.example.uploader.dtos.CompletedPart;
import org.example.uploader.services.S3MultipartService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.AbortMultipartUploadRequest;
import software.amazon.awssdk.services.s3.model.CompleteMultipartUploadRequest;
import software.amazon.awssdk.services.s3.model.CompletedMultipartUpload;
import software.amazon.awssdk.services.s3.model.CreateMultipartUploadRequest;
import software.amazon.awssdk.services.s3.model.UploadPartRequest;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.GetObjectPresignRequest;
import software.amazon.awssdk.services.s3.presigner.model.UploadPartPresignRequest;

import java.net.URI;
import java.util.List;

@Component
public class S3MultipartServiceImpl implements S3MultipartService {

    private static final Logger LOGGER = LoggerFactory.getLogger(S3MultipartServiceImpl.class);

    private final S3Client s3;
    private final S3Presigner presigner;
    private final S3Properties properties;

    public S3MultipartServiceImpl(S3Client s3, S3Presigner presigner, S3Properties properties) {
        this.s3 = s3;
        this.presigner = presigner;
        this.properties = properties;
    }

    @Override
    public String initiate(String objectKey, String contentType) {
        LOGGER.info("Sending CreateMultipartUpload to S3: bucket={}, objectKey={}",
                properties.bucket(), objectKey);
        return s3.createMultipartUpload(CreateMultipartUploadRequest.builder()
                        .bucket(properties.bucket())
                        .key(objectKey)
                        .contentType(contentType)
                        .build())
                .uploadId();
    }

    @Override
    public URI presignPart(String objectKey, String uploadId, int partNumber) {
        var request = UploadPartRequest.builder()
                .bucket(properties.bucket())
                .key(objectKey)
                .uploadId(uploadId)
                .partNumber(partNumber)
                .build();
        var presigned = presigner.presignUploadPart(UploadPartPresignRequest.builder()
                        .signatureDuration(properties.presignDuration())
                        .uploadPartRequest(request)
                        .build());
        return URI.create(presigned.url().toString());
    }

    @Override
    public void complete(String objectKey, String uploadId, List<CompletedPart> parts) {
        LOGGER.info("Sending CompleteMultipartUpload to S3: bucket={}, objectKey={}, parts={}",
                properties.bucket(), objectKey, parts.size());
        var awsParts = parts.stream()
                .map(part -> software.amazon.awssdk.services.s3.model.CompletedPart.builder()
                        .partNumber(part.partNumber())
                        .eTag(part.eTag())
                        .build())
                .toList();
        s3.completeMultipartUpload(CompleteMultipartUploadRequest.builder()
                .bucket(properties.bucket())
                .key(objectKey)
                .uploadId(uploadId)
                .multipartUpload(CompletedMultipartUpload.builder().parts(awsParts).build())
                .build());
    }

    @Override
    public void abort(String objectKey, String uploadId) {
        LOGGER.info("Sending AbortMultipartUpload to S3: bucket={}, objectKey={}",
                properties.bucket(), objectKey);
        s3.abortMultipartUpload(AbortMultipartUploadRequest.builder()
                .bucket(properties.bucket())
                .key(objectKey)
                .uploadId(uploadId)
                .build());
    }

    @Override
    public URI presignPlayback(String objectKey) {
        LOGGER.info("Generating presigned playback URL: bucket={}, objectKey={}",
                properties.bucket(), objectKey);
        var presigned = presigner.presignGetObject(GetObjectPresignRequest.builder()
                        .signatureDuration(properties.presignDuration())
                        .getObjectRequest(builder -> builder.bucket(properties.bucket()).key(objectKey))
                        .build());
        return URI.create(presigned.url().toString());
    }
}
