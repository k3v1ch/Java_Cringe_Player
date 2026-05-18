package com.cringe.volume.dto;

public class TrackInfo {
    private String filename;
    private long sizeBytes;

    public TrackInfo(String filename, long sizeBytes) {
        this.filename = filename;
        this.sizeBytes = sizeBytes;
    }

    public String getFilename() { return filename; }
    public long getSizeBytes() { return sizeBytes; }
}
