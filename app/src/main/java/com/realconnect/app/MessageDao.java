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

    @Query("SELECT * FROM messages WHERE id IN (SELECT id FROM (SELECT id, MAX(timestamp) FROM messages GROUP BY chatId)) ORDER BY timestamp DESC")
    List<Message> getRecentChats();

    @Query("UPDATE messages SET isRead = 1 WHERE chatId = :chatId AND receiverPhone = :selfPhone")
    void markChatAsRead(String chatId, String selfPhone);

    @Query("DELETE FROM messages WHERE chatId = :chatId")
    void deleteChat(String chatId);
}