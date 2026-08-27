package com.realconnect.app;

public class CallRecording {
    private final String id;
    private final String filePath;
    private final String contactPhone;
    private final String contactName;
    private final long timestamp;
    private long durationMillis;
    private final long fileSizeBytes;

    public CallRecording(String id, String filePath, String contactPhone, String contactName, long timestamp, long durationMillis, long fileSizeBytes) {
        this.id = id;
        this.filePath = filePath;
        this.contactPhone = contactPhone;
        this.contactName = contactName;
        this.timestamp = timestamp;
        this.durationMillis = durationMillis;
        this.fileSizeBytes = fileSizeBytes;
    }

    public String getId() { return id; }
    public String getFilePath() { return filePath; }
    public String getContactPhone() { return contactPhone; }
    public String getContactName() { return contactName; }
    public long getTimestamp() { return timestamp; }
    public long getDurationMillis() { return durationMillis; }
    public void setDurationMillis(long durationMillis) { this.durationMillis = durationMillis; }
    public long getFileSizeBytes() { return fileSizeBytes; }
}