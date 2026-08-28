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

public class ContinuousCallAiScanner {

    public interface ScanListener {
        void onListeningTick(int secondsElapsed, int targetSeconds, String statusText);
        void onRiskUpdated(LiveRiskResult result);
    }

    private static final String TAG = "ContinuousCallAiScanner";
    private static final int INITIAL_CONTEXT_TARGET_SECONDS = 30;

    private final Context context;
    private final String phone;
    private final String name;
    private final boolean isPreFlaggedSpam;
    private final ScanListener listener;

    private final Handler handler = new Handler(Looper.getMainLooper());
    private boolean isRunning = false;
    private int secondsElapsed = 0;
    private final StringBuilder accumulatedTranscript = new StringBuilder();

    private SpeechRecognizer speechRecognizer;
    private AudioManager audioManager;
    private Runnable tickerRunnable;

    // Acoustic features accumulated during call
    private byte[] latestAudioSample;
    private double averageEnergy = 0.0;
    private boolean isSyntheticPatternDetected = false;

    public ContinuousCallAiScanner(Context context, String phone, String name, boolean isPreFlaggedSpam, ScanListener listener) {
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
        isSyntheticPatternDetected = false;

        initSilentSpeechRecognizer();
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

    private void initSilentSpeechRecognizer() {
        try {
            if (!SpeechRecognizer.isRecognitionAvailable(context)) {
                Log.w(TAG, "SpeechRecognizer not available");
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
                        restartSilentListening();
                    }
                }

                @Override
                public void onResults(Bundle results) {
                    appendRecognitionResults(results);
                    if (isRunning) restartSilentListening();
                }

                @Override
                public void onPartialResults(Bundle partialResults) {
                    appendRecognitionResults(partialResults);
                }

                @Override public void onEvent(int eventType, Bundle params) {}
            });

            startSilentListeningIntent();
        } catch (Exception e) {
            Log.e(TAG, "Failed to init SpeechRecognizer", e);
        }
    }

    private void startSilentListeningIntent() {
        if (speechRecognizer == null || !isRunning) return;
        try {
            // Temporarily silence system chime stream during startListening
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
            Log.e(TAG, "Failed to start listening intent", e);
        }
    }

    private void restartSilentListening() {
        handler.postDelayed(() -> {
            if (isRunning) startSilentListeningIntent();
        }, 500);
    }

    private void appendRecognitionResults(Bundle bundle) {
        if (bundle == null) return;
        ArrayList<String> matches = bundle.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION);
        if (matches != null && !matches.isEmpty()) {
            for (String match : matches) {
                if (match != null && !match.trim().isEmpty()) {
                    accumulatedTranscript.append(" ").append(match.trim());
                    Log.d(TAG, "Spoken phrase captured: " + match.trim());
                }
            }
        }
    }

    private void startTicker() {
        tickerRunnable = new Runnable() {
            @Override
            public void run() {
                if (!isRunning) return;

                secondsElapsed++;

                // Sample audio snippet silently in background at 10s and 25s
                if (secondsElapsed == 10 || secondsElapsed == 25) {
                    captureBackgroundSnippet();
                }

                if (secondsElapsed < INITIAL_CONTEXT_TARGET_SECONDS) {
                    int remaining = INITIAL_CONTEXT_TARGET_SECONDS - secondsElapsed;
                    String statusText;
                    if (secondsElapsed <= 10) {
                        statusText = "AI: LISTENING (" + secondsElapsed + "s/30s) • CAPTURING VOICE";
                    } else if (secondsElapsed <= 20) {
                        statusText = "AI: LISTENING (" + secondsElapsed + "s/30s) • ANALYZING CONTEXT";
                    } else {
                        statusText = "AI: EVALUATING RISK (" + remaining + "s REMAINING)";
                    }

                    if (listener != null) {
                        listener.onListeningTick(secondsElapsed, INITIAL_CONTEXT_TARGET_SECONDS, statusText);
                    }
                } else if (secondsElapsed == INITIAL_CONTEXT_TARGET_SECONDS || (secondsElapsed > INITIAL_CONTEXT_TARGET_SECONDS && secondsElapsed % 15 == 0)) {
                    // Evaluate risk with full 30s context
                    evaluateCurrentContext();
                }

                handler.postDelayed(this, 1000);
            }
        };
        handler.postDelayed(tickerRunnable, 1000);
    }

    private void captureBackgroundSnippet() {
        AudioRecorderHelper.captureAudioSnippet(context, 2, new AudioRecorderHelper.AudioCaptureCallback() {
            @Override
            public void onAudioCaptured(byte[] audioBytes) {
                latestAudioSample = audioBytes;
                analyzeAcoustics(audioBytes);
            }

            @Override
            public void onError(String errorMessage) {
                Log.w(TAG, "Background audio sample skipped: " + errorMessage);
            }
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

        averageEnergy = rms;

        if (zcr > 0.35 && rms > 200) {
            isSyntheticPatternDetected = true;
        }
    }

    private void evaluateCurrentContext() {
        String contactName = ContactRepository.getInstance(context).findContactByNumber(phone);
        boolean isKnownContact = (contactName != null && !contactName.trim().isEmpty());
        String currentTranscript = accumulatedTranscript.toString().trim();

        if (latestAudioSample != null) {
            AiService.detectBot(latestAudioSample, isBotFromApi -> {
                AiService.checkSpam(phone, isSpamApi -> {
                    boolean isSpam = isPreFlaggedSpam || isSpamApi;
                    boolean isBot = isBotFromApi || isSyntheticPatternDetected;
                    LiveRiskResult result = buildFinalRiskResult(currentTranscript, isKnownContact, isSpam, isBot);
                    if (listener != null) {
                        new Handler(Looper.getMainLooper()).post(() -> listener.onRiskUpdated(result));
                    }
                });
            });
        } else {
            AiService.checkSpam(phone, isSpamApi -> {
                boolean isSpam = isPreFlaggedSpam || isSpamApi;
                LiveRiskResult result = buildFinalRiskResult(currentTranscript, isKnownContact, isSpam, isSyntheticPatternDetected);
                if (listener != null) {
                    new Handler(Looper.getMainLooper()).post(() -> listener.onRiskUpdated(result));
                }
            });
        }
    }

    private LiveRiskResult buildFinalRiskResult(String transcript, boolean isKnownContact, boolean isSpam, boolean isBot) {
        FraudIntelligenceEngine.FraudAssessment assessment = FraudIntelligenceEngine.evaluate(transcript, isKnownContact, isSpam, isBot);

        LiveRiskResult res = new LiveRiskResult(
                assessment.riskLevel,
                assessment.fraudScore,
                assessment.detailedSummary,
                assessment.actionRecommendation
        );
        res.setContextEvaluated(true);
        res.setListeningDurationSeconds(secondsElapsed);
        res.setBot(isBot);
        res.setSpam(isSpam);
        res.setTrustedContact(isKnownContact);
        res.setTranscriptExcerpt(transcript);

        for (String ind : assessment.detectedIndicators) {
            res.addIndicator(ind);
        }
        for (String kw : assessment.flaggedKeywords) {
            res.addFlaggedKeyword(kw);
        }

        return res;
    }
}