package com.realconnect.app;

import android.content.Context;
import android.media.MediaMetadataRetriever;
import android.media.MediaRecorder;
import android.os.Build;
import android.util.Log;
import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class CallRecordingHelper {

    private static final String TAG = "CallRecordingHelper";
    private static CallRecordingHelper instance;

    private MediaRecorder mediaRecorder;
    private boolean isRecording = false;
    private String currentFilePath;
    private String currentContactPhone;
    private String currentContactName;
    private long recordingStartTime = 0;

    private CallRecordingHelper() {}

    public static synchronized CallRecordingHelper getInstance() {
        if (instance == null) {
            instance = new CallRecordingHelper();
        }
        return instance;
    }

    public boolean isRecording() {
        return isRecording;
    }

    public synchronized boolean startRecording(Context context, String contactPhone, String contactName) {
        if (isRecording) return true;

        try {
            File dir = new File(context.getFilesDir(), "recordings");
            if (!dir.exists()) {
                dir.mkdirs();
            }

            long timestamp = System.currentTimeMillis();
            String cleanPhone = ChatRepository.cleanPhone(contactPhone);
            String safeName = (contactName != null && !contactName.trim().isEmpty())
                    ? contactName.replaceAll("[^a-zA-Z0-9_]", "_")
                    : "Call";
            String fileName = "REC_" + cleanPhone + "_" + safeName + "_" + timestamp + ".m4a";
            File recordFile = new File(dir, fileName);

            currentFilePath = recordFile.getAbsolutePath();
            currentContactPhone = contactPhone;
            currentContactName = contactName;

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                mediaRecorder = new MediaRecorder(context);
            } else {
                mediaRecorder = new MediaRecorder();
            }

            mediaRecorder.setAudioSource(MediaRecorder.AudioSource.MIC);
            mediaRecorder.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4);
            mediaRecorder.setAudioEncoder(MediaRecorder.AudioEncoder.AAC);
            mediaRecorder.setAudioSamplingRate(44100);
            mediaRecorder.setAudioEncodingBitRate(128000);
            mediaRecorder.setOutputFile(currentFilePath);

            mediaRecorder.prepare();
            mediaRecorder.start();

            isRecording = true;
            recordingStartTime = System.currentTimeMillis();
            Log.d(TAG, "Recording started: " + currentFilePath);
            return true;
        } catch (Exception e) {
            Log.e(TAG, "Failed to start recording", e);
            releaseRecorder();
            return false;
        }
    }

    public synchronized boolean stopRecording() {
        if (!isRecording || mediaRecorder == null) return false;

        try {
            mediaRecorder.stop();
            Log.d(TAG, "Recording stopped: " + currentFilePath);
        } catch (Exception e) {
            Log.e(TAG, "Error stopping recording", e);
        } finally {
            releaseRecorder();
        }
        return true;
    }

    private void releaseRecorder() {
        isRecording = false;
        if (mediaRecorder != null) {
            try {
                mediaRecorder.reset();
                mediaRecorder.release();
            } catch (Exception ignored) {}
            mediaRecorder = null;
        }
    }

    public static List<CallRecording> getRecordings(Context context) {
        List<CallRecording> list = new ArrayList<>();
        File dir = new File(context.getFilesDir(), "recordings");
        if (!dir.exists() || !dir.isDirectory()) {
            return list;
        }

        File[] files = dir.listFiles((d, name) -> name.endsWith(".m4a") || name.endsWith(".mp3") || name.endsWith(".aac"));
        if (files == null) return list;

        for (File file : files) {
            try {
                String fileName = file.getName();
                String phone = "Unknown";
                String name = "Call Recording";
                long timestamp = file.lastModified();

                // Format: REC_{phone}_{name}_{timestamp}.m4a
                String[] parts = fileName.replace(".m4a", "").split("_");
                if (parts.length >= 4) {
                    phone = parts[1];
                    name = parts[2].replace("_", " ");
                    try {
                        timestamp = Long.parseLong(parts[3]);
                    } catch (Exception ignored) {}
                }

                long duration = 0;
                try {
                    MediaMetadataRetriever mmr = new MediaMetadataRetriever();
                    mmr.setDataSource(file.getAbsolutePath());
                    String durationStr = mmr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION);
                    if (durationStr != null) {
                        duration = Long.parseLong(durationStr);
                    }
                    mmr.release();
                } catch (Exception ignored) {}

                list.add(new CallRecording(
                        file.getName(),
                        file.getAbsolutePath(),
                        phone,
                        name,
                        timestamp,
                        duration,
                        file.length()
                ));
            } catch (Exception ignored) {}
        }

        Collections.sort(list, (a, b) -> Long.compare(b.getTimestamp(), a.getTimestamp()));
        return list;
    }

    public static boolean deleteRecording(CallRecording recording) {
        if (recording == null || recording.getFilePath() == null) return false;
        try {
            File file = new File(recording.getFilePath());
            return file.exists() && file.delete();
        } catch (Exception e) {
            Log.e(TAG, "Failed to delete recording", e);
            return false;
        }
    }
}