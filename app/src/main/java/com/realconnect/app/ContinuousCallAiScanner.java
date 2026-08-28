package com.realconnect.app;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import java.util.ArrayList;
import java.util.List;

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
    }

    public void start() {
        if (isRunning) return;
        isRunning = true;
        secondsElapsed = 0;
        isSyntheticPatternDetected = false;

        startTicker();
    }

    public void stop() {
        isRunning = false;
        if (tickerRunnable != null) {
            handler.removeCallbacks(tickerRunnable);
            tickerRunnable = null;
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
                        statusText = "AI: LISTENING (" + secondsElapsed + "s/30s) • SAMPLING AUDIO";
                    } else if (secondsElapsed <= 20) {
                        statusText = "AI: LISTENING (" + secondsElapsed + "s/30s) • ANALYZING VOICE PATTERNS";
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

        // Calculate RMS Energy and Zero-Crossing Rate (ZCR)
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

        // Abnormally uniform high zero crossing with low energy variance indicates synthetic vocoder / robocall
        if (zcr > 0.35 && rms > 200) {
            isSyntheticPatternDetected = true;
        }
    }

    private void evaluateCurrentContext() {
        String contactName = ContactRepository.getInstance(context).findContactByNumber(phone);
        boolean isKnownContact = (contactName != null && !contactName.trim().isEmpty());

        if (latestAudioSample != null) {
            AiService.detectBot(latestAudioSample, isBotFromApi -> {
                AiService.checkSpam(phone, isSpamApi -> {
                    boolean isSpam = isPreFlaggedSpam || isSpamApi;
                    boolean isBot = isBotFromApi || isSyntheticPatternDetected;
                    LiveRiskResult result = buildFinalRiskResult(isKnownContact, isSpam, isBot);
                    if (listener != null) {
                        new Handler(Looper.getMainLooper()).post(() -> listener.onRiskUpdated(result));
                    }
                });
            });
        } else {
            AiService.checkSpam(phone, isSpamApi -> {
                boolean isSpam = isPreFlaggedSpam || isSpamApi;
                LiveRiskResult result = buildFinalRiskResult(isKnownContact, isSpam, isSyntheticPatternDetected);
                if (listener != null) {
                    new Handler(Looper.getMainLooper()).post(() -> listener.onRiskUpdated(result));
                }
            });
        }
    }

    private LiveRiskResult buildFinalRiskResult(boolean isKnownContact, boolean isSpam, boolean isBot) {
        FraudIntelligenceEngine.FraudAssessment assessment = FraudIntelligenceEngine.evaluate("", isKnownContact, isSpam, isBot);

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

        for (String ind : assessment.detectedIndicators) {
            res.addIndicator(ind);
        }
        for (String kw : assessment.flaggedKeywords) {
            res.addFlaggedKeyword(kw);
        }

        return res;
    }
}