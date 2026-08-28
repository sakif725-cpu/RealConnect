package com.realconnect.app;

import android.content.Context;
import android.content.SharedPreferences;
import androidx.annotation.NonNull;
import java.util.HashSet;
import java.util.Set;

public class BlockedNumbersManager {

    private static final String PREF_NAME = "BlockedNumbersPrefs";
    private static final String KEY_BLOCKED = "blocked_numbers_set";

    public static boolean isBlocked(@NonNull Context context, String phoneNumber) {
        if (phoneNumber == null || phoneNumber.trim().isEmpty()) return false;
        String clean = ChatRepository.cleanPhone(phoneNumber);
        SharedPreferences prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
        Set<String> blocked = prefs.getStringSet(KEY_BLOCKED, new HashSet<>());
        return blocked.contains(clean) || blocked.contains(phoneNumber.trim());
    }

    public static void blockNumber(@NonNull Context context, String phoneNumber) {
        if (phoneNumber == null || phoneNumber.trim().isEmpty()) return;
        String clean = ChatRepository.cleanPhone(phoneNumber);
        SharedPreferences prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
        Set<String> current = new HashSet<>(prefs.getStringSet(KEY_BLOCKED, new HashSet<>()));
        current.add(clean);
        current.add(phoneNumber.trim());
        prefs.edit().putStringSet(KEY_BLOCKED, current).apply();
    }

    public static void unblockNumber(@NonNull Context context, String phoneNumber) {
        if (phoneNumber == null || phoneNumber.trim().isEmpty()) return;
        String clean = ChatRepository.cleanPhone(phoneNumber);
        SharedPreferences prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
        Set<String> current = new HashSet<>(prefs.getStringSet(KEY_BLOCKED, new HashSet<>()));
        current.remove(clean);
        current.remove(phoneNumber.trim());
        prefs.edit().putStringSet(KEY_BLOCKED, current).apply();
    }

    @NonNull
    public static Set<String> getBlockedNumbers(@NonNull Context context) {
        SharedPreferences prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
        return new HashSet<>(prefs.getStringSet(KEY_BLOCKED, new HashSet<>()));
    }
}