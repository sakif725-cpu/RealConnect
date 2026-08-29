package com.realconnect.app;

import android.os.Handler;
import android.os.Looper;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

public class AiMessageThreatAnalyzer {

    private static final String TAG = "AiMessageThreatAnalyzer";

    public enum ThreatLevel {
        SAFE(0, "#10B981", "#ECFDF5", "#A7F3D0"),
        MEDIUM(55, "#D97706", "#FFFBEB", "#FDE68A"),
        HIGH(85, "#EA580C", "#FFF7ED", "#FED7AA"),
        CRITICAL(96, "#DC2626", "#FEF2F2", "#FECACA");

        public final int defaultScore;
        public final String colorHex;
        public final String bgHex;
        public final String strokeHex;

        ThreatLevel(int defaultScore, String colorHex, String bgHex, String strokeHex) {
            this.defaultScore = defaultScore;
            this.colorHex = colorHex;
            this.bgHex = bgHex;
            this.strokeHex = strokeHex;
        }
    }

    public static class ThreatReport {
        public boolean isSuspicious;
        public ThreatLevel level = ThreatLevel.SAFE;
        public int riskScore; // 0 to 100
        public String category;
        public String shortBadge;
        public String explanation;
        public String recommendation;
        public List<String> indicators = new ArrayList<>();
        public boolean userDismissed = false;
    }

    public interface AnalysisCallback {
        void onReportReady(ThreatReport report);
    }

    private static final Map<String, ThreatReport> reportCache = new HashMap<>();
    private static final Map<String, Boolean> dismissedCache = new HashMap<>();

    // Common Phishing & Threat Regex Patterns
    private static final Pattern URL_PATTERN = Pattern.compile(
            "(https?://|www\\.)[a-zA-Z0-9.-]+\\.[a-zA-Z]{2,}(/[a-zA-Z0-9._~:/?#\\[\\]@!$&'()*+,;=-]*)?",
            Pattern.CASE_INSENSITIVE
    );

    private static final Pattern SHORTENER_PATTERN = Pattern.compile(
            "bit\\.ly|tinyurl\\.com|t\\.co|is\\.gd|cutt\\.ly|rb\\.gy|shorturl\\.at|ow\\.ly",
            Pattern.CASE_INSENSITIVE
    );

    private static final Pattern OTP_PATTERN = Pattern.compile(
            "\\b(otp|one time password|verification code|security code|2fa|login code|auth code|6-digit|4-digit)\\b",
            Pattern.CASE_INSENSITIVE
    );

    private static final Pattern CVV_CARD_PATTERN = Pattern.compile(
            "\\b(cvv|cvc|card number|credit card|debit card|atm pin|expir(y|ation) date)\\b",
            Pattern.CASE_INSENSITIVE
    );

    private static final Pattern LOTTERY_PRIZE_PATTERN = Pattern.compile(
            "\\b(congratulations|won|lottery|prize|lucky draw|crore|lakhs?|claim (your )?reward|jackpot|free gift)\\b",
            Pattern.CASE_INSENSITIVE
    );

    private static final Pattern URGENCY_PRESSURE_PATTERN = Pattern.compile(
            "\\b(immediately|urgent|within (1|2|24) (hour|hr|hours)|account (suspended|blocked|terminated)|legal action|police warrant|arrest)\\b",
            Pattern.CASE_INSENSITIVE
    );

    private static final Pattern MONEY_TRANSFER_PATTERN = Pattern.compile(
            "\\b(deposit|transfer money|send (cash|funds|crypto|bitcoin)|processing fee|advance payment|wire money)\\b",
            Pattern.CASE_INSENSITIVE
    );

    private static final Pattern IMPERSONATION_PATTERN = Pattern.compile(
            "\\b(bank manager|customer care|tech support|police department|customs officer|income tax|ajio support|amazon helpdesk)\\b",
            Pattern.CASE_INSENSITIVE
    );

    /**
     * Analyzes a message text for threats using local real-time heuristics, and enriches via AI API if applicable.
     */
    public static ThreatReport analyzeSync(String messageId, String text) {
        if (text == null || text.trim().isEmpty()) {
            ThreatReport safe = new ThreatReport();
            safe.isSuspicious = false;
            safe.level = ThreatLevel.SAFE;
            safe.riskScore = 0;
            safe.category = "Safe Message";
            safe.shortBadge = "Safe";
            safe.explanation = "No malicious patterns detected.";
            safe.recommendation = "Message appears normal.";
            return safe;
        }

        if (messageId != null && Boolean.TRUE.equals(dismissedCache.get(messageId))) {
            ThreatReport dismissed = new ThreatReport();
            dismissed.isSuspicious = false;
            dismissed.userDismissed = true;
            dismissed.level = ThreatLevel.SAFE;
            dismissed.category = "Dismissed";
            return dismissed;
        }

        if (messageId != null && reportCache.containsKey(messageId)) {
            return reportCache.get(messageId);
        }

        ThreatReport report = performHeuristicAnalysis(text);
        if (messageId != null) {
            reportCache.put(messageId, report);
        }
        return report;
    }

    /**
     * Async analysis with AI service fallback enrichment
     */
    public static void analyzeAsync(String messageId, String text, AnalysisCallback callback) {
        ThreatReport localReport = analyzeSync(messageId, text);

        if (!localReport.isSuspicious) {
            callback.onReportReady(localReport);
            return;
        }

        // Deep Cloud AI semantic verification
        AiService.analyzeText(text, aiResponse -> {
            if (aiResponse != null && aiResponse.isScammer) {
                if (aiResponse.riskScore > 0) {
                    localReport.riskScore = Math.max(localReport.riskScore, aiResponse.riskScore);
                }
                if (aiResponse.summary != null && !aiResponse.summary.isEmpty()) {
                    localReport.explanation = aiResponse.summary;
                }
                if (aiResponse.threatIndicators != null && !aiResponse.threatIndicators.isEmpty()) {
                    for (String ind : aiResponse.threatIndicators) {
                        if (!localReport.indicators.contains("• " + ind) && !localReport.indicators.contains(ind)) {
                            localReport.indicators.add("• " + ind);
                        }
                    }
                }
                if (aiResponse.recommendation != null && !aiResponse.recommendation.isEmpty()) {
                    localReport.recommendation = aiResponse.recommendation;
                }
            }

            if (messageId != null) {
                reportCache.put(messageId, localReport);
            }
            new Handler(Looper.getMainLooper()).post(() -> callback.onReportReady(localReport));
        });
    }

    public static void markAsDismissed(String messageId) {
        if (messageId == null) return;
        dismissedCache.put(messageId, true);
        if (reportCache.containsKey(messageId)) {
            reportCache.get(messageId).userDismissed = true;
            reportCache.get(messageId).isSuspicious = false;
        }
    }

    public static boolean isDismissed(String messageId) {
        return messageId != null && Boolean.TRUE.equals(dismissedCache.get(messageId));
    }

    private static ThreatReport performHeuristicAnalysis(String rawText) {
        ThreatReport report = new ThreatReport();
        String text = rawText.toLowerCase();

        boolean hasUrl = URL_PATTERN.matcher(rawText).find();
        boolean hasShortener = SHORTENER_PATTERN.matcher(rawText).find();
        boolean asksOtp = OTP_PATTERN.matcher(text).find();
        boolean asksCvv = CVV_CARD_PATTERN.matcher(text).find();
        boolean hasLottery = LOTTERY_PRIZE_PATTERN.matcher(text).find();
        boolean hasUrgency = URGENCY_PRESSURE_PATTERN.matcher(text).find();
        boolean asksMoney = MONEY_TRANSFER_PATTERN.matcher(text).find();
        boolean impersonates = IMPERSONATION_PATTERN.matcher(text).find();

        int score = 0;
        List<String> redFlags = new ArrayList<>();

        // 1. Phishing URLs & Link Shorteners
        if (hasUrl) {
            score += 35;
            redFlags.add("• Contains unverified external web link");
            if (hasShortener) {
                score += 40;
                redFlags.add("• Uses masked/shortened URL commonly employed in phishing");
            }
            if (hasUrgency || asksOtp || asksCvv || impersonates) {
                score += 30;
                redFlags.add("• Suspicious URL combined with urgent credential solicitation");
            }
        }

        // 2. OTP & Security Code Interception
        if (asksOtp) {
            score += 55;
            redFlags.add("• Solicits one-time verification password (OTP) or security code");
            if (hasUrgency || impersonates) {
                score += 25;
                redFlags.add("• High-pressure attempt to intercept 2FA login credentials");
            }
        }

        // 3. Card Details / CVV / PIN Theft
        if (asksCvv) {
            score += 60;
            redFlags.add("• Requests private card numbers, CVV security code, or ATM PIN");
        }

        // 4. Fake Lottery / Awards with Advance Fee
        if (hasLottery) {
            score += 35;
            redFlags.add("• Promises unrealistic lottery winnings, cash rewards, or prizes");
            if (asksMoney || hasUrgency) {
                score += 40;
                redFlags.add("• Demands processing fees or deposits to release fake prize money");
            }
        }

        // 5. Impersonation & Extortion / Account Suspensions
        if (hasUrgency && impersonates) {
            score += 45;
            redFlags.add("• Impersonates an official organization using artificial urgency & threats");
        } else if (hasUrgency && (asksMoney || asksOtp)) {
            score += 35;
            redFlags.add("• Uses urgent deadlines to pressure immediate compliance");
        }

        // Categorization & Severity Assignment
        if (score >= 45) {
            report.isSuspicious = true;
            report.indicators = redFlags;

            if (score >= 80) {
                report.level = ThreatLevel.CRITICAL;
                report.riskScore = Math.min(99, score);
            } else if (score >= 60) {
                report.level = ThreatLevel.HIGH;
                report.riskScore = Math.min(84, score);
            } else {
                report.level = ThreatLevel.MEDIUM;
                report.riskScore = Math.min(65, score);
            }

            if (asksOtp) {
                report.category = "Credential & OTP Harvesting";
                report.shortBadge = "⚠️ Suspicious: Potential OTP Theft";
                report.explanation = "This message is attempting to solicit your one-time verification code (OTP). Sharing codes allows attackers to hijack your private accounts.";
                report.recommendation = "NEVER share your OTP, login code, or password with anyone, even if they claim to be support.";
            } else if (hasUrl) {
                report.category = "Phishing URL / Malicious Link";
                report.shortBadge = "🚨 AI Alert: Phishing Link Detected";
                report.explanation = "This message contains a suspicious or masked URL that may lead to a credential-harvesting phishing page or malware download.";
                report.recommendation = "Do NOT click the link. Verify the sender's identity through official channels.";
            } else if (asksCvv) {
                report.category = "Financial Card / CVV Theft";
                report.shortBadge = "🚨 High Risk: Banking Data Harvesting";
                report.explanation = "The sender is asking for your credit/debit card numbers, CVV security code, or banking credentials.";
                report.recommendation = "Never send credit card CVVs or banking PINs over private messages. Legitimate entities never request them.";
            } else if (hasLottery) {
                report.category = "Fake Prize / Advance Fee Scam";
                report.shortBadge = "⚠️ Suspicious: Lottery / Prize Scam";
                report.explanation = "This message offers unverified prize money or lucky draw rewards to solicit personal information or advance deposits.";
                report.recommendation = "Do not reply or send money to claim prizes. Delete this message.";
            } else {
                report.category = "Social Engineering & Extortion";
                report.shortBadge = "⚠️ Caution: High-Pressure Request";
                report.explanation = "This message employs high-pressure psychological urgency, impersonation, or threats to force compliance.";
                report.recommendation = "Exercise caution. Do not follow urgent instructions without independent verification.";
            }
        } else {
            report.isSuspicious = false;
            report.level = ThreatLevel.SAFE;
            report.riskScore = Math.max(2, score);
            report.category = "Normal Message";
            report.shortBadge = "Safe";
            report.explanation = "No malicious patterns detected.";
            report.recommendation = "Safe conversation.";
        }

        return report;
    }
}

