package com.realconnect.app;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.ImageButton;
import android.widget.TextView;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import java.util.List;

public class PrivacyActivity extends AppCompatActivity {

    private TextView textRecordingsCount;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_privacy);

        View header = findViewById(R.id.privacy_header);
        ViewCompat.setOnApplyWindowInsetsListener(header, (v, insets) -> {
            Insets statusBarInsets = insets.getInsets(WindowInsetsCompat.Type.statusBars() | WindowInsetsCompat.Type.displayCutout());
            int topOffset = statusBarInsets.top > 0 ? statusBarInsets.top : (int) (16 * getResources().getDisplayMetrics().density);
            v.setPadding(
                    v.getPaddingLeft(),
                    topOffset + (int) (4 * getResources().getDisplayMetrics().density),
                    v.getPaddingRight(),
                    (int) (12 * getResources().getDisplayMetrics().density)
            );
            return insets;
        });

        ImageButton btnBack = findViewById(R.id.btn_privacy_back);
        btnBack.setOnClickListener(v -> finish());

        textRecordingsCount = findViewById(R.id.text_recordings_count);

        // 1. Recordings (Functional)
        findViewById(R.id.card_option_recordings).setOnClickListener(v -> {
            Intent intent = new Intent(PrivacyActivity.this, RecordingsActivity.class);
            startActivity(intent);
        });

        // 2. Change Phone Number
        findViewById(R.id.card_option_change_phone).setOnClickListener(v -> {
            new AlertDialog.Builder(PrivacyActivity.this)
                    .setTitle("Change Phone Number")
                    .setMessage("Migrate your account information, chat messages, and call logs to a new mobile phone number securely.\n\nThis feature will be available in the upcoming update.")
                    .setPositiveButton("Got It", null)
                    .show();
        });

        // 3. Blocked Numbers
        findViewById(R.id.card_option_blocked_numbers).setOnClickListener(v -> {
            java.util.Set<String> blockedSet = BlockedNumbersManager.getBlockedNumbers(PrivacyActivity.this);
            if (blockedSet.isEmpty()) {
                new AlertDialog.Builder(PrivacyActivity.this)
                        .setTitle("Blocked Numbers")
                        .setMessage("You have not blocked any numbers yet.\n\nTo block a contact, long-press on any contact card in the Contacts tab.")
                        .setPositiveButton("Got It", null)
                        .show();
            } else {
                String[] blockedArr = blockedSet.toArray(new String[0]);
                new AlertDialog.Builder(PrivacyActivity.this)
                        .setTitle("Blocked Numbers (" + blockedArr.length + ")")
                        .setItems(blockedArr, (dialog, which) -> {
                            String selected = blockedArr[which];
                            new AlertDialog.Builder(PrivacyActivity.this)
                                    .setTitle("Unblock Number?")
                                    .setMessage("Allow incoming calls and messages from " + selected + "?")
                                    .setPositiveButton("Unblock", (d, w) -> {
                                        BlockedNumbersManager.unblockNumber(PrivacyActivity.this, selected);
                                        android.widget.Toast.makeText(PrivacyActivity.this, "Unblocked " + selected, android.widget.Toast.LENGTH_SHORT).show();
                                    })
                                    .setNegativeButton("Cancel", null)
                                    .show();
                        })
                        .setPositiveButton("Close", null)
                        .show();
            }
        });
    }

    @Override
    protected void onResume() {
        super.onResume();
        updateRecordingsCount();
    }

    private void updateRecordingsCount() {
        if (textRecordingsCount == null) return;
        List<CallRecording> list = CallRecordingHelper.getRecordings(this);
        if (list == null || list.isEmpty()) {
            textRecordingsCount.setText("No recordings saved yet");
        } else if (list.size() == 1) {
            textRecordingsCount.setText("1 call recording saved");
        } else {
            textRecordingsCount.setText(list.size() + " call recordings saved");
        }
    }
}