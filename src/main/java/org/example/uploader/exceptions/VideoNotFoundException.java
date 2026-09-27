package org.example.uploader.exceptions;

public class VideoNotFoundException extends RuntimeException {
    public VideoNotFoundException(String id) {
        super("Video not found: " + id);
    }
}
