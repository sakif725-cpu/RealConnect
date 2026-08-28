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

        // 1. Render dynamic visible whip rope overlay across screen
        if (context instanceof android.app.Activity && targetView != null) {
            WhipOverlayView.show((android.app.Activity) context, targetView);
        }

        // 2. Play synthesized whip crack audio & haptics
        playWhipSound();
        playHaptic(context);

        // 3. Play avatar physics reaction
        if (targetView != null) {
            animateWhip(targetView);
        }
    }

    public static void animateWhip(View view) {
        if (view == null) return;

        // Stage 1: Windup (tilt back & stretch)
        view.animate()
                .rotation(-22f)
                .translationX(-16f)
                .scaleX(0.92f)
                .scaleY(1.08f)
                .setDuration(130)
                .setInterpolator(new AccelerateDecelerateInterpolator())
                .withEndAction(() -> {
                    // Stage 2: Whipping Snap (forward slash & squash)
                    view.animate()
                            .rotation(32f)
                            .translationX(24f)
                            .scaleX(1.26f)
                            .scaleY(0.76f)
                            .setDuration(160)
                            .setInterpolator(new AccelerateDecelerateInterpolator())
                            .withEndAction(() -> {
                                // Stage 3: Elastic Rebound Vibration back to neutral
                                view.animate()
                                        .rotation(0f)
                                        .translationX(0f)
                                        .scaleX(1.0f)
                                        .scaleY(1.0f)
                                        .setDuration(390)
                                        .setInterpolator(new OvershootInterpolator(3.6f))
                                        .start();
                            }).start();
                }).start();
    }

    private static void playHaptic(Context context) {
        try {
            Vibrator vibrator = (Vibrator) context.getSystemService(Context.VIBRATOR_SERVICE);
            if (vibrator != null && vibrator.hasVibrator()) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    long[] timings = new long[]{0, 35, 110, 45, 120, 75};
                    int[] amplitudes = new int[]{0, 190, 0, 230, 0, 255};
                    vibrator.vibrate(VibrationEffect.createWaveform(timings, amplitudes, -1));
                } else {
                    vibrator.vibrate(new long[]{0, 35, 110, 45, 120, 75}, -1);
                }
            }
        } catch (Exception ignored) {}
    }

    private static synchronized short[] getOrCreateWhipBuffer() {
        if (synthesizedWhipBuffer != null) return synthesizedWhipBuffer;

        int sampleRate = 44100;
        int totalSamples = (int) (sampleRate * 0.68); // 680ms beating audio
        short[] buffer = new short[totalSamples];
        Random random = new Random();

        // Rhythmic beating crack hits with bass punch
        int[] hitTimes = new int[]{
                (int) (sampleRate * 0.05), // Hit 1 at ~50ms
                (int) (sampleRate * 0.22), // Hit 2 at ~220ms
                (int) (sampleRate * 0.42)  // Hit 3 heavy power crack at ~420ms
        };

        for (int h = 0; h < hitTimes.length; h++) {
            int start = hitTimes[h];
            boolean isHeavy = (h == 2);
            int hitDur = (int) (sampleRate * (isHeavy ? 0.24 : 0.16));

            for (int i = 0; i < hitDur && (start + i) < totalSamples; i++) {
                double t = (double) i / hitDur;
                double sound;

                if (i < (int) (sampleRate * 0.028)) {
                    // Sharp impact impulse
                    double noise = (random.nextDouble() * 2.0 - 1.0);
                    double impulse = (i % 2 == 0 ? 1.0 : -1.0);
                    sound = (noise * 0.75 + impulse * 0.25) * (isHeavy ? 1.0 : 0.82);
                } else {
                    // Exponential snap decay + bass thump for heavy hit
                    double decay = Math.exp((isHeavy ? -8.5 : -13.0) * t);
                    double noise = (random.nextDouble() * 2.0 - 1.0) * decay;
                    double bassThump = isHeavy ? (Math.sin(2.0 * Math.PI * 110.0 * (double) i / sampleRate) * decay * 0.55) : 0;
                    sound = (noise + bassThump) * (isHeavy ? 0.95 : 0.78);
                }

                int sampleVal = (int) (sound * Short.MAX_VALUE);
                buffer[start + i] = (short) Math.max(Short.MIN_VALUE, Math.min(Short.MAX_VALUE, sampleVal));
            }
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
                    Thread.sleep(700);
                } catch (InterruptedException ignored) {}
                track.release();
            } catch (Exception ignored) {}
        });
    }
}