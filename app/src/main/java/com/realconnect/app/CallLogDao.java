package com.realconnect.app;

import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.Query;
import java.util.List;

@Dao
public interface CallLogDao {

    @Insert
    void insert(CallLogEntry log);

    @Query("SELECT * FROM call_logs ORDER BY timestamp DESC")
    List<CallLogEntry> getAllCallLogs();

    @Query("DELETE FROM call_logs WHERE id = :id")
    void deleteById(int id);

    @Query("DELETE FROM call_logs")
    void clearAll();
}