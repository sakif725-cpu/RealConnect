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

    @Query("SELECT m.* FROM messages m INNER JOIN (SELECT chatId, MAX(timestamp) AS max_time FROM messages GROUP BY chatId) latest ON m.chatId = latest.chatId AND m.timestamp = latest.max_time ORDER BY m.timestamp DESC")
    List<Message> getRecentChats();

    @Query("SELECT COUNT(*) FROM messages WHERE isRead = 0")
    int getUnreadMessageCount();

    @Query("SELECT COUNT(*) FROM messages WHERE chatId = :chatId AND isRead = 0")
    int getUnreadCountForChat(String chatId);

    @Query("UPDATE messages SET isRead = 1 WHERE chatId = :chatId AND isRead = 0")
    void markChatAsRead(String chatId);

    @Query("DELETE FROM messages WHERE chatId = :chatId")
    void deleteChat(String chatId);

    @Query("DELETE FROM messages WHERE id = :messageId")
    void deleteMessage(String messageId);
}