package com.realconnect.app;

import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonArray;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

public class AiIntentAnalyzer {

    public interface IntentAnalysisCallback {
        void onAnalysisCompleted(IntentResult result);
    }

    public static class IntentResult {
        public String intention;
        public LiveRiskResult.Level riskLevel;
        public int riskScore; // 0 to 100
        public String summary;
        public String recommendation;
        public List<String> threatIndicators = new ArrayList<>();
        public List<String> flaggedKeywords = new ArrayList<>();
    }

    private static final String TAG = "AiIntentAnalyzer";
    private static final OkHttpClient httpClient = new OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(20, TimeUnit.SECONDS)
            .build();
    private static final Gson gson = new Gson();

    public static void analyzeCallerIntent(String transcript, String callerPhone, String callerName, IntentAnalysisCallback callback) {
        if (transcript == null || transcript.trim().isEmpty()) {
            IntentResult emptyResult = new IntentResult();
            emptyResult.intention = "Monitoring live call audio stream • Waiting for spoken dialogue...";
            emptyResult.riskLevel = LiveRiskResult.Level.LOW;
            emptyResult.riskScore = 0;
            emptyResult.summary = "Listening to conversation in real time";
            emptyResult.recommendation = "AI Guard is actively listening. Intent will be evaluated as words are spoken.";
            emptyResult.threatIndicators.add("• Live audio stream active");
            new Handler(Looper.getMainLooper()).post(() -> callback.onAnalysisCompleted(emptyResult));
            return;
        }

        // Perform async AI intent analysis
        new Thread(() -> {
            IntentResult result = null;

            // 1. Query AI Cloud Intelligence API
            try {
                result = queryAiService(transcript, callerPhone, callerName);
            } catch (Exception e) {
                Log.w(TAG, "AI API call failed, using on-device semantic intent engine: " + e.getMessage());
            }

            // 2. Fallback to on-device semantic intent engine if network fails
            if (result == null) {
                result = evaluateSemanticIntent(transcript);
            }

            final IntentResult finalResult = result;
            new Handler(Looper.getMainLooper()).post(() -> callback.onAnalysisCompleted(finalResult));
        }).start();
    }

    private static IntentResult queryAiService(String transcript, String phone, String name) throws IOException {
        String prompt = "You are an AI Real-Time Fraud & Intent Analyzer for phone calls.\n" +
                "Caller Number: " + (phone != null ? phone : "Unknown") + "\n" +
                "Live Conversation Transcript:\n\"" + transcript + "\"\n\n" +
                "Analyze the caller's real intention. Detect phishing, lottery scams, OTP theft, CVV requests, impersonation, or normal casual conversation.\n" +
                "Respond in strict JSON with these exact keys:\n" +
                "{\n" +
                "  \"intention\": \"brief description of caller's true intent\",\n" +
                "  \"riskLevel\": \"LOW\" | \"MEDIUM\" | \"HIGH\",\n" +
                "  \"riskScore\": 0 to 100,\n" +
                "  \"summary\": \"clear explanation of detected intent\",\n" +
                "  \"threatIndicators\": [\"indicator 1\", \"indicator 2\"],\n" +
                "  \"recommendation\": \"actionable advice for user\"\n" +
                "}";

        JsonObject jsonBody = new JsonObject();
        JsonArray contents = new JsonArray();
        JsonObject contentObj = new JsonObject();
        JsonArray parts = new JsonArray();
        JsonObject partObj = new JsonObject();
        partObj.addProperty("text", prompt);
        parts.add(partObj);
        contentObj.add("parts", parts);
        contents.add(contentObj);
        jsonBody.add("contents", contents);

        // Send to AI endpoint
        RequestBody body = RequestBody.create(
                MediaType.parse("application/json; charset=utf-8"),
                jsonBody.toString()
        );

        Request request = new Request.Builder()
                .url("https://ai-detection-sys.onrender.com/voice-analysis")
                .post(body)
                .build();

        try (Response response = httpClient.newCall(request).execute()) {
            if (response.isSuccessful() && response.body() != null) {
                String responseStr = response.body().string();
                return parseAiJsonResponse(responseStr, transcript);
            }
        }

        return evaluateSemanticIntent(transcript);
    }

    private static IntentResult parseAiJsonResponse(String json, String originalTranscript) {
        try {
            JsonObject root = gson.fromJson(json, JsonObject.class);
            IntentResult r = new IntentResult();
            r.intention = root.has("intention") ? root.get("intention").getAsString() : "Live Conversation Analysis";
            String levelStr = root.has("riskLevel") ? root.get("riskLevel").getAsString() : "LOW";
            r.riskLevel = levelStr.equalsIgnoreCase("HIGH") ? LiveRiskResult.Level.HIGH :
                    (levelStr.equalsIgnoreCase("MEDIUM") ? LiveRiskResult.Level.MEDIUM : LiveRiskResult.Level.LOW);
            r.riskScore = root.has("riskScore") ? root.get("riskScore").getAsInt() : 10;
            r.summary = root.has("summary") ? root.get("summary").getAsString() : r.intention;
            r.recommendation = root.has("recommendation") ? root.get("recommendation").getAsString() : "Exercise standard caution.";

            if (root.has("threatIndicators")) {
                JsonArray arr = root.getAsJsonArray("threatIndicators");
                for (int i = 0; i < arr.size(); i++) {
                    r.threatIndicators.add("• " + arr.get(i).getAsString());
                }
            }
            return r;
        } catch (Exception e) {
            return evaluateSemanticIntent(originalTranscript);
        }
    }

    public static IntentResult evaluateSemanticIntent(String transcript) {
        IntentResult result = new IntentResult();
        String text = transcript.toLowerCase();

        boolean asksCvv = text.contains("cvv") || text.contains("security code");
        boolean asksCard = text.contains("credit card") || text.contains("debit card") || text.contains("card number");
        boolean asksBank = text.contains("bank account") || text.contains("account number") || text.contains("atm pin");
        boolean claimsLottery = text.contains("lottery") || text.contains("5 crore") || text.contains("prize") || text.contains("lucky draw") || text.contains("won");
        boolean asksOtp = text.contains("otp") || text.contains("one time password") || text.contains("verification code") || text.contains("sms code");
        boolean impersonates = text.contains("ajio") || text.contains("amazon") || text.contains("police") || text.contains("customs") || text.contains("bank");
        boolean createsUrgency = text.contains("right now") || text.contains("immediately") || text.contains("deposit") || text.contains("urgent");

        if ((asksCvv || asksCard || asksBank) && claimsLottery) {
            result.riskLevel = LiveRiskResult.Level.HIGH;
            result.riskScore = 98;
            result.intention = "🚨 Financial Theft: Soliciting Card CVV and Bank Info under false lottery pretext";
            result.summary = "Caller is actively attempting to extract your credit card 3-digit CVV and banking details by promising an unverified prize.";
            result.recommendation = "DO NOT give your CVV, card numbers, or bank account details. Legitimate rewards never ask for your card security code. Hang up immediately.";
            result.threatIndicators.add("• Request for sensitive 3-digit CVV number");
            result.threatIndicators.add("• Fake 5 Crore lottery / prize hook");
            if (impersonates) result.threatIndicators.add("• Corporate brand impersonation (AJIO / Bank)");
        } else if (asksOtp) {
            result.riskLevel = LiveRiskResult.Level.HIGH;
            result.riskScore = 95;
            result.intention = "🚨 Account Hijacking: Attempting to intercept OTP / Security Code";
            result.summary = "Caller is pressuring you to reveal a secret verification code to bypass security.";
            result.recommendation = "NEVER share your OTP with anyone, even official support agents. Hang up immediately.";
            result.threatIndicators.add("• Direct request for one-time verification password (OTP)");
        } else if (asksCvv || asksCard || asksBank) {
            result.riskLevel = LiveRiskResult.Level.HIGH;
            result.riskScore = 90;
            result.intention = "⚠️ Financial Harvesting: Soliciting private payment or bank account credentials";
            result.summary = "Caller is requesting sensitive banking or card payment information over the phone.";
            result.recommendation = "Do not share credit/debit card numbers over unverified phone calls.";
            result.threatIndicators.add("• Solicitation of banking / card information");
        } else if (claimsLottery || (impersonates && createsUrgency)) {
            result.riskLevel = LiveRiskResult.Level.HIGH;
            result.riskScore = 85;
            result.intention = "⚠️ Social Engineering: Manipulating trust using false authority and artificial urgency";
            result.summary = "Caller is using psychological pressure and impersonation to make you comply with instructions.";
            result.recommendation = "Do not transfer money or follow caller instructions. Verify independently.";
            result.threatIndicators.add("• Psychological urgency & false authority claim");
        } else {
            // Normal conversation
            result.riskLevel = LiveRiskResult.Level.LOW;
            result.riskScore = 2;
            result.intention = "💬 Casual / Normal Conversation";
            result.summary = "Natural human conversation with no malicious or fraudulent intent detected.";
            result.recommendation = "Safe call. Continue your conversation normally.";
            result.threatIndicators.add("• Natural conversational speech flow");
            result.threatIndicators.add("• Zero financial, OTP, or phishing triggers detected");
        }

        return result;
    }
}