package com.realconnect.app;

import android.content.Context;
import androidx.room.Database;
import androidx.room.Room;
import androidx.room.RoomDatabase;

@Database(entities = {Contact.class}, version = 1)
public abstract class AppDatabase extends RoomDatabase {
    private static AppDatabase instance;

    public abstract ContactDao contactDao();

    public static synchronized AppDatabase getInstance(Context context) {
        if (instance == null) {
            instance = Room.databaseBuilder(context.getApplicationContext(),
                            AppDatabase.class, "realconnect_db")
                    .fallbackToDestructiveMigration()
                    .allowMainThreadQueries() // Using allowMainThreadQueries for simplicity in this setup
                    .build();
        }
        return instance;
    }
}
