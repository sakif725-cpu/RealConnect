package com.realconnect.app;

import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;
import java.util.List;

@Dao
public interface MessageDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void insert(Message message);

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void insertAll(List<Message> messages);

    @Query("SELECT * FROM messages WHERE chatId = :chatId ORDER BY timestamp ASC")
    List<Message> getMessagesForChat(String chatId);

    @Query("SELECT * FROM messages WHERE chatId = :chatId1 OR chatId = :chatId2 OR chatId = :chatId3 OR (senderPhone = :p1 AND receiverPhone = :p2) OR (senderPhone = :p2 AND receiverPhone = :p1) OR (senderPhone = :clean1 AND receiverPhone = :clean2) OR (senderPhone = :clean2 AND receiverPhone = :clean1) ORDER BY timestamp ASC")
    List<Message> getMessagesForConversationDetailed(
            String chatId1, String chatId2, String chatId3,
            String p1, String p2,
            String clean1, String clean2
    );

    @Query("SELECT * FROM messages ORDER BY timestamp ASC")
    List<Message> getAllMessages();

    @Query("SELECT * FROM messages WHERE chatId = :chatId OR (senderPhone LIKE '%' || :cleanTarget || '%' AND receiverPhone LIKE '%' || :cleanSelf || '%') OR (senderPhone LIKE '%' || :cleanSelf || '%' AND receiverPhone LIKE '%' || :cleanTarget || '%') ORDER BY timestamp ASC")
    List<Message> getMessagesForConversation(String chatId, String cleanSelf, String cleanTarget);

    @Query("SELECT m.* FROM messages m INNER JOIN (SELECT chatId, MAX(timestamp) AS max_time FROM messages GROUP BY chatId) latest ON m.chatId = latest.chatId AND m.timestamp = latest.max_time ORDER BY m.timestamp DESC")
    List<Message> getRecentChats();

    @Query("SELECT COUNT(*) FROM messages WHERE isRead = 0")
    int getUnreadMessageCount();

    @Query("SELECT COUNT(*) FROM messages WHERE chatId = :chatId AND isRead = 0")
    int getUnreadCountForChat(String chatId);

    @Query("UPDATE messages SET isRead = 1 WHERE chatId = :chatId AND isRead = 0")
    void markChatAsRead(String chatId);

    @Query("SELECT * FROM messages WHERE id = :messageId LIMIT 1")
    Message getMessageById(String messageId);

    @Query("DELETE FROM messages WHERE chatId = :chatId")
    void deleteChat(String chatId);

    @Query("DELETE FROM messages WHERE id = :messageId")
    void deleteMessage(String messageId);
}