package org.example.uploader.dtos;

public record InitiatedUpload(String videoId, long partSize, int partCount) {
}
