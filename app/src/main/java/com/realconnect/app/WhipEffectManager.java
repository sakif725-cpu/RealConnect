package com.realconnect.app;

import android.app.Activity;
import android.content.Context;
import android.content.SharedPreferences;
import android.media.AudioAttributes;
import android.media.AudioFormat;
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
    private static short[] synthesizedShatterBuffer = null;

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
        triggerWhip(context, targetView, 1, null);
    }

    public static void triggerWhip(Context context, View targetView, int hitCount, Runnable onReassembled) {
        if (!isWhipEnabled(context) || targetView == null) return;

        // Render dynamic whip overlay. Wait for the whip to physically reach the target view before animating/shaking it!
        if (context instanceof Activity) {
            WhipOverlayView.show((Activity) context, targetView, () -> {
                // This callback fires at the exact moment the whip strikes the avatar
                if (hitCount >= 5) {
                    playWhipSound();
                    playShatterSound();
                    playHaptic(context, true);
                    AvatarShatterOverlayView.shatter((Activity) context, targetView, onReassembled != null ? onReassembled::run : null);
                } else {
                    playWhipSound();
                    playHaptic(context, false);
                    animateWhipProgressive(targetView, hitCount);
                }
            });
        } else {
            // Fallback for non-activity context
            playWhipSound();
            animateWhipProgressive(targetView, hitCount);
        }
    }

    public static void animateWhip(View view) {
        animateWhipProgressive(view, 1);
    }

    /**
     * Reacts to the whip impact at the instant of strike.
     * The avatar starts with an immediate slash kick & squash in the direction of the hit,
     * followed by an elastic rebound vibration.
     */
    public static void animateWhipProgressive(View view, int hitCount) {
        if (view == null) return;

        float intensity = Math.min(2.2f, 1.0f + (hitCount - 1) * 0.35f);

        // Stage 1: Immediate Impact Kick & Squash (struck by whip)
        view.animate()
                .rotation(28f * intensity)
                .translationX(20f * intensity)
                .scaleX(1.22f)
                .scaleY(0.80f)
                .setDuration(130)
                .setInterpolator(new AccelerateDecelerateInterpolator())
                .withEndAction(() -> {
                    // Stage 2: Overshoot Elastic Rebound back to neutral
                    view.animate()
                            .rotation(0f)
                            .translationX(0f)
                            .scaleX(1.0f)
                            .scaleY(1.0f)
                            .setDuration(390)
                            .setInterpolator(new OvershootInterpolator(3.8f))
                            .start();
                }).start();
    }

    private static void playHaptic(Context context, boolean isShatter) {
        try {
            Vibrator vibrator = (Vibrator) context.getSystemService(Context.VIBRATOR_SERVICE);
            if (vibrator != null && vibrator.hasVibrator()) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    if (isShatter) {
                        long[] timings = {0, 60, 40, 80, 50, 120};
                        int[] amplitudes = {0, 255, 0, 255, 0, 200};
                        vibrator.vibrate(VibrationEffect.createWaveform(timings, amplitudes, -1));
                    } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                        vibrator.vibrate(VibrationEffect.createPredefined(VibrationEffect.EFFECT_HEAVY_CLICK));
                    } else {
                        vibrator.vibrate(45);
                    }
                } else {
                    vibrator.vibrate(isShatter ? 220 : 45);
                }
            }
        } catch (Exception ignored) {}
    }

    /**
     * Synthesized whip buffer from commit 6860e37
     */
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

    private static synchronized short[] getOrCreateShatterBuffer() {
        if (synthesizedShatterBuffer != null) return synthesizedShatterBuffer;

        int sampleRate = 44100;
        int numSamples = (int) (sampleRate * 0.65); // 650ms duration
        short[] buffer = new short[numSamples];
        Random random = new Random();

        // 1. Initial Glass Impact Crunch (0.00s -> 0.08s)
        int crunchSamples = (int) (sampleRate * 0.08);
        for (int i = 0; i < crunchSamples; i++) {
            double t = (double) i / crunchSamples;
            double noise = (random.nextDouble() * 2.0 - 1.0);
            double impulse = Math.sin(2.0 * Math.PI * (2800.0 - 1800.0 * t) * (double) i / sampleRate);
            double env = Math.exp(-4.0 * t);
            buffer[i] = (short) ((noise * 0.75 + impulse * 0.25) * env * Short.MAX_VALUE);
        }

        // 2. High Frequency Shard Fractures & Resonant Tinkle (0.08s -> 0.35s)
        int fractureStart = crunchSamples;
        int fractureSamples = (int) (sampleRate * 0.27);
        for (int i = 0; i < fractureSamples; i++) {
            double t = (double) i / fractureSamples;
            double f1 = Math.sin(2.0 * Math.PI * 3400.0 * (double) i / sampleRate);
            double f2 = Math.sin(2.0 * Math.PI * 4800.0 * (double) i / sampleRate);
            double f3 = Math.sin(2.0 * Math.PI * 1950.0 * (double) i / sampleRate);
            double noise = (random.nextDouble() * 2.0 - 1.0) * 0.45;
            double decay = Math.exp(-8.0 * t);
            buffer[fractureStart + i] = (short) ((f1 * 0.3 + f2 * 0.25 + f3 * 0.2 + noise) * decay * Short.MAX_VALUE * 0.9);
        }

        // 3. Sub-bass boom & crumbling tail (0.35s -> 0.65s)
        int tailStart = fractureStart + fractureSamples;
        int tailSamples = numSamples - tailStart;
        for (int i = 0; i < tailSamples; i++) {
            double t = (double) i / tailSamples;
            double bass = Math.sin(2.0 * Math.PI * 65.0 * (double) i / sampleRate);
            double noise = (random.nextDouble() * 2.0 - 1.0) * 0.2;
            double decay = Math.exp(-6.5 * t);
            buffer[tailStart + i] = (short) ((bass * 0.6 + noise * 0.4) * decay * Short.MAX_VALUE * 0.6);
        }

        synthesizedShatterBuffer = buffer;
        return synthesizedShatterBuffer;
    }

    public static void playWhipSound() {
        playTrack(getOrCreateWhipBuffer(), 350);
    }

    public static void playShatterSound() {
        playTrack(getOrCreateShatterBuffer(), 700);
    }

    private static void playTrack(short[] audioData, int sleepMs) {
        audioExecutor.execute(() -> {
            try {
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

                try {
                    Thread.sleep(sleepMs);
                } catch (InterruptedException ignored) {}
                track.release();
            } catch (Exception ignored) {}
        });
    }
}