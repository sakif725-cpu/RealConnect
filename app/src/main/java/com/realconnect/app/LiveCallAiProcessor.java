package com.realconnect.app;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import java.io.ByteArrayOutputStream;

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
    private boolean isSyntheticVoiceDetected = false;
    private boolean isSpamFromRender = false;
    private LiveRiskResult currentRiskResult;

    private final ByteArrayOutputStream pcmBuffer = new ByteArrayOutputStream();
    private long lastAudioSendTime = 0;
    private boolean isSendingAudio = false;
    private String accumulatedTranscript = "";
    private AiApiService.VoiceAnalysisResponse latestServerAnalysis;

    private android.speech.SpeechRecognizer speechRecognizer;
    private android.content.Intent speechIntent;
    private boolean isSpeechRecognizerActive = false;
    private String lastSentTranscript = "";

    public LiveCallAiProcessor(Context context, String phone, String name, boolean isPreFlaggedSpam, AiScanListener listener) {
        this.context = context;
        this.phone = phone;
        this.name = name;
        this.isPreFlaggedSpam = isPreFlaggedSpam;
        this.listener = listener;
        this.isSpamFromRender = isPreFlaggedSpam;
    }

    public void start() {
        if (isRunning) return;
        isRunning = true;
        secondsElapsed = 0;
        isSyntheticVoiceDetected = false;
        accumulatedTranscript = "";
        lastSentTranscript = "";

        Log.d(TAG, "Connecting to AI backend: " + AiService.BASE_URL);
        queryRenderSpamCheck();
        initOnDeviceSpeechRecognizer();
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
            isSpeechRecognizerActive = false;
        }
        synchronized (pcmBuffer) {
            pcmBuffer.reset();
        }
    }

    private void initOnDeviceSpeechRecognizer() {
        handler.post(() -> {
            if (!android.speech.SpeechRecognizer.isRecognitionAvailable(context)) {
                Log.w(TAG, "On-device speech recognition not available. Falling back to audio stream mode.");
                isSpeechRecognizerActive = false;
                return;
            }

            try {
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S && 
                    android.speech.SpeechRecognizer.isOnDeviceRecognitionAvailable(context)) {
                    speechRecognizer = android.speech.SpeechRecognizer.createOnDeviceSpeechRecognizer(context);
                } else {
                    speechRecognizer = android.speech.SpeechRecognizer.createSpeechRecognizer(context);
                }

                speechIntent = new android.content.Intent(android.speech.RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
                speechIntent.putExtra(android.speech.RecognizerIntent.EXTRA_LANGUAGE_MODEL, 
                        android.speech.RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
                speechIntent.putExtra(android.speech.RecognizerIntent.EXTRA_PARTIAL_RESULTS, true);
                speechIntent.putExtra(android.speech.RecognizerIntent.EXTRA_MAX_RESULTS, 1);

                speechRecognizer.setRecognitionListener(new android.speech.RecognitionListener() {
                    @Override public void onReadyForSpeech(android.os.Bundle params) {}
                    @Override public void onBeginningOfSpeech() {}
                    @Override public void onRmsChanged(float rmsdB) {}
                    @Override public void onBufferReceived(byte[] buffer) {}
                    @Override public void onEndOfSpeech() { restartListening(); }
                    @Override public void onError(int error) { restartListening(); }
                    @Override public void onEvent(int eventType, android.os.Bundle params) {}

                    @Override
                    public void onResults(android.os.Bundle results) {
                        processSpeechResults(results);
                        restartListening();
                    }

                    @Override
                    public void onPartialResults(android.os.Bundle partialResults) {
                        processSpeechResults(partialResults);
                    }
                });

                startListening();
            } catch (Exception e) {
                Log.e(TAG, "Error initializing on-device speech recognizer", e);
                isSpeechRecognizerActive = false;
            }
        });
    }

    private void startListening() {
        if (speechRecognizer != null && isRunning && speechIntent != null) {
            try {
                speechRecognizer.startListening(speechIntent);
                isSpeechRecognizerActive = true;
                Log.d(TAG, "🎙️ Local On-Device SpeechRecognizer started (Ultra-low bandwidth mode)");
            } catch (Exception e) {
                Log.w(TAG, "Failed to start speech listening", e);
            }
        }
    }

    private void restartListening() {
        if (!isRunning) return;
        handler.postDelayed(() -> {
            if (isRunning && speechRecognizer != null) {
                startListening();
            }
        }, 300);
    }

    private void processSpeechResults(android.os.Bundle bundle) {
        if (bundle == null) return;
        java.util.ArrayList<String> matches = bundle.getStringArrayList(android.speech.SpeechRecognizer.RESULTS_RECOGNITION);
        if (matches != null && !matches.isEmpty()) {
            String text = matches.get(0).trim();
            if (!text.isEmpty() && !text.equalsIgnoreCase(lastSentTranscript)) {
                lastSentTranscript = text;
                accumulatedTranscript = text;
                Log.d(TAG, "⚡ Spoken text transcribed locally (" + text.length() + " chars): \"" + text + "\" -> Sending ~50 bytes to AI Server");

                // Send pure lightweight text to AI backend
                AiService.analyzeText(text, response -> {
                    if (response != null) {
                        latestServerAnalysis = response;
                        Log.i(TAG, "🛡️ AI Server Threat Result: Risk=" + response.riskLevel + " (" + response.riskScore + "%) | Scam=" + response.isScammer);
                    }
                    evaluateLiveContext();
                });
            }
        }
    }

    private long lastTextSentTime = 0;

    public void onAudioSamplesCaptured(byte[] data, int sampleRate, int channels) {
        if (!isRunning || data == null || data.length == 0) return;

        long now = System.currentTimeMillis();
        // If local speech recognizer sent text recently, skip sending heavy audio
        if (isSpeechRecognizerActive && (now - lastTextSentTime < 4000)) {
            return;
        }

        synchronized (pcmBuffer) {
            pcmBuffer.write(data, 0, data.length);

            // Once we have 2.5 seconds of audio, dispatch audio to server
            int bytesPerSecond = sampleRate * channels * 2;
            int thresholdBytes = (int) (bytesPerSecond * 2.5);

            if (pcmBuffer.size() >= thresholdBytes && (now - lastAudioSendTime >= 2500) && !isSendingAudio) {
                byte[] rawPcm = pcmBuffer.toByteArray();
                pcmBuffer.reset();
                lastAudioSendTime = now;
                Log.d(TAG, "🎤 Audio stream (" + rawPcm.length + " bytes) -> Sending to AI server for transcription...");
                dispatchAudioSnippetToServer(rawPcm, sampleRate, channels);
            }
        }
    }

    private void dispatchAudioSnippetToServer(byte[] pcmData, int sampleRate, int channels) {
        isSendingAudio = true;
        byte[] wavBytes = addWavHeader(pcmData, sampleRate, channels);

        AiService.analyzeLiveVoice(wavBytes, response -> {
            isSendingAudio = false;
            if (response != null) {
                latestServerAnalysis = response;
                Log.i(TAG, "🛡️ Render AI Response: Risk=" + response.riskLevel + " (" + response.riskScore + "%) | Intent=" + response.intention + " | Transcript=\"" + response.transcript + "\"");
                if (response.transcript != null && !response.transcript.trim().isEmpty()) {
                    accumulatedTranscript = response.transcript.trim();
                }
            } else {
                Log.w(TAG, "⚠️ Render AI returned null response or connection error");
            }
            evaluateLiveContext();
        });
    }

    private byte[] addWavHeader(byte[] pcmData, int sampleRate, int channels) {
        int totalAudioLen = pcmData.length;
        int totalDataLen = totalAudioLen + 36;
        int byteRate = sampleRate * channels * 2;

        byte[] header = new byte[44];
        header[0] = 'R'; header[1] = 'I'; header[2] = 'F'; header[3] = 'F';
        header[4] = (byte) (totalDataLen & 0xff);
        header[5] = (byte) ((totalDataLen >> 8) & 0xff);
        header[6] = (byte) ((totalDataLen >> 16) & 0xff);
        header[7] = (byte) ((totalDataLen >> 24) & 0xff);
        header[8] = 'W'; header[9] = 'A'; header[10] = 'V'; header[11] = 'E';
        header[12] = 'f'; header[13] = 'm'; header[14] = 't'; header[15] = ' ';
        header[16] = 16; header[17] = 0; header[18] = 0; header[19] = 0; // Subchunk1Size = 16
        header[20] = 1; header[21] = 0; // AudioFormat = 1 (PCM)
        header[22] = (byte) channels; header[23] = 0;
        header[24] = (byte) (sampleRate & 0xff);
        header[25] = (byte) ((sampleRate >> 8) & 0xff);
        header[26] = (byte) ((sampleRate >> 16) & 0xff);
        header[27] = (byte) ((sampleRate >> 24) & 0xff);
        header[28] = (byte) (byteRate & 0xff);
        header[29] = (byte) ((byteRate >> 8) & 0xff);
        header[30] = (byte) ((byteRate >> 16) & 0xff);
        header[31] = (byte) ((byteRate >> 24) & 0xff);
        header[32] = (byte) (channels * 2); header[33] = 0; // BlockAlign
        header[34] = 16; header[35] = 0; // BitsPerSample = 16
        header[36] = 'd'; header[37] = 'a'; header[38] = 't'; header[39] = 'a';
        header[40] = (byte) (totalAudioLen & 0xff);
        header[41] = (byte) ((totalAudioLen >> 8) & 0xff);
        header[42] = (byte) ((totalAudioLen >> 16) & 0xff);
        header[43] = (byte) ((totalAudioLen >> 24) & 0xff);

        byte[] wav = new byte[header.length + pcmData.length];
        System.arraycopy(header, 0, wav, 0, header.length);
        System.arraycopy(pcmData, 0, wav, header.length, pcmData.length);
        return wav;
    }

    public String getCapturedTranscript() {
        if (accumulatedTranscript != null && !accumulatedTranscript.trim().isEmpty()) {
            return accumulatedTranscript.trim();
        }
        if (latestServerAnalysis != null && latestServerAnalysis.transcript != null && !latestServerAnalysis.transcript.trim().isEmpty()) {
            return latestServerAnalysis.transcript.trim();
        }
        return "🎙️ WebRTC In-Call Live Audio Monitoring Active";
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

    private void queryRenderSpamCheck() {
        AiService.checkSpam(phone, isSpam -> {
            Log.d(TAG, "Render GET /spam-check response for " + phone + ": isSpam=" + isSpam);
            isSpamFromRender = isPreFlaggedSpam || isSpam;
            evaluateLiveContext();
        });
    }

    private void startTicker() {
        tickerRunnable = new Runnable() {
            @Override
            public void run() {
                if (!isRunning) return;

                secondsElapsed++;

                if (listener != null) {
                    String statusText;
                    if (currentRiskResult != null && currentRiskResult.getLevel() == LiveRiskResult.Level.HIGH) {
                        statusText = "AI: HIGH RISK (" + currentRiskResult.getRiskScore() + "%)";
                    } else if (currentRiskResult != null && currentRiskResult.getLevel() == LiveRiskResult.Level.MEDIUM) {
                        statusText = "AI: MODERATE (" + currentRiskResult.getRiskScore() + "%) • " + secondsElapsed + "s";
                    } else if (secondsElapsed < 5) {
                        statusText = "AI: MONITORING • " + secondsElapsed + "s";
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

    private void evaluateLiveContext() {
        if (latestServerAnalysis != null && (latestServerAnalysis.isScammer || latestServerAnalysis.riskScore > 35 || latestServerAnalysis.isBot)) {
            LiveRiskResult.Level level;
            if ("HIGH".equalsIgnoreCase(latestServerAnalysis.riskLevel) || "CRITICAL".equalsIgnoreCase(latestServerAnalysis.riskLevel)) {
                level = LiveRiskResult.Level.HIGH;
            } else if ("MEDIUM".equalsIgnoreCase(latestServerAnalysis.riskLevel)) {
                level = LiveRiskResult.Level.MEDIUM;
            } else {
                level = LiveRiskResult.Level.LOW;
            }

            int score = latestServerAnalysis.riskScore > 0 ? latestServerAnalysis.riskScore : (int) (latestServerAnalysis.confidence * 100);
            String summary = latestServerAnalysis.summary != null && !latestServerAnalysis.summary.isEmpty()
                    ? latestServerAnalysis.summary : (latestServerAnalysis.isBot ? "Synthetic bot voice detected" : "Suspicious call behavior");
            String recommendation = latestServerAnalysis.recommendation != null && !latestServerAnalysis.recommendation.isEmpty()
                    ? latestServerAnalysis.recommendation : "Exercise caution.";

            LiveRiskResult res = new LiveRiskResult(level, score, summary, recommendation);
            res.setContextEvaluated(true);
            res.setListeningDurationSeconds(secondsElapsed);
            res.setBot(latestServerAnalysis.isBot || isSyntheticVoiceDetected);
            res.setSpam(isSpamFromRender);
            res.setTranscriptExcerpt(getCapturedTranscript());
            res.setCallerIntent(latestServerAnalysis.intention != null ? latestServerAnalysis.intention : "Live Call Analysis");

            if (latestServerAnalysis.threatIndicators != null) {
                for (String ind : latestServerAnalysis.threatIndicators) {
                    res.addIndicator("• " + ind);
                }
            }
            if (latestServerAnalysis.scamType != null && !latestServerAnalysis.scamType.isEmpty()) {
                res.addFlaggedKeyword(latestServerAnalysis.scamType);
            }

            currentRiskResult = res;
            if (listener != null) {
                listener.onRiskUpdated(res);
            }
            return;
        }

        AiIntentAnalyzer.analyzeCallerIntent(accumulatedTranscript, phone, name, intentResult -> {
            LiveRiskResult res = new LiveRiskResult(
                    intentResult.riskLevel,
                    intentResult.riskScore,
                    intentResult.summary,
                    intentResult.recommendation
            );
            res.setContextEvaluated(true);
            res.setListeningDurationSeconds(secondsElapsed);
            res.setBot(isSyntheticVoiceDetected);
            res.setSpam(isSpamFromRender);
            res.setTranscriptExcerpt(getCapturedTranscript());
            res.setCallerIntent(intentResult.intention);

            for (String ind : intentResult.threatIndicators) {
                res.addIndicator(ind);
            }
            if (isSyntheticVoiceDetected) {
                res.addIndicator("• Render AI Voice Analysis: Synthetic bot voice detected");
            }
            if (isSpamFromRender) {
                res.addIndicator("• Render Spam Check: Number flagged on global databases");
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