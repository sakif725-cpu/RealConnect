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
import java.util.concurrent.CopyOnWriteArrayList;

public class ChatRepository {

    private static final String TAG = "ChatRepository";
    private static ChatRepository instance;
    private final MessageDao messageDao;
    private final DatabaseReference dbChats;
    private final DatabaseReference dbInbox;

    private final Map<String, ChildEventListener> threadListeners = new HashMap<>();
    private final List<OnMessageReceivedListener> globalListeners = new CopyOnWriteArrayList<>();
    private ChildEventListener userInboxListener;
    private String currentListeningPhone = null;

    public interface OnMessageReceivedListener {
        void onNewMessage(Message message);
    }

    private ChatRepository(Context context) {
        AppDatabase db = AppDatabase.getInstance(context);
        this.messageDao = db.messageDao();
        this.dbChats = FirebaseDatabase.getInstance().getReference("chats");
        this.dbInbox = FirebaseDatabase.getInstance().getReference("user_inbox");
    }

    public static synchronized ChatRepository getInstance(Context context) {
        if (instance == null) {
            instance = new ChatRepository(context.getApplicationContext());
        }
        return instance;
    }

    public static String cleanPhone(String phone) {
        if (phone == null) return "";
        return phone.replaceAll("[^0-9]", "");
    }

    public static String getChatId(String phoneA, String phoneB) {
        String cleanA = cleanPhone(phoneA);
        String cleanB = cleanPhone(phoneB);
        return cleanA.compareTo(cleanB) < 0 ? cleanA + "_" + cleanB : cleanB + "_" + cleanA;
    }

    public void addGlobalListener(OnMessageReceivedListener listener) {
        if (listener != null && !globalListeners.contains(listener)) {
            globalListeners.add(listener);
        }
    }

    public void removeGlobalListener(OnMessageReceivedListener listener) {
        if (listener != null) {
            globalListeners.remove(listener);
        }
    }

    private void notifyGlobalListeners(Message message) {
        for (OnMessageReceivedListener listener : globalListeners) {
            try {
                listener.onNewMessage(message);
            } catch (Exception ignored) {}
        }
    }

    public Message sendMessage(String senderPhone, String receiverPhone, String text) {
        String cleanSender = cleanPhone(senderPhone);
        String cleanReceiver = cleanPhone(receiverPhone);
        String chatId = getChatId(cleanSender, cleanReceiver);
        String messageId = UUID.randomUUID().toString();
        long timestamp = System.currentTimeMillis();

        Message message = new Message(messageId, chatId, cleanSender, cleanReceiver, text, timestamp, true);

        // 1. Save locally to Room
        messageDao.insert(message);
        notifyGlobalListeners(message);

        // 2. Sync to Firebase chat thread
        dbChats.child(chatId).child("messages").child(messageId).setValue(message)
                .addOnFailureListener(e -> Log.e(TAG, "Failed to sync message to thread", e));

        // 3. Deliver to receiver's user_inbox (receives from ANY number, even unsaved!)
        dbInbox.child(cleanReceiver).child(messageId).setValue(message)
                .addOnFailureListener(e -> Log.e(TAG, "Failed to deliver to user_inbox", e));

        return message;
    }

    public List<Message> getLocalMessages(String chatId) {
        return messageDao.getMessagesForChat(chatId);
    }

    public List<Message> getRecentChats() {
        return messageDao.getRecentChats();
    }

    public void markAsRead(String chatId, String selfPhone) {
        String cleanSelf = cleanPhone(selfPhone);
        messageDao.markChatAsRead(chatId, cleanSelf);
    }

    public void startListeningToUserInbox(String selfPhone, @Nullable OnMessageReceivedListener notificationCallback) {
        String cleanSelf = cleanPhone(selfPhone);
        if (cleanSelf.isEmpty()) return;

        if (cleanSelf.equals(currentListeningPhone) && userInboxListener != null) {
            return; // Already listening
        }

        stopListeningToUserInbox();
        currentListeningPhone = cleanSelf;

        userInboxListener = new ChildEventListener() {
            @Override
            public void onChildAdded(@NonNull DataSnapshot snapshot, @Nullable String previousChildName) {
                try {
                    Message message = snapshot.getValue(Message.class);
                    if (message != null) {
                        message.setRead(false);
                        messageDao.insert(message);

                        // Remove from inbox queue so it's not redelivered
                        snapshot.getRef().removeValue();

                        notifyGlobalListeners(message);
                        if (notificationCallback != null) {
                            notificationCallback.onNewMessage(message);
                        }
                    }
                } catch (Exception e) {
                    Log.e(TAG, "Error processing inbox message", e);
                }
            }

            @Override public void onChildChanged(@NonNull DataSnapshot snapshot, @Nullable String previousChildName) {}
            @Override public void onChildRemoved(@NonNull DataSnapshot snapshot) {}
            @Override public void onChildMoved(@NonNull DataSnapshot snapshot, @Nullable String previousChildName) {}
            @Override public void onCancelled(@NonNull DatabaseError error) {}
        };

        dbInbox.child(cleanSelf).addChildEventListener(userInboxListener);
    }

    public void stopListeningToUserInbox() {
        if (userInboxListener != null && currentListeningPhone != null) {
            dbInbox.child(currentListeningPhone).removeEventListener(userInboxListener);
            userInboxListener = null;
            currentListeningPhone = null;
        }
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

        threadListeners.put(chatId, childListener);
        dbChats.child(chatId).child("messages").addChildEventListener(childListener);
    }

    public void stopListeningForMessages(String chatId) {
        ChildEventListener listener = threadListeners.remove(chatId);
        if (listener != null) {
            dbChats.child(chatId).child("messages").removeEventListener(listener);
        }
    }
}