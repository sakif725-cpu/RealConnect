package com.realconnect.app;

import android.content.Context;
import android.content.Intent;
import android.media.AudioManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.speech.RecognitionListener;
import android.speech.RecognizerIntent;
import android.speech.SpeechRecognizer;
import android.util.Log;
import java.util.ArrayList;
import java.util.Locale;

public class LiveCallAiProcessor {

    public interface AiScanListener {
        void onProgressTick(int secondsElapsed, String statusSummary);
        void onRiskUpdated(LiveRiskResult result);
    }

    private static final String TAG = "LiveCallAiProcessor";

    private final Context context;
    private final String phone;
    private final String name;
    private final boolean isPreFlaggedSpam;
    private final AiScanListener listener;

    private final Handler handler = new Handler(Looper.getMainLooper());
    private boolean isRunning = false;
    private int secondsElapsed = 0;
    private final StringBuilder accumulatedTranscript = new StringBuilder();

    private SpeechRecognizer speechRecognizer;
    private AudioManager audioManager;
    private Runnable tickerRunnable;

    private byte[] latestAudioBytes;
    private boolean isSyntheticVoiceDetected = false;
    private LiveRiskResult currentRiskResult;

    public LiveCallAiProcessor(Context context, String phone, String name, boolean isPreFlaggedSpam, AiScanListener listener) {
        this.context = context;
        this.phone = phone;
        this.name = name;
        this.isPreFlaggedSpam = isPreFlaggedSpam;
        this.listener = listener;
        this.audioManager = (AudioManager) context.getSystemService(Context.AUDIO_SERVICE);
    }

    public void start() {
        if (isRunning) return;
        isRunning = true;
        secondsElapsed = 0;
        accumulatedTranscript.setLength(0);
        isSyntheticVoiceDetected = false;

        initSilentAudioCapture();
        startTicker();
    }

    public void stop() {
        isRunning = false;
        if (tickerRunnable != null) {
            handler.removeCallbacks(tickerRunnable);
            tickerRunnable = null;
        }
        if (speechRecognizer != null) {
            try {
                speechRecognizer.stopListening();
                speechRecognizer.cancel();
                speechRecognizer.destroy();
            } catch (Exception ignored) {}
            speechRecognizer = null;
        }
    }

    public String getCapturedTranscript() {
        String text = accumulatedTranscript.toString().trim();
        return text.isEmpty() ? "Listening to live audio stream (No speech detected yet)..." : text;
    }

    public LiveRiskResult getCurrentRiskResult() {
        if (currentRiskResult == null) {
            evaluateLiveContext();
        }
        return currentRiskResult;
    }

    public int getSecondsElapsed() {
        return secondsElapsed;
    }

    private void startTicker() {
        tickerRunnable = new Runnable() {
            @Override
            public void run() {
                if (!isRunning) return;

                secondsElapsed++;

                // Silent acoustic sample at 5s, 15s, 30s
                if (secondsElapsed == 5 || secondsElapsed == 15 || secondsElapsed == 30) {
                    captureSilentAcousticSample();
                }

                // Periodic AI context re-evaluation
                evaluateLiveContext();

                if (listener != null) {
                    String statusText;
                    if (currentRiskResult != null && currentRiskResult.getLevel() == LiveRiskResult.Level.HIGH) {
                        statusText = "AI: HIGH RISK (" + currentRiskResult.getRiskScore() + "%)";
                    } else if (currentRiskResult != null && currentRiskResult.getLevel() == LiveRiskResult.Level.MEDIUM) {
                        statusText = "AI: MODERATE (" + currentRiskResult.getRiskScore() + "%) • " + secondsElapsed + "s";
                    } else {
                        statusText = "AI: SAFE (" + (currentRiskResult != null ? currentRiskResult.getRiskScore() : 5) + "%) • " + secondsElapsed + "s";
                    }
                    listener.onProgressTick(secondsElapsed, statusText);
                }

                handler.postDelayed(this, 1000);
            }
        };
        handler.postDelayed(tickerRunnable, 1000);
    }

    private void initSilentAudioCapture() {
        try {
            if (!SpeechRecognizer.isRecognitionAvailable(context)) {
                Log.w(TAG, "SpeechRecognizer not available on device");
                return;
            }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && SpeechRecognizer.isOnDeviceRecognitionAvailable(context)) {
                speechRecognizer = SpeechRecognizer.createOnDeviceSpeechRecognizer(context);
            } else {
                speechRecognizer = SpeechRecognizer.createSpeechRecognizer(context);
            }

            speechRecognizer.setRecognitionListener(new RecognitionListener() {
                @Override public void onReadyForSpeech(Bundle params) {}
                @Override public void onBeginningOfSpeech() {}
                @Override public void onRmsChanged(float rmsdB) {}
                @Override public void onBufferReceived(byte[] buffer) {}
                @Override public void onEndOfSpeech() {}
                @Override public void onError(int error) {
                    if (isRunning && speechRecognizer != null) {
                        restartSilentCapture();
                    }
                }

                @Override
                public void onResults(Bundle results) {
                    processSpeechResults(results);
                    if (isRunning) restartSilentCapture();
                }

                @Override
                public void onPartialResults(Bundle partialResults) {
                    processSpeechResults(partialResults);
                }

                @Override public void onEvent(int eventType, Bundle params) {}
            });

            startSilentListening();
        } catch (Exception e) {
            Log.e(TAG, "Audio capture init failed", e);
        }
    }

    private void startSilentListening() {
        if (speechRecognizer == null || !isRunning) return;
        try {
            if (audioManager != null) {
                audioManager.adjustStreamVolume(AudioManager.STREAM_SYSTEM, AudioManager.ADJUST_MUTE, 0);
            }

            Intent intent = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
            intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
            intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault().toString());
            intent.putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true);
            intent.putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 3);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                intent.putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, true);
            }

            speechRecognizer.startListening(intent);
        } catch (Exception e) {
            Log.e(TAG, "Silent listening start failed", e);
        }
    }

    private void restartSilentCapture() {
        handler.postDelayed(() -> {
            if (isRunning) startSilentListening();
        }, 500);
    }

    private void processSpeechResults(Bundle bundle) {
        if (bundle == null) return;
        ArrayList<String> matches = bundle.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION);
        if (matches != null && !matches.isEmpty()) {
            for (String match : matches) {
                if (match != null && !match.trim().isEmpty()) {
                    accumulatedTranscript.append(" ").append(match.trim());
                }
            }
            evaluateLiveContext();
        }
    }

    private void captureSilentAcousticSample() {
        AudioRecorderHelper.captureAudioSnippet(context, 2, new AudioRecorderHelper.AudioCaptureCallback() {
            @Override
            public void onAudioCaptured(byte[] audioBytes) {
                latestAudioBytes = audioBytes;
                analyzeAcoustics(audioBytes);
            }

            @Override public void onError(String errorMessage) {}
        });
    }

    private void analyzeAcoustics(byte[] audioBytes) {
        if (audioBytes == null || audioBytes.length < 100) return;

        long sum = 0;
        int zeroCrossings = 0;
        int prevSample = 0;

        for (int i = 44; i < audioBytes.length - 1; i += 2) {
            short sample = (short) ((audioBytes[i + 1] << 8) | (audioBytes[i] & 0xff));
            sum += (long) sample * sample;

            if ((sample >= 0 && prevSample < 0) || (sample < 0 && prevSample >= 0)) {
                zeroCrossings++;
            }
            prevSample = sample;
        }

        int totalSamples = Math.max(1, (audioBytes.length - 44) / 2);
        double rms = Math.sqrt((double) sum / totalSamples);
        double zcr = (double) zeroCrossings / totalSamples;

        if (zcr > 0.35 && rms > 200) {
            isSyntheticVoiceDetected = true;
        }
    }

    private void evaluateLiveContext() {
        String transcript = accumulatedTranscript.toString().trim();

        AiIntentAnalyzer.analyzeCallerIntent(transcript, phone, name, intentResult -> {
            LiveRiskResult res = new LiveRiskResult(
                    intentResult.riskLevel,
                    intentResult.riskScore,
                    intentResult.summary,
                    intentResult.recommendation
            );
            res.setContextEvaluated(true);
            res.setListeningDurationSeconds(secondsElapsed);
            res.setBot(isSyntheticVoiceDetected);
            res.setSpam(isPreFlaggedSpam);
            res.setTranscriptExcerpt(transcript);
            res.setCallerIntent(intentResult.intention);

            for (String ind : intentResult.threatIndicators) {
                res.addIndicator(ind);
            }
            for (String kw : intentResult.flaggedKeywords) {
                res.addFlaggedKeyword(kw);
            }

            currentRiskResult = res;

            if (listener != null) {
                listener.onRiskUpdated(res);
            }
        });
    }
}