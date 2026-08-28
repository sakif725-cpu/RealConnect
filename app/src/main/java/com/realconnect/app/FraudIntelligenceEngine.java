package com.realconnect.app;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class FraudIntelligenceEngine {

    public static class FraudAssessment {
        public int fraudScore; // 0 to 100
        public LiveRiskResult.Level riskLevel;
        public String threatCategory;
        public String detailedSummary;
        public String actionRecommendation;
        public final List<String> detectedIndicators = new ArrayList<>();
        public final List<String> flaggedKeywords = new ArrayList<>();
    }

    // 1. Critical Financial & Card Harvesting (Immediate Red Flag)
    private static final String[] FINANCIAL_KEYWORDS = {
            "cvv", "cvv number", "credit card", "debit card", "card number",
            "bank account", "account number", "atm pin", "pin number", "expiry date",
            "bank details", "net banking", "transfer money", "deposit money", "deposit it"
    };

    // 2. Lottery & Advance-Fee Fraud
    private static final String[] LOTTERY_KEYWORDS = {
            "won a lottery", "lottery", "5 crore", "5 cr", "1 crore", "prize",
            "lucky draw", "won cash", "congratulations you have won", "congratulations",
            "claim reward", "jackpot", "reward points", "cashback offer"
    };

    // 3. OTP & Security Credential Theft
    private static final String[] OTP_KEYWORDS = {
            "otp", "one time password", "verification code", "security code",
            "share the code", "sms code", "6 digit", "4 digit", "tell me the otp"
    };

    // 4. Brand & Authority Impersonation
    private static final String[] IMPERSONATION_KEYWORDS = {
            "calling from ajio", "calling from amazon", "calling from flipkart",
            "calling from bank", "customer care", "customer service", "police department",
            "customs department", "courier parcel", "illegal parcel", "arrest warrant",
            "telecom department", "sim block", "kyc update", "account suspended"
    };

    // 5. Psychological Urgency & Pressure Triggers
    private static final String[] URGENCY_KEYWORDS = {
            "right now", "immediately", "deposit right now", "urgent", "within 10 minutes",
            "today only", "last chance", "before account blocked", "confirm now"
    };

    public static FraudAssessment evaluate(String dialogueText, boolean isKnownContact, boolean isSpamNumber, boolean isBotVoice) {
        FraudAssessment assessment = new FraudAssessment();
        int score = 0;

        boolean hasFinancial = false;
        boolean hasLottery = false;
        boolean hasOtp = false;
        boolean hasImpersonation = false;
        boolean hasUrgency = false;

        String text = (dialogueText != null) ? dialogueText.toLowerCase(Locale.ROOT).trim() : "";

        // Check Financial triggers
        for (String kw : FINANCIAL_KEYWORDS) {
            if (text.contains(kw)) {
                hasFinancial = true;
                score += 40;
                assessment.flaggedKeywords.add(kw);
                assessment.detectedIndicators.add("• Soliciting Sensitive Banking Info: \"" + kw + "\"");
                break;
            }
        }

        // Check Lottery triggers
        for (String kw : LOTTERY_KEYWORDS) {
            if (text.contains(kw)) {
                hasLottery = true;
                score += 35;
                assessment.flaggedKeywords.add(kw);
                assessment.detectedIndicators.add("• Fake Lottery / Advance-Fee Hook: \"" + kw + "\"");
                break;
            }
        }

        // Check OTP triggers
        for (String kw : OTP_KEYWORDS) {
            if (text.contains(kw)) {
                hasOtp = true;
                score += 45;
                assessment.flaggedKeywords.add(kw);
                assessment.detectedIndicators.add("• Credential / OTP Interception Attempt: \"" + kw + "\"");
                break;
            }
        }

        // Check Impersonation triggers
        for (String kw : IMPERSONATION_KEYWORDS) {
            if (text.contains(kw)) {
                hasImpersonation = true;
                score += 30;
                assessment.flaggedKeywords.add(kw);
                assessment.detectedIndicators.add("• Corporate / Authority Impersonation: \"" + kw + "\"");
                break;
            }
        }

        // Check Urgency triggers
        for (String kw : URGENCY_KEYWORDS) {
            if (text.contains(kw)) {
                hasUrgency = true;
                score += 20;
                assessment.flaggedKeywords.add(kw);
                assessment.detectedIndicators.add("• High-Pressure Coercion / Urgency: \"" + kw + "\"");
                break;
            }
        }

        if (isBotVoice) {
            score += 40;
            assessment.detectedIndicators.add("• Synthetic Voice Signature Detected (Robocall)");
        }

        if (isSpamNumber) {
            score += 35;
            assessment.detectedIndicators.add("• Incoming phone number has active spam/fraud reports");
        }

        // Classify Risk Level & Generate Targeted Recommendations
        if (hasFinancial && hasLottery) {
            assessment.riskLevel = LiveRiskResult.Level.HIGH;
            assessment.fraudScore = Math.min(score, 99);
            assessment.threatCategory = "CRITICAL: LOTTERY PRIZE & CVV FRAUD";
            assessment.detailedSummary = "High-severity financial scam: Caller is claiming a fake lottery prize to extract your bank account number and 3-digit card CVV.";
            assessment.actionRecommendation = "DO NOT give your CVV, card numbers, or bank details. Legitimate rewards NEVER require your card security code. Hang up immediately.";
        } else if (hasOtp) {
            assessment.riskLevel = LiveRiskResult.Level.HIGH;
            assessment.fraudScore = Math.min(score, 99);
            assessment.threatCategory = "CRITICAL: OTP & CREDENTIAL THEFT";
            assessment.detailedSummary = "Unauthorized account takeover attempt: Caller is actively requesting an OTP or authentication PIN.";
            assessment.actionRecommendation = "NEVER share your OTP with anyone, even if they claim to be official support. Hang up and report the number.";
        } else if (hasFinancial) {
            assessment.riskLevel = LiveRiskResult.Level.HIGH;
            assessment.fraudScore = Math.min(score, 99);
            assessment.threatCategory = "HIGH RISK: FINANCIAL DATA HARVESTING";
            assessment.detailedSummary = "Caller is soliciting private banking, debit/credit card, or PIN credentials.";
            assessment.actionRecommendation = "Banks and authentic companies will never ask for your card details or PIN over a phone call.";
        } else if (hasLottery || (hasImpersonation && hasUrgency)) {
            assessment.riskLevel = LiveRiskResult.Level.HIGH;
            assessment.fraudScore = Math.min(score, 99);
            assessment.threatCategory = "HIGH RISK: SOCIAL ENGINEERING / PHISHING";
            assessment.detailedSummary = "Caller is using false authority claims and artificial urgency to manipulate you into taking action.";
            assessment.actionRecommendation = "Verify the organization through their official website. Do not transfer funds or follow caller instructions.";
        } else if (isBotVoice || isSpamNumber) {
            assessment.riskLevel = LiveRiskResult.Level.HIGH;
            assessment.fraudScore = Math.min(score, 99);
            assessment.threatCategory = isBotVoice ? "HIGH RISK: SYNTHETIC AI ROBOCALL" : "HIGH RISK: SPAM / TELEMARKETING";
            assessment.detailedSummary = "Suspicious caller profile with active spam/bot flags detected.";
            assessment.actionRecommendation = "Exercise extreme caution and avoid sharing personal data.";
        } else if (text.isEmpty()) {
            // Dialogue is currently being captured
            assessment.riskLevel = isKnownContact ? LiveRiskResult.Level.LOW : LiveRiskResult.Level.MEDIUM;
            assessment.fraudScore = isKnownContact ? 5 : 30;
            assessment.threatCategory = "AI MONITORING: LISTENING TO LIVE AUDIO";
            assessment.detailedSummary = isKnownContact ?
                    "Verified Saved Contact • Listening for conversation triggers..." :
                    "Unknown Caller • Monitoring incoming speech for scam patterns...";
            assessment.actionRecommendation = "AI Guard is actively monitoring the live call audio stream. Continue conversation normally.";
            if (isKnownContact) {
                assessment.detectedIndicators.add("• Number matches saved contact in phonebook");
            } else {
                assessment.detectedIndicators.add("• Caller is not in your contacts list");
            }
            assessment.detectedIndicators.add("• Real-time speech stream active");
        } else if (!isKnownContact) {
            assessment.riskLevel = LiveRiskResult.Level.MEDIUM;
            assessment.fraudScore = 35;
            assessment.threatCategory = "MODERATE RISK: UNKNOWN CALLER";
            assessment.detailedSummary = "Caller is not in your contacts. Natural voice verified with no scam triggers detected.";
            assessment.actionRecommendation = "Normal conversation so far. Continue with standard caution.";
            assessment.detectedIndicators.add("• Natural human voice verified");
            assessment.detectedIndicators.add("• No financial or lottery triggers found in dialogue");
        } else {
            assessment.riskLevel = LiveRiskResult.Level.LOW;
            assessment.fraudScore = 5;
            assessment.threatCategory = "LOW RISK: TRUSTED CONTACT";
            assessment.detailedSummary = "Verified contact from your phonebook. Voice and conversation are clean.";
            assessment.actionRecommendation = "Safe call. No risk factors identified.";
            assessment.detectedIndicators.add("• Saved contact in your phonebook");
            assessment.detectedIndicators.add("• Clean conversational history");
        }

        return assessment;
    }
}