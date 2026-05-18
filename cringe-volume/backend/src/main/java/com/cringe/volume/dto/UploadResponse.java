package com.cringe.volume.dto;

public class UploadResponse {
    private String fileName;

    public UploadResponse(String fileName) {
        this.fileName = fileName;
    }

    public String getFileName() {
        return fileName;
    }
}
