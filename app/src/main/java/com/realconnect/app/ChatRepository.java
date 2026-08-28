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
    private OnMessageReceivedListener backgroundNotificationCallback = null;

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
        String digits = phone.replaceAll("[^0-9]", "");
        if (digits.length() >= 10) {
            return digits.substring(digits.length() - 10);
        }
        return digits;
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

    private void notifyGlobalListeners(@Nullable Message message) {
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

        // Sender's local copy: marked as read since sender wrote it
        Message localMessage = new Message(messageId, chatId, cleanSender, cleanReceiver, text, timestamp, true);
        messageDao.insert(localMessage);
        notifyGlobalListeners(localMessage);

        // Remote copy for receiver: isRead = false so receiver device gets unread counter badge!
        Message remoteMessage = new Message(messageId, chatId, cleanSender, cleanReceiver, text, timestamp, false);

        // 2. Sync to Firebase chat thread
        dbChats.child(chatId).child("messages").child(messageId).setValue(remoteMessage)
                .addOnFailureListener(e -> Log.e(TAG, "Failed to sync message to thread", e));

        // 3. Deliver to receiver's user_inbox (receives from ANY number, even unsaved!)
        dbInbox.child(cleanReceiver).child(messageId).setValue(remoteMessage)
                .addOnFailureListener(e -> Log.e(TAG, "Failed to deliver to user_inbox", e));

        return localMessage;
    }

    public List<Message> getLocalMessages(String chatId) {
        return messageDao.getMessagesForChat(chatId);
    }

    public List<Message> getRecentChats() {
        return messageDao.getRecentChats();
    }

    public int getUnreadMessageCount() {
        return messageDao.getUnreadMessageCount();
    }

    public int getUnreadMessageCount(String selfPhone) {
        return messageDao.getUnreadMessageCount();
    }

    public int getUnreadCountForChat(String chatId) {
        return messageDao.getUnreadCountForChat(chatId);
    }

    public int getUnreadCountForChat(String chatId, String selfPhone) {
        return messageDao.getUnreadCountForChat(chatId);
    }

    public void markAsRead(String chatId) {
        messageDao.markChatAsRead(chatId);
        notifyGlobalListeners(null);
    }

    public void markAsRead(String chatId, String selfPhone) {
        messageDao.markChatAsRead(chatId);
        notifyGlobalListeners(null);
    }

    public void deleteChat(String chatId) {
        messageDao.deleteChat(chatId);
        notifyGlobalListeners(null);
    }

    public void deleteMessage(String messageId) {
        messageDao.deleteMessage(messageId);
        notifyGlobalListeners(null);
    }

    public void startListeningToUserInbox(String selfPhone, @Nullable OnMessageReceivedListener notificationCallback) {
        String cleanSelf = cleanPhone(selfPhone);
        if (cleanSelf.isEmpty()) return;

        if (notificationCallback != null) {
            this.backgroundNotificationCallback = notificationCallback;
        }

        if (cleanSelf.equals(currentListeningPhone) && userInboxListener != null) {
            return; // Already listening to this phone
        }

        stopListeningToUserInbox();
        currentListeningPhone = cleanSelf;

        userInboxListener = new ChildEventListener() {
            @Override
            public void onChildAdded(@NonNull DataSnapshot snapshot, @Nullable String previousChildName) {
                try {
                    Message message = snapshot.getValue(Message.class);
                    if (message != null) {
                        // Mark as unread on receiver device
                        message.setRead(false);
                        messageDao.insert(message);

                        // Remove from inbox queue so it's not redelivered
                        snapshot.getRef().removeValue();

                        notifyGlobalListeners(message);
                        if (backgroundNotificationCallback != null) {
                            backgroundNotificationCallback.onNewMessage(message);
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