package com.realconnect.app;

import android.content.Intent;
import android.os.Bundle;
import android.widget.ImageButton;
import android.widget.TextView;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import java.util.List;

public class PrivacyActivity extends AppCompatActivity {

    private TextView textRecordingsCount;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_privacy);

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
            new AlertDialog.Builder(PrivacyActivity.this)
                    .setTitle("Blocked Numbers")
                    .setMessage("Manage contacts and unknown numbers blocked from calling or sending SMS messages to you.\n\nThis feature will be available in the upcoming update.")
                    .setPositiveButton("Got It", null)
                    .show();
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