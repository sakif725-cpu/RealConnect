package com.realconnect.app;

import android.Manifest;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.os.Build;
import android.os.Bundle;
import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentTransaction;
import com.google.android.material.badge.BadgeDrawable;
import com.google.android.material.bottomnavigation.BottomNavigationView;

public class MainActivity extends AppCompatActivity {

    private static final int NOTIFICATION_PERMISSION_CODE = 101;
    private BottomNavigationView bottomNav;
    private ChatRepository.OnMessageReceivedListener messageListener;
    private CallLogRepository.OnCallLogsChangedListener callLogsListener;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_main);

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.bottom_navigation), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(0, 0, 0, systemBars.bottom);
            return insets;
        });

        bottomNav = findViewById(R.id.bottom_navigation);
        bottomNav.setOnItemSelectedListener(item -> {
            Fragment selectedFragment = null;
            int itemId = item.getItemId();

            if (itemId == R.id.nav_contacts) {
                selectedFragment = new ContactsFragment();
            } else if (itemId == R.id.nav_chats) {
                selectedFragment = new ChatsFragment();
            } else if (itemId == R.id.nav_call) {
                selectedFragment = new CallFragment();
                // Mark missed calls as read when opening Call tab
                CallLogRepository.getInstance(this).markMissedCallsAsRead();
                updateBadges();
            } else if (itemId == R.id.nav_profile) {
                selectedFragment = new ProfileFragment();
            }

            if (selectedFragment != null) {
                loadFragment(selectedFragment);
            }
            return true;
        });

        if (savedInstanceState == null) {
            bottomNav.setSelectedItemId(R.id.nav_contacts);
        }

        setupBadgeListeners();
        checkNotificationPermission();
        startCallServiceIfRegistered();
    }

    private void setupBadgeListeners() {
        messageListener = message -> runOnUiThread(this::updateBadges);
        ChatRepository.getInstance(this).addGlobalListener(messageListener);

        callLogsListener = () -> runOnUiThread(this::updateBadges);
        CallLogRepository.getInstance(this).addListener(callLogsListener);
    }

    @Override
    protected void onResume() {
        super.onResume();
        startCallServiceIfRegistered();
        updateBadges();
    }

    public void updateBadges() {
        if (bottomNav == null) return;
        try {
            SharedPreferences prefs = getSharedPreferences("ProfilePrefs", Context.MODE_PRIVATE);
            String selfPhone = prefs.getString("phone", "");

            // 1. Unread Messages Badge on Chats Tab
            int unreadMessages = ChatRepository.getInstance(this).getUnreadMessageCount();
            if (unreadMessages > 0) {
                BadgeDrawable chatBadge = bottomNav.getOrCreateBadge(R.id.nav_chats);
                chatBadge.setVisible(true);
                chatBadge.setNumber(unreadMessages);
                chatBadge.setBackgroundColor(Color.parseColor("#0EA5E9"));
                chatBadge.setBadgeTextColor(Color.WHITE);
            } else {
                bottomNav.removeBadge(R.id.nav_chats);
            }

            // 2. Missed Calls Badge on Call Tab
            int unreadMissedCalls = CallLogRepository.getInstance(this).getUnreadMissedCallsCount();
            if (unreadMissedCalls > 0) {
                BadgeDrawable callBadge = bottomNav.getOrCreateBadge(R.id.nav_call);
                callBadge.setVisible(true);
                callBadge.setNumber(unreadMissedCalls);
                callBadge.setBackgroundColor(Color.parseColor("#EF4444"));
                callBadge.setBadgeTextColor(Color.WHITE);
            } else {
                bottomNav.removeBadge(R.id.nav_call);
            }
        } catch (Exception ignored) {}
    }

    private void startCallServiceIfRegistered() {
        SharedPreferences prefs = getSharedPreferences("ProfilePrefs", Context.MODE_PRIVATE);
        String selfPhone = prefs.getString("phone", null);
        if (selfPhone != null && !selfPhone.trim().isEmpty()) {
            CallService.start(this);
            ChatRepository.getInstance(this).startListeningToUserInbox(selfPhone, null);
            syncProfileToFirebase();
        }
    }

    private void syncProfileToFirebase() {
        SharedPreferences prefs = getSharedPreferences("ProfilePrefs", Context.MODE_PRIVATE);
        String selfPhone = prefs.getString("phone", null);
        String name = prefs.getString("name", null);
        String imageUriStr = prefs.getString("image_uri", null);

        String cleanPhone = ChatRepository.cleanPhone(selfPhone);
        if (cleanPhone.isEmpty()) return;

        new Thread(() -> {
            try {
                com.google.firebase.database.DatabaseReference userRef =
                        com.google.firebase.database.FirebaseDatabase.getInstance().getReference("users").child(cleanPhone);
                if (name != null && !name.trim().isEmpty()) {
                    userRef.child("name").setValue(name);
                }
                if (imageUriStr != null && !imageUriStr.trim().isEmpty()) {
                    android.net.Uri uri = android.net.Uri.parse(imageUriStr);
                    String base64 = ImageUtils.uriToBase64(getApplicationContext(), uri, 240);
                    if (base64 != null && !base64.isEmpty()) {
                        userRef.child("profileImageBase64").setValue(base64);
                    }
                }
            } catch (Exception ignored) {}
        }).start();
    }

    private void checkNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.POST_NOTIFICATIONS}, NOTIFICATION_PERMISSION_CODE);
            }
        }
    }

    private void loadFragment(Fragment fragment) {
        getSupportFragmentManager().beginTransaction()
                .setTransition(FragmentTransaction.TRANSIT_FRAGMENT_FADE)
                .replace(R.id.fragment_container, fragment)
                .commit();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (messageListener != null) {
            ChatRepository.getInstance(this).removeGlobalListener(messageListener);
        }
        if (callLogsListener != null) {
            CallLogRepository.getInstance(this).removeListener(callLogsListener);
        }
    }
}