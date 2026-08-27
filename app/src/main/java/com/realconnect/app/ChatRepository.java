package com.realconnect.app;

import android.content.Context;
import android.util.Log;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import com.google.firebase.database.ChildEventListener;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class ChatRepository {

    private static final String TAG = "ChatRepository";
    private static ChatRepository instance;
    private final MessageDao messageDao;
    private final DatabaseReference dbRef;
    private final Map<String, ChildEventListener> activeListeners = new HashMap<>();

    public interface OnMessageReceivedListener {
        void onNewMessage(Message message);
    }

    private ChatRepository(Context context) {
        AppDatabase db = AppDatabase.getInstance(context);
        this.messageDao = db.messageDao();
        this.dbRef = FirebaseDatabase.getInstance().getReference("chats");
    }

    public static synchronized ChatRepository getInstance(Context context) {
        if (instance == null) {
            instance = new ChatRepository(context);
        }
        return instance;
    }

    public static String getChatId(String phoneA, String phoneB) {
        if (phoneA == null) phoneA = "";
        if (phoneB == null) phoneB = "";
        String cleanA = phoneA.replaceAll("[^0-9+]", "");
        String cleanB = phoneB.replaceAll("[^0-9+]", "");
        return cleanA.compareTo(cleanB) < 0 ? cleanA + "_" + cleanB : cleanB + "_" + cleanA;
    }

    public Message sendMessage(String senderPhone, String receiverPhone, String text) {
        String chatId = getChatId(senderPhone, receiverPhone);
        String messageId = UUID.randomUUID().toString();
        long timestamp = System.currentTimeMillis();

        Message message = new Message(messageId, chatId, senderPhone, receiverPhone, text, timestamp, true);

        // 1. Save locally to Room
        messageDao.insert(message);

        // 2. Sync to Firebase
        dbRef.child(chatId).child("messages").child(messageId).setValue(message)
                .addOnFailureListener(e -> Log.e(TAG, "Failed to send message to Firebase", e));

        return message;
    }

    public List<Message> getLocalMessages(String chatId) {
        return messageDao.getMessagesForChat(chatId);
    }

    public List<Message> getRecentChats() {
        return messageDao.getRecentChats();
    }

    public void markAsRead(String chatId, String selfPhone) {
        messageDao.markChatAsRead(chatId, selfPhone);
    }

    public void startListeningForMessages(String chatId, OnMessageReceivedListener listener) {
        stopListeningForMessages(chatId);

        ChildEventListener childListener = new ChildEventListener() {
            @Override
            public void onChildAdded(@NonNull DataSnapshot snapshot, @Nullable String previousChildName) {
                try {
                    Message message = snapshot.getValue(Message.class);
                    if (message != null) {
                        messageDao.insert(message);
                        if (listener != null) {
                            listener.onNewMessage(message);
                        }
                    }
                } catch (Exception e) {
                    Log.e(TAG, "Error parsing incoming message", e);
                }
            }

            @Override public void onChildChanged(@NonNull DataSnapshot snapshot, @Nullable String previousChildName) {}
            @Override public void onChildRemoved(@NonNull DataSnapshot snapshot) {}
            @Override public void onChildMoved(@NonNull DataSnapshot snapshot, @Nullable String previousChildName) {}
            @Override public void onCancelled(@NonNull DatabaseError error) {}
        };

        activeListeners.put(chatId, childListener);
        dbRef.child(chatId).child("messages").addChildEventListener(childListener);
    }

    public void stopListeningForMessages(String chatId) {
        ChildEventListener listener = activeListeners.remove(chatId);
        if (listener != null) {
            dbRef.child(chatId).child("messages").removeEventListener(listener);
        }
    }
}