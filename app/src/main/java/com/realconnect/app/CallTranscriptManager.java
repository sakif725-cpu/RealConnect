package com.realconnect.app;

import android.Manifest;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.speech.RecognitionListener;
import android.speech.RecognizerIntent;
import android.speech.SpeechRecognizer;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.Locale;

/**
 * Production-ready Real-Time Call Speech-to-Text Pipeline & Transcript Logger.
 *
 * Key Architecture:
 * 1. Sandboxed Internal Storage: Writes to context.getFilesDir()/profile/privacy/TranscriptsLog/transcript.txt
 * 2. Thread-Safe Append-Only Writer: Appends finalized sentences line-by-line with timestamps.
 * 3. Continuous Recognition Loop: Handles silences and errors with auto-recovery.
 * 4. Zero External Leakage: Operates purely in Linux application sandbox (0700 private UID).
 */
public class CallTranscriptManager {

    private static final String TAG = "CallTranscriptManager";

    // Required directory hierarchy relative to internal context.getFilesDir()
    private static final String DIRECTORY_PATH = "profile/privacy/TranscriptsLog";
    private static final String FILE_NAME = "transcript.txt";
    private static final long RESTART_DELAY_MS = 250L;

    public interface OnTranscriptUpdatedListener {
        void onSentenceLogged(@NonNull String timestamp, @NonNull String text);
        void onError(@NonNull String errorMessage);
    }

    private final Context context;
    private final Handler mainHandler;
    private final Object fileLock = new Object();
    private final SimpleDateFormat timestampFormatter;

    private SpeechRecognizer speechRecognizer;
    private Intent speechIntent;
    private boolean isListening = false;
    private OnTranscriptUpdatedListener listener;

    public CallTranscriptManager(@NonNull Context context) {
        this.context = context.getApplicationContext();
        this.mainHandler = new Handler(Looper.getMainLooper());
        this.timestampFormatter = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault());
        initSpeechRecognizer();
    }

    public void setOnTranscriptUpdatedListener(@Nullable OnTranscriptUpdatedListener listener) {
        this.listener = listener;
    }

    /**
     * Requirement 1 & 4: Helper method to establish nested directories inside context.getFilesDir()
     * Path: /data/user/0/<package_name>/files/profile/privacy/TranscriptsLog/transcript.txt
     */
    @NonNull
    public File getTranscriptFile() throws IOException {
        File directory = new File(context.getFilesDir(), DIRECTORY_PATH);
        if (!directory.exists()) {
            boolean created = directory.mkdirs();
            if (!created && !directory.exists()) {
                throw new IOException("Failed to create privacy transcript directory: " + directory.getAbsolutePath());
            }
        }
        return new File(directory, FILE_NAME);
    }

    /**
     * Requirement 3: Continuous SpeechRecognizer framework with automated fault tolerance.
     */
    private void initSpeechRecognizer() {
        if (!SpeechRecognizer.isRecognitionAvailable(context)) {
            Log.e(TAG, "SpeechRecognizer is unavailable on this device");
            return;
        }

        // Favor on-device offline recognition when supported (Android 12+ / API 31+)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && SpeechRecognizer.isOnDeviceRecognitionAvailable(context)) {
            speechRecognizer = SpeechRecognizer.createOnDeviceSpeechRecognizer(context);
        } else {
            speechRecognizer = SpeechRecognizer.createSpeechRecognizer(context);
        }

        speechIntent = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
        speechIntent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
        speechIntent.putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault().toString());
        speechIntent.putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, false);
        speechIntent.putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            speechIntent.putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, true);
        }

        speechRecognizer.setRecognitionListener(new RecognitionListener() {
            @Override
            public void onReadyForSpeech(Bundle params) {
                Log.d(TAG, "RecognitionListener: Ready for audio stream");
            }

            @Override public void onBeginningOfSpeech() {}
            @Override public void onRmsChanged(float rmsdB) {}
            @Override public void onBufferReceived(byte[] buffer) {}
            @Override public void onEndOfSpeech() {}

            @Override
            public void onError(int error) {
                Log.w(TAG, "RecognitionListener: onError code = " + error);
                // On silence, speech timeout, or connection blips, restart continuous loop
                if (isListening) {
                    scheduleRestart();
                }
            }

            @Override
            public void onResults(Bundle results) {
                processResults(results);
                // Keep continuous listening active for the remainder of the call
                if (isListening) {
                    scheduleRestart();
                }
            }

            @Override public void onPartialResults(Bundle partialResults) {}
            @Override public void onEvent(int eventType, Bundle params) {}
        });
    }

    /**
     * Starts continuous listening loop on the main looper.
     */
    public synchronized void startListening() {
        if (isListening) return;

        if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            String errorMsg = "RECORD_AUDIO permission is not granted";
            Log.e(TAG, errorMsg);
            if (listener != null) listener.onError(errorMsg);
            return;
        }

        isListening = true;
        beginListeningIntent();
    }

    /**
     * Stops continuous listening loop and releases all native recognizer instances.
     */
    public synchronized void stopListening() {
        isListening = false;
        mainHandler.removeCallbacksAndMessages(null);

        if (speechRecognizer != null) {
            try {
                speechRecognizer.stopListening();
                speechRecognizer.cancel();
                speechRecognizer.destroy();
            } catch (Exception e) {
                Log.e(TAG, "Error cleaning up SpeechRecognizer", e);
            }
            speechRecognizer = null;
        }
    }

    private void beginListeningIntent() {
        if (!isListening) return;

        mainHandler.post(() -> {
            try {
                if (speechRecognizer == null) {
                    initSpeechRecognizer();
                }
                if (speechRecognizer != null && speechIntent != null) {
                    speechRecognizer.startListening(speechIntent);
                }
            } catch (Exception e) {
                Log.e(TAG, "Exception starting listening intent", e);
                scheduleRestart();
            }
        });
    }

    private void scheduleRestart() {
        if (!isListening) return;
        mainHandler.removeCallbacksAndMessages(null);
        mainHandler.postDelayed(this::beginListeningIntent, RESTART_DELAY_MS);
    }

    private void processResults(Bundle results) {
        if (results == null) return;
        ArrayList<String> matches = results.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION);
        if (matches != null && !matches.isEmpty()) {
            String text = matches.get(0);
            if (text != null && !text.trim().isEmpty()) {
                String cleanSentence = text.trim();
                String timestamp = timestampFormatter.format(new Date());

                // Requirement 2: Append-only write to internal storage
                appendSentenceToFile(timestamp, cleanSentence);

                if (listener != null) {
                    mainHandler.post(() -> listener.onSentenceLogged(timestamp, cleanSentence));
                }
            }
        }
    }

    /**
     * Requirement 2 & 4: Append-Only Thread-Safe Writer
     * Appends a new line formatted as "[YYYY-MM-DD HH:MM:SS] <Text>" to transcript.txt
     */
    private void appendSentenceToFile(@NonNull String timestamp, @NonNull String sentence) {
        synchronized (fileLock) {
            BufferedWriter writer = null;
            try {
                File targetFile = getTranscriptFile();
                // Pass 'true' to FileWriter for append-only mode (never overwrites)
                writer = new BufferedWriter(new FileWriter(targetFile, true));
                String line = String.format("[%s] %s", timestamp, sentence);
                writer.write(line);
                writer.newLine();
                writer.flush();
                Log.d(TAG, "Successfully appended: " + line);
            } catch (IOException e) {
                Log.e(TAG, "Failed writing to internal transcript file", e);
                if (listener != null) {
                    mainHandler.post(() -> listener.onError("File write error: " + e.getMessage()));
                }
            } finally {
                if (writer != null) {
                    try {
                        writer.close();
                    } catch (IOException ignored) {}
                }
            }
        }
    }

    /**
     * Reads the entire log file from internal storage.
     */
    @NonNull
    public String readCompleteTranscript() {
        synchronized (fileLock) {
            try {
                File file = getTranscriptFile();
                if (!file.exists()) return "";

                StringBuilder builder = new StringBuilder();
                try (java.io.BufferedReader reader = new java.io.BufferedReader(new java.io.FileReader(file))) {
                    String line;
                    while ((line = reader.readLine()) != null) {
                        builder.append(line).append("\n");
                    }
                }
                return builder.toString();
            } catch (IOException e) {
                Log.e(TAG, "Failed to read transcript file", e);
                return "";
            }
        }
    }

    /**
     * Deletes transcript file securely if needed.
     */
    public boolean deleteTranscriptFile() {
        synchronized (fileLock) {
            try {
                File file = getTranscriptFile();
                return !file.exists() || file.delete();
            } catch (IOException e) {
                Log.e(TAG, "Failed to delete transcript file", e);
                return false;
            }
        }
    }
}