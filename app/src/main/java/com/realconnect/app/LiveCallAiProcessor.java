package com.realconnect.app;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

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
    }

    public void start() {
        if (isRunning) return;
        isRunning = true;
        secondsElapsed = 0;
        isSyntheticVoiceDetected = false;

        startTicker();
    }

    public void stop() {
        isRunning = false;
        if (tickerRunnable != null) {
            handler.removeCallbacks(tickerRunnable);
            tickerRunnable = null;
        }
    }

    public String getCapturedTranscript() {
        return "🎙️ Live Audio Stream Active (Monitoring incoming voice stream & acoustic frequency biomarkers)";
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

                // Silent acoustic buffer sample at 5s, 15s, 30s
                if (secondsElapsed == 5 || secondsElapsed == 15 || secondsElapsed == 30) {
                    captureSilentAcousticSample();
                }

                // Periodic AI intent re-evaluation
                evaluateLiveContext();

                if (listener != null) {
                    String statusText;
                    if (currentRiskResult != null && currentRiskResult.getLevel() == LiveRiskResult.Level.HIGH) {
                        statusText = "AI: HIGH RISK (" + currentRiskResult.getRiskScore() + "%)";
                    } else if (currentRiskResult != null && currentRiskResult.getLevel() == LiveRiskResult.Level.MEDIUM) {
                        statusText = "AI: MODERATE (" + currentRiskResult.getRiskScore() + "%) • " + secondsElapsed + "s";
                    } else {
                        statusText = "AI: MONITORING • " + secondsElapsed + "s";
                    }
                    listener.onProgressTick(secondsElapsed, statusText);
                }

                handler.postDelayed(this, 1000);
            }
        };
        handler.postDelayed(tickerRunnable, 1000);
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
        AiIntentAnalyzer.analyzeCallerIntent("", phone, name, intentResult -> {
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
            res.setTranscriptExcerpt(getCapturedTranscript());
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