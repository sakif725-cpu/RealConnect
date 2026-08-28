package com.realconnect.app;

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
    private static short[] synthesizedBeatingBuffer = null;

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

        // 1. Render 3-Hit Beating Combo Visual Overlay
        if (context instanceof android.app.Activity && targetView != null) {
            WhipOverlayView.show((android.app.Activity) context, targetView);
        }

        // 2. Play 3-Hit Rhythmic Beating Audio & Haptics
        playWhipSound();
        playHaptic(context);

        // 3. Play 3-Hit Beating Reaction on Avatar
        if (targetView != null) {
            animateWhip(targetView);
        }
    }

    public static void animateWhip(View view) {
        if (view == null) return;

        // Hit 1: Left Whip Strike (Tilt right & recoil)
        view.animate()
                .rotation(22f)
                .translationX(18f)
                .scaleX(1.15f)
                .scaleY(0.88f)
                .setDuration(90)
                .setInterpolator(new AccelerateDecelerateInterpolator())
                .withEndAction(() -> {
                    // Hit 2: Counter Right Whip Strike (Whack to the left)
                    view.animate()
                            .rotation(-28f)
                            .translationX(-22f)
                            .scaleX(0.88f)
                            .scaleY(1.15f)
                            .setDuration(120)
                            .setInterpolator(new AccelerateDecelerateInterpolator())
                            .withEndAction(() -> {
                                // Hit 3: Heavy Overhead Power Slam (Squash down & explosive recoil)
                                view.animate()
                                        .rotation(0f)
                                        .translationX(0f)
                                        .translationY(16f)
                                        .scaleX(1.35f)
                                        .scaleY(0.68f)
                                        .setDuration(130)
                                        .setInterpolator(new AccelerateDecelerateInterpolator())
                                        .withEndAction(() -> {
                                            // Final Elastic Spring Rebound to neutral
                                            view.animate()
                                                    .rotation(0f)
                                                    .translationX(0f)
                                                    .translationY(0f)
                                                    .scaleX(1.0f)
                                                    .scaleY(1.0f)
                                                    .setDuration(320)
                                                    .setInterpolator(new OvershootInterpolator(4.2f))
                                                    .start();
                                        }).start();
                            }).start();
                }).start();
    }

    private static void playHaptic(Context context) {
        try {
            Vibrator vibrator = (Vibrator) context.getSystemService(Context.VIBRATOR_SERVICE);
            if (vibrator != null && vibrator.hasVibrator()) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    long[] timings = new long[]{0, 30, 110, 40, 130, 70};
                    int[] amplitudes = new int[]{0, 180, 0, 220, 0, 255};
                    vibrator.vibrate(VibrationEffect.createWaveform(timings, amplitudes, -1));
                } else {
                    vibrator.vibrate(new long[]{0, 30, 110, 40, 130, 70}, -1);
                }
            }
        } catch (Exception ignored) {}
    }

    private static synchronized short[] getOrCreateBeatingBuffer() {
        if (synthesizedBeatingBuffer != null) return synthesizedBeatingBuffer;

        int sampleRate = 44100;
        int totalSamples = (int) (sampleRate * 0.70); // 700ms total beating combo
        short[] buffer = new short[totalSamples];
        Random random = new Random();

        // Helper to synthesize a whip crack burst at a given start sample
        int[] hitTimes = new int[]{
                (int) (sampleRate * 0.06), // Hit 1 at ~60ms
                (int) (sampleRate * 0.25), // Hit 2 at ~250ms
                (int) (sampleRate * 0.46)  // Hit 3 heavy slam at ~460ms
        };

        for (int h = 0; h < hitTimes.length; h++) {
            int start = hitTimes[h];
            boolean isHeavy = (h == 2);
            int hitDur = (int) (sampleRate * (isHeavy ? 0.22 : 0.16));

            for (int i = 0; i < hitDur && (start + i) < totalSamples; i++) {
                double t = (double) i / hitDur;
                double sound;

                if (i < (int) (sampleRate * 0.025)) {
                    // Sudden sharp crack impulse
                    double noise = (random.nextDouble() * 2.0 - 1.0);
                    double impulse = (i % 2 == 0 ? 1.0 : -1.0);
                    sound = (noise * 0.75 + impulse * 0.25) * (isHeavy ? 1.0 : 0.8);
                } else {
                    // Exponential snap decay + bass thump for heavy hit
                    double decay = Math.exp((isHeavy ? -9.0 : -14.0) * t);
                    double noise = (random.nextDouble() * 2.0 - 1.0) * decay;
                    double bassThump = isHeavy ? (Math.sin(2.0 * Math.PI * 120.0 * (double) i / sampleRate) * decay * 0.5) : 0;
                    sound = (noise + bassThump) * (isHeavy ? 0.95 : 0.75);
                }

                int sampleVal = (int) (sound * Short.MAX_VALUE);
                buffer[start + i] = (short) Math.max(Short.MIN_VALUE, Math.min(Short.MAX_VALUE, sampleVal));
            }
        }

        synthesizedBeatingBuffer = buffer;
        return synthesizedBeatingBuffer;
    }

    public static void playWhipSound() {
        audioExecutor.execute(() -> {
            try {
                short[] audioData = getOrCreateBeatingBuffer();
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
                    Thread.sleep(750);
                } catch (InterruptedException ignored) {}
                track.release();
            } catch (Exception ignored) {}
        });
    }
}