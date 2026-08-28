package com.realconnect.app;

import android.content.Context;
import android.content.Intent;
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
    private Runnable tickerRunnable;

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
        accumulatedTranscript.setLength(0);

        initSpeechRecognizer();
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

    private void startTicker() {
        tickerRunnable = new Runnable() {
            @Override
            public void run() {
                if (!isRunning) return;

                secondsElapsed++;

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
                    // Evaluate risk with 30s of conversation context
                    evaluateCurrentContext();
                }

                handler.postDelayed(this, 1000);
            }
        };
        handler.postDelayed(tickerRunnable, 1000);
    }

    private void initSpeechRecognizer() {
        try {
            if (!SpeechRecognizer.isRecognitionAvailable(context)) {
                Log.w(TAG, "SpeechRecognizer not available on this device");
                return;
            }

            speechRecognizer = SpeechRecognizer.createSpeechRecognizer(context);
            speechRecognizer.setRecognitionListener(new RecognitionListener() {
                @Override public void onReadyForSpeech(Bundle params) {}
                @Override public void onBeginningOfSpeech() {}
                @Override public void onRmsChanged(float rmsdB) {}
                @Override public void onBufferReceived(byte[] buffer) {}
                @Override public void onEndOfSpeech() {}
                @Override public void onError(int error) {
                    if (isRunning && speechRecognizer != null) {
                        restartListening();
                    }
                }

                @Override
                public void onResults(Bundle results) {
                    appendRecognitionResults(results);
                    if (isRunning) restartListening();
                }

                @Override
                public void onPartialResults(Bundle partialResults) {
                    appendRecognitionResults(partialResults);
                }

                @Override public void onEvent(int eventType, Bundle params) {}
            });

            startListeningIntent();
        } catch (Exception e) {
            Log.e(TAG, "SpeechRecognizer init failed", e);
        }
    }

    private void startListeningIntent() {
        if (speechRecognizer == null || !isRunning) return;
        try {
            Intent intent = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
            intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
            intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault().toString());
            intent.putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true);
            intent.putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 3);
            speechRecognizer.startListening(intent);
        } catch (Exception e) {
            Log.e(TAG, "Failed to start listening intent", e);
        }
    }

    private void restartListening() {
        handler.postDelayed(() -> {
            if (isRunning) startListeningIntent();
        }, 300);
    }

    private void appendRecognitionResults(Bundle bundle) {
        if (bundle == null) return;
        ArrayList<String> matches = bundle.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION);
        if (matches != null && !matches.isEmpty()) {
            for (String match : matches) {
                if (match != null && !match.trim().isEmpty()) {
                    accumulatedTranscript.append(" ").append(match.trim());
                }
            }
        }
    }

    private void evaluateCurrentContext() {
        String transcript = accumulatedTranscript.toString();
        ScamPatternDetector.AnalysisOutcome outcome = ScamPatternDetector.analyze(transcript);

        String contactName = ContactRepository.getInstance(context).findContactByNumber(phone);
        boolean isKnownContact = (contactName != null && !contactName.trim().isEmpty());

        // Perform audio capture & bot analysis
        AudioRecorderHelper.captureAudioSnippet(context, 2, new AudioRecorderHelper.AudioCaptureCallback() {
            @Override
            public void onAudioCaptured(byte[] audioBytes) {
                AiService.detectBot(audioBytes, isBot -> {
                    AiService.checkSpam(phone, isSpamApi -> {
                        boolean isSpam = isPreFlaggedSpam || isSpamApi;
                        LiveRiskResult result = buildFinalRiskResult(outcome, isKnownContact, isSpam, isBot);
                        if (listener != null) {
                            new Handler(Looper.getMainLooper()).post(() -> listener.onRiskUpdated(result));
                        }
                    });
                });
            }

            @Override
            public void onError(String errorMessage) {
                AiService.checkSpam(phone, isSpamApi -> {
                    boolean isSpam = isPreFlaggedSpam || isSpamApi;
                    LiveRiskResult result = buildFinalRiskResult(outcome, isKnownContact, isSpam, false);
                    if (listener != null) {
                        new Handler(Looper.getMainLooper()).post(() -> listener.onRiskUpdated(result));
                    }
                });
            }
        });
    }

    private LiveRiskResult buildFinalRiskResult(ScamPatternDetector.AnalysisOutcome outcome, boolean isKnownContact, boolean isSpam, boolean isBot) {
        int baseRisk = 0;
        if (isKnownContact) baseRisk = 5;
        else if (isSpam) baseRisk = 75;
        else baseRisk = 30;

        int finalScore = Math.min(baseRisk + outcome.scamScoreBonus + (isBot ? 40 : 0), 99);
        LiveRiskResult.Level level;

        if (finalScore >= 65 || outcome.isFinancialPhishing || outcome.isLotteryScam || outcome.isOtpTheft || isBot) {
            level = LiveRiskResult.Level.HIGH;
        } else if (finalScore >= 35 || !isKnownContact) {
            level = LiveRiskResult.Level.MEDIUM;
        } else {
            level = LiveRiskResult.Level.LOW;
        }

        String summary;
        String recommendation;

        if (outcome.isFinancialPhishing && outcome.isLotteryScam) {
            summary = "Financial Phishing & Fake Lottery Scam Detected";
            recommendation = "Caller is attempting to obtain bank/card details under the guise of a prize. DO NOT disclose CVV or OTP. Hang up immediately.";
        } else if (outcome.isFinancialPhishing) {
            summary = "Suspicious Financial / Banking Data Request";
            recommendation = "Caller is requesting sensitive bank account, card, or CVV information. Legitimate institutions never ask for this over phone.";
        } else if (outcome.isLotteryScam) {
            summary = "Lottery / Advance Fee Fraud Detected";
            recommendation = "Caller claims you won a prize/lottery. This is a common social engineering scam.";
        } else if (outcome.isOtpTheft) {
            summary = "Security Alert: OTP / PIN Interception Attempt";
            recommendation = "Never share OTP or verification codes with anyone.";
        } else if (isBot) {
            summary = "Automated Synthetic Voice / Robocall Detected";
            recommendation = "Audio biomarkers indicate synthetic AI voice synthesis. Exercise extreme caution.";
        } else if (isSpam) {
            summary = "High Spam Risk (Flagged Number Reputation)";
            recommendation = "This number has previous scam or spam reports.";
        } else if (isKnownContact) {
            summary = "Verified Trusted Contact (Natural Conversation)";
            recommendation = "No scam patterns or malicious triggers detected in conversation.";
        } else {
            summary = "Unknown Caller (No Scam Triggers Detected)";
            recommendation = "Conversation appears normal so far. Continue with standard caution.";
        }

        LiveRiskResult res = new LiveRiskResult(level, finalScore, summary, recommendation);
        res.setContextEvaluated(true);
        res.setListeningDurationSeconds(secondsElapsed);
        res.setBot(isBot);
        res.setSpam(isSpam);
        res.setTrustedContact(isKnownContact);
        res.setTranscriptExcerpt(accumulatedTranscript.toString().trim());

        for (String phrase : outcome.flaggedPhrases) {
            res.addIndicator("• " + phrase);
        }
        for (String trigger : outcome.detectedTriggers) {
            res.addFlaggedKeyword(trigger);
        }

        if (isBot) res.addIndicator("• AI Synthetic Speech Pattern Detected");
        if (isSpam) res.addIndicator("• Phone number flagged on scam databases");
        if (isKnownContact) res.addIndicator("• Caller matches a saved contact");

        return res;
    }
}