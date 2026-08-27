package com.realconnect.app;

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import android.media.AudioFormat;
import android.media.AudioRecord;
import android.media.MediaRecorder;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import androidx.core.app.ActivityCompat;
import java.io.ByteArrayOutputStream;

public class AudioRecorderHelper {

    private static final String TAG = "AudioRecorderHelper";

    public interface AudioCaptureCallback {
        void onAudioCaptured(byte[] audioBytes);
        void onError(String errorMessage);
    }

    public static void captureAudioSnippet(Context context, int durationSeconds, AudioCaptureCallback callback) {
        if (ActivityCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            callback.onError("RECORD_AUDIO permission not granted");
            return;
        }

        new Thread(() -> {
            int sampleRate = 16000;
            int channelConfig = AudioFormat.CHANNEL_IN_MONO;
            int audioFormat = AudioFormat.ENCODING_PCM_16BIT;
            int minBufferSize = AudioRecord.getMinBufferSize(sampleRate, channelConfig, audioFormat);
            int bufferSize = Math.max(minBufferSize, sampleRate * 2);

            AudioRecord recorder = null;
            try {
                recorder = new AudioRecord(
                        MediaRecorder.AudioSource.VOICE_COMMUNICATION,
                        sampleRate,
                        channelConfig,
                        audioFormat,
                        bufferSize
                );

                if (recorder.getState() != AudioRecord.STATE_INITIALIZED) {
                    recorder = new AudioRecord(
                            MediaRecorder.AudioSource.MIC,
                            sampleRate,
                            channelConfig,
                            audioFormat,
                            bufferSize
                    );
                }

                if (recorder.getState() != AudioRecord.STATE_INITIALIZED) {
                    new Handler(Looper.getMainLooper()).post(() -> callback.onError("Failed to initialize AudioRecord"));
                    return;
                }

                recorder.startRecording();
                ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
                byte[] tempBuffer = new byte[1024];
                long endTime = System.currentTimeMillis() + (durationSeconds * 1000L);

                while (System.currentTimeMillis() < endTime) {
                    int read = recorder.read(tempBuffer, 0, tempBuffer.length);
                    if (read > 0) {
                        outputStream.write(tempBuffer, 0, read);
                    }
                }

                recorder.stop();
                byte[] audioBytes = outputStream.toByteArray();
                new Handler(Looper.getMainLooper()).post(() -> callback.onAudioCaptured(audioBytes));

            } catch (Exception e) {
                Log.e(TAG, "Audio capture error", e);
                new Handler(Looper.getMainLooper()).post(() -> callback.onError(e.getMessage()));
            } finally {
                if (recorder != null) {
                    try {
                        recorder.release();
                    } catch (Exception ignored) {}
                }
            }
        }).start();
    }
}