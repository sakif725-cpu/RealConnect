package com.realconnect.app;

import androidx.room.Entity;
import androidx.room.Ignore;
import androidx.room.PrimaryKey;

@Entity(tableName = "call_logs")
public class CallLogEntry {

    public static final int TYPE_INCOMING = 1;
    public static final int TYPE_OUTGOING = 2;
    public static final int TYPE_MISSED = 3;

    @PrimaryKey(autoGenerate = true)
    private int id;
    private String phoneNumber;
    private String contactName;
    private int callType;
    private long timestamp;
    private int durationSeconds;
    private boolean isSpam;
    private boolean isRead;

    public CallLogEntry() {}

    @Ignore
    public CallLogEntry(String phoneNumber, String contactName, int callType, long timestamp, int durationSeconds, boolean isSpam) {
        this.phoneNumber = phoneNumber;
        this.contactName = contactName;
        this.callType = callType;
        this.timestamp = timestamp;
        this.durationSeconds = durationSeconds;
        this.isSpam = isSpam;
        this.isRead = (callType != TYPE_MISSED);
    }

    @Ignore
    public CallLogEntry(String phoneNumber, String contactName, int callType, long timestamp, int durationSeconds, boolean isSpam, boolean isRead) {
        this.phoneNumber = phoneNumber;
        this.contactName = contactName;
        this.callType = callType;
        this.timestamp = timestamp;
        this.durationSeconds = durationSeconds;
        this.isSpam = isSpam;
        this.isRead = isRead;
    }

    public int getId() { return id; }
    public void setId(int id) { this.id = id; }

    public String getPhoneNumber() { return phoneNumber; }
    public void setPhoneNumber(String phoneNumber) { this.phoneNumber = phoneNumber; }

    public String getContactName() { return contactName; }
    public void setContactName(String contactName) { this.contactName = contactName; }

    public int getCallType() { return callType; }
    public void setCallType(int callType) { this.callType = callType; }

    public long getTimestamp() { return timestamp; }
    public void setTimestamp(long timestamp) { this.timestamp = timestamp; }

    public int getDurationSeconds() { return durationSeconds; }
    public void setDurationSeconds(int durationSeconds) { this.durationSeconds = durationSeconds; }

    public boolean isSpam() { return isSpam; }
    public void setSpam(boolean spam) { isSpam = spam; }

    public boolean isRead() { return isRead; }
    public void setRead(boolean read) { isRead = read; }
}