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
    private TextView textTranscriptsCount;

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
        textTranscriptsCount = findViewById(R.id.text_transcripts_count);

        // 1. Recordings (Functional)
        findViewById(R.id.card_option_recordings).setOnClickListener(v -> {
            Intent intent = new Intent(PrivacyActivity.this, RecordingsActivity.class);
            startActivity(intent);
        });

        // 2. Transcripts Log (Functional)
        findViewById(R.id.card_option_transcripts).setOnClickListener(v -> {
            Intent intent = new Intent(PrivacyActivity.this, TranscriptsLogActivity.class);
            startActivity(intent);
        });

        // 3. Change Phone Number
        findViewById(R.id.card_option_change_phone).setOnClickListener(v -> {
            new AlertDialog.Builder(PrivacyActivity.this)
                    .setTitle("Change Phone Number")
                    .setMessage("Migrate your account information, chat messages, and call logs to a new mobile phone number securely.\n\nThis feature will be available in the upcoming update.")
                    .setPositiveButton("Got It", null)
                    .show();
        });

        // 4. Blocked Numbers
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
        updateTranscriptsCount();
    }

    private void updateTranscriptsCount() {
        if (textTranscriptsCount == null) return;
        try {
            CallTranscriptManager manager = new CallTranscriptManager(this);
            String transcript = manager.readCompleteTranscript();
            java.io.File file = manager.getTranscriptFile();
            if (transcript.trim().isEmpty() || !file.exists() || file.length() == 0) {
                textTranscriptsCount.setText("No transcripts logged yet");
            } else {
                int lines = transcript.split("\n").length;
                long bytes = file.length();
                String sizeStr = bytes > 1024 ? String.format(java.util.Locale.getDefault(), "%.1f KB", bytes / 1024.0) : bytes + " B";
                textTranscriptsCount.setText(lines + " " + (lines == 1 ? "entry" : "entries") + " (" + sizeStr + ") saved");
            }
        } catch (Exception e) {
            textTranscriptsCount.setText("Manage real-time call speech transcripts");
        }
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