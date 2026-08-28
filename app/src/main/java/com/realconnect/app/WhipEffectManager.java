package com.realconnect.app;

import android.content.Context;
import android.content.SharedPreferences;
import android.media.AudioAttributes;
import android.media.AudioFormat;
import android.media.AudioManager;
import android.media.AudioTrack;
import android.os.Build;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.view.View;
import android.view.animation.AccelerateDecelerateInterpolator;
import android.view.animation.OvershootInterpolator;
import java.util.Random;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class WhipEffectManager {

    private static final String PREF_NAME = "MagicPrefs";
    private static final String KEY_WHIP_ENABLED = "magic_whip_enabled";

    private static final ExecutorService audioExecutor = Executors.newSingleThreadExecutor();
    private static short[] synthesizedWhipBuffer = null;

    public static boolean isWhipEnabled(Context context) {
        if (context == null) return true;
        SharedPreferences prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
        return prefs.getBoolean(KEY_WHIP_ENABLED, true);
    }

    public static void setWhipEnabled(Context context, boolean enabled) {
        if (context == null) return;
        SharedPreferences prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
        prefs.edit().putBoolean(KEY_WHIP_ENABLED, enabled).apply();
    }

    public static void triggerWhip(Context context, View targetView) {
        if (!isWhipEnabled(context)) return;

        // 1. Play synthesized whip crack audio
        playWhipSound();

        // 2. Play haptic feedback
        playHaptic(context);

        // 3. Play visual whip animation
        if (targetView != null) {
            animateWhip(targetView);
        }
    }

    public static void animateWhip(View view) {
        if (view == null) return;

        // Stage 1: Quick Windup (tilt back & stretch)
        view.animate()
                .rotation(-24f)
                .translationX(-18f)
                .scaleX(0.9f)
                .scaleY(1.1f)
                .setDuration(75)
                .setInterpolator(new AccelerateDecelerateInterpolator())
                .withEndAction(() -> {
                    // Stage 2: Fast Whipping Snap (forward slash & squash)
                    view.animate()
                            .rotation(36f)
                            .translationX(28f)
                            .scaleX(1.3f)
                            .scaleY(0.72f)
                            .setDuration(95)
                            .setInterpolator(new AccelerateDecelerateInterpolator())
                            .withEndAction(() -> {
                                // Stage 3: Elastic Rebound Vibration back to neutral
                                view.animate()
                                        .rotation(0f)
                                        .translationX(0f)
                                        .scaleX(1.0f)
                                        .scaleY(1.0f)
                                        .setDuration(260)
                                        .setInterpolator(new OvershootInterpolator(3.8f))
                                        .start();
                            }).start();
                }).start();
    }

    private static void playHaptic(Context context) {
        try {
            Vibrator vibrator = (Vibrator) context.getSystemService(Context.VIBRATOR_SERVICE);
            if (vibrator != null && vibrator.hasVibrator()) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    vibrator.vibrate(VibrationEffect.createPredefined(VibrationEffect.EFFECT_HEAVY_CLICK));
                } else {
                    vibrator.vibrate(40);
                }
            }
        } catch (Exception ignored) {}
    }

    private static synchronized short[] getOrCreateWhipBuffer() {
        if (synthesizedWhipBuffer != null) return synthesizedWhipBuffer;

        int sampleRate = 44100;
        int numSamples = (int) (sampleRate * 0.32); // 320ms duration
        short[] buffer = new short[numSamples];
        Random random = new Random();

        // 0.00s -> 0.10s : Rising whoosh windup
        int whooshSamples = (int) (sampleRate * 0.10);
        for (int i = 0; i < whooshSamples; i++) {
            double t = (double) i / whooshSamples;
            double freq = 200.0 + (900.0 * t * t);
            double sin = Math.sin(2.0 * Math.PI * freq * (double) i / sampleRate);
            double noise = (random.nextDouble() * 2.0 - 1.0) * 0.4;
            double env = t * t * 0.5;
            buffer[i] = (short) ((sin + noise) * env * Short.MAX_VALUE);
        }

        // 0.10s -> 0.13s : High energy whip crack impact
        int snapStart = whooshSamples;
        int snapSamples = (int) (sampleRate * 0.03);
        for (int i = 0; i < snapSamples; i++) {
            double t = (double) i / snapSamples;
            double noise = (random.nextDouble() * 2.0 - 1.0);
            double impulse = (i % 2 == 0 ? 1.0 : -1.0) * (1.0 - t * 0.5);
            buffer[snapStart + i] = (short) ((noise * 0.7 + impulse * 0.3) * Short.MAX_VALUE);
        }

        // 0.13s -> 0.32s : Exponential decay snap tail
        int tailStart = snapStart + snapSamples;
        int tailSamples = numSamples - tailStart;
        for (int i = 0; i < tailSamples; i++) {
            double t = (double) i / tailSamples;
            double decay = Math.exp(-12.0 * t);
            double noise = (random.nextDouble() * 2.0 - 1.0) * decay;
            buffer[tailStart + i] = (short) (noise * Short.MAX_VALUE * 0.85);
        }

        synthesizedWhipBuffer = buffer;
        return synthesizedWhipBuffer;
    }

    public static void playWhipSound() {
        audioExecutor.execute(() -> {
            try {
                short[] audioData = getOrCreateWhipBuffer();
                int bufferSize = audioData.length * 2;

                AudioTrack track = new AudioTrack.Builder()
                        .setAudioAttributes(new AudioAttributes.Builder()
                                .setUsage(AudioAttributes.USAGE_ASSISTANCE_SONIFICATION)
                                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                                .build())
                        .setAudioFormat(new AudioFormat.Builder()
                                .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                                .setSampleRate(44100)
                                .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                                .build())
                        .setBufferSizeInBytes(bufferSize)
                        .setTransferMode(AudioTrack.MODE_STATIC)
                        .build();

                track.write(audioData, 0, audioData.length);
                track.play();

                // Release after playing
                try {
                    Thread.sleep(350);
                } catch (InterruptedException ignored) {}
                track.release();
            } catch (Exception ignored) {}
        });
    }
}