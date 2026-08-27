package com.realconnect.app;

import androidx.annotation.NonNull;
import androidx.room.Entity;
import androidx.room.Ignore;
import androidx.room.PrimaryKey;

@Entity(tableName = "messages")
public class Message {

    @PrimaryKey
    @NonNull
    private String id;
    private String chatId;
    private String senderPhone;
    private String receiverPhone;
    private String text;
    private long timestamp;
    private boolean isRead;

    public Message() {
        this.id = "";
    }

    @Ignore
    public Message(@NonNull String id, String chatId, String senderPhone, String receiverPhone, String text, long timestamp, boolean isRead) {
        this.id = id;
        this.chatId = chatId;
        this.senderPhone = senderPhone;
        this.receiverPhone = receiverPhone;
        this.text = text;
        this.timestamp = timestamp;
        this.isRead = isRead;
    }

    @NonNull
    public String getId() {
        return id;
    }

    public void setId(@NonNull String id) {
        this.id = id;
    }

    public String getChatId() {
        return chatId;
    }

    public void setChatId(String chatId) {
        this.chatId = chatId;
    }

    public String getSenderPhone() {
        return senderPhone;
    }

    public void setSenderPhone(String senderPhone) {
        this.senderPhone = senderPhone;
    }

    public String getReceiverPhone() {
        return receiverPhone;
    }

    public void setReceiverPhone(String receiverPhone) {
        this.receiverPhone = receiverPhone;
    }

    public String getText() {
        return text;
    }

    public void setText(String text) {
        this.text = text;
    }

    public long getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(long timestamp) {
        this.timestamp = timestamp;
    }

    public boolean isRead() {
        return isRead;
    }

    public void setRead(boolean isRead) {
        this.isRead = isRead;
    }
}