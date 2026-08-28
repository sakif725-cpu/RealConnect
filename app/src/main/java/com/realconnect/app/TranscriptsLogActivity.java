package com.realconnect.app;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.ImageButton;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import java.io.File;

public class TranscriptsLogActivity extends AppCompatActivity {

    private TextView textTranscriptBody;
    private TextView textTranscriptStats;
    private View layoutEmptyTranscript;
    private View scrollTranscriptContent;
    private ImageButton btnShare;
    private ImageButton btnDelete;

    private CallTranscriptManager transcriptManager;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_transcripts_log);

        View header = findViewById(R.id.transcripts_header);
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

        transcriptManager = new CallTranscriptManager(this);

        ImageButton btnBack = findViewById(R.id.btn_transcripts_back);
        btnBack.setOnClickListener(v -> finish());

        textTranscriptBody = findViewById(R.id.text_transcript_body);
        textTranscriptStats = findViewById(R.id.text_transcript_stats);
        layoutEmptyTranscript = findViewById(R.id.layout_empty_transcript);
        scrollTranscriptContent = findViewById(R.id.scroll_transcript_content);
        btnShare = findViewById(R.id.btn_share_transcript);
        btnDelete = findViewById(R.id.btn_delete_transcript);

        btnShare.setOnClickListener(v -> shareTranscript());
        btnDelete.setOnClickListener(v -> showDeleteConfirmationDialog());

        loadTranscriptData();
    }

    @Override
    protected void onResume() {
        super.onResume();
        loadTranscriptData();
    }

    private void loadTranscriptData() {
        String transcript = transcriptManager.readCompleteTranscript();
        try {
            File file = transcriptManager.getTranscriptFile();
            if (transcript.trim().isEmpty() || !file.exists() || file.length() == 0) {
                layoutEmptyTranscript.setVisibility(View.VISIBLE);
                scrollTranscriptContent.setVisibility(View.GONE);
                btnShare.setVisibility(View.GONE);
                btnDelete.setVisibility(View.GONE);
                textTranscriptStats.setText("0 KB");
            } else {
                layoutEmptyTranscript.setVisibility(View.GONE);
                scrollTranscriptContent.setVisibility(View.VISIBLE);
                btnShare.setVisibility(View.VISIBLE);
                btnDelete.setVisibility(View.VISIBLE);

                textTranscriptBody.setText(transcript.trim());

                long bytes = file.length();
                String sizeStr = bytes > 1024 ? String.format(java.util.Locale.getDefault(), "%.1f KB", bytes / 1024.0) : bytes + " B";
                int lines = transcript.split("\n").length;
                textTranscriptStats.setText(lines + " " + (lines == 1 ? "entry" : "entries") + " • " + sizeStr);
            }
        } catch (Exception e) {
            layoutEmptyTranscript.setVisibility(View.VISIBLE);
            scrollTranscriptContent.setVisibility(View.GONE);
            btnShare.setVisibility(View.GONE);
            btnDelete.setVisibility(View.GONE);
            textTranscriptStats.setText("0 KB");
        }
    }

    private void shareTranscript() {
        String transcript = transcriptManager.readCompleteTranscript();
        if (transcript.trim().isEmpty()) {
            Toast.makeText(this, "No transcript data to share", Toast.LENGTH_SHORT).show();
            return;
        }

        Intent sendIntent = new Intent();
        sendIntent.setAction(Intent.ACTION_SEND);
        sendIntent.putExtra(Intent.EXTRA_TITLE, "RealConnect Call Transcript");
        sendIntent.putExtra(Intent.EXTRA_TEXT, transcript);
        sendIntent.setType("text/plain");

        Intent shareIntent = Intent.createChooser(sendIntent, "Share Call Transcripts");
        startActivity(shareIntent);
    }

    private void showDeleteConfirmationDialog() {
        new AlertDialog.Builder(this)
                .setTitle("Clear All Transcripts?")
                .setMessage("Are you sure you want to permanently delete 'transcript.txt'? This action cannot be undone.")
                .setPositiveButton("Clear Log", (dialog, which) -> {
                    boolean deleted = transcriptManager.deleteTranscriptFile();
                    if (deleted) {
                        Toast.makeText(TranscriptsLogActivity.this, "Transcript log cleared", Toast.LENGTH_SHORT).show();
                        loadTranscriptData();
                    } else {
                        Toast.makeText(TranscriptsLogActivity.this, "Failed to clear transcript log", Toast.LENGTH_SHORT).show();
                    }
                })
                .setNegativeButton("Cancel", null)
                .show();
    }
}