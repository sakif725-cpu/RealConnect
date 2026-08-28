package com.realconnect.app;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class ScamPatternDetector {

    public static class AnalysisOutcome {
        public int scamScoreBonus = 0;
        public boolean isFinancialPhishing = false;
        public boolean isLotteryScam = false;
        public boolean isOtpTheft = false;
        public boolean isUrgencyImpersonation = false;
        public final List<String> detectedTriggers = new ArrayList<>();
        public final List<String> flaggedPhrases = new ArrayList<>();
    }

    private static final String[] FINANCIAL_KEYWORDS = {
            "credit card", "cvv", "cvv number", "card number", "bank account", "account number",
            "debit card", "atm pin", "expiry date", "deposit money", "transfer money", "bank details"
    };

    private static final String[] LOTTERY_KEYWORDS = {
            "won a lottery", "lottery", "5 crore", "5 cr", "prize", "lucky draw", "won cash",
            "congratulations you have won", "congratulations you won", "claim your prize", "jackpot"
    };

    private static final String[] OTP_KEYWORDS = {
            "otp", "one time password", "verification code", "security code", "share the pin",
            "sms code", "6 digit code", "4 digit code"
    };

    private static final String[] IMPERSONATION_KEYWORDS = {
            "calling from ajio", "calling from bank", "customer care", "police department",
            "customs department", "arrest warrant", "deposit right now", "immediately", "urgent"
    };

    public static AnalysisOutcome analyze(String transcript) {
        AnalysisOutcome outcome = new AnalysisOutcome();
        if (transcript == null || transcript.trim().isEmpty()) {
            return outcome;
        }

        String lower = transcript.toLowerCase(Locale.ROOT);

        // 1. Financial check
        for (String kw : FINANCIAL_KEYWORDS) {
            if (lower.contains(kw)) {
                outcome.isFinancialPhishing = true;
                outcome.scamScoreBonus += 35;
                outcome.flaggedPhrases.add("Request for banking / card data: \"" + kw + "\"");
                outcome.detectedTriggers.add(kw);
            }
        }

        // 2. Lottery check
        for (String kw : LOTTERY_KEYWORDS) {
            if (lower.contains(kw)) {
                outcome.isLotteryScam = true;
                outcome.scamScoreBonus += 30;
                outcome.flaggedPhrases.add("Fake lottery / prize claim: \"" + kw + "\"");
                outcome.detectedTriggers.add(kw);
            }
        }

        // 3. OTP check
        for (String kw : OTP_KEYWORDS) {
            if (lower.contains(kw)) {
                outcome.isOtpTheft = true;
                outcome.scamScoreBonus += 40;
                outcome.flaggedPhrases.add("Attempt to extract OTP / secret code: \"" + kw + "\"");
                outcome.detectedTriggers.add(kw);
            }
        }

        // 4. Impersonation / Urgency check
        for (String kw : IMPERSONATION_KEYWORDS) {
            if (lower.contains(kw)) {
                outcome.isUrgencyImpersonation = true;
                outcome.scamScoreBonus += 25;
                outcome.flaggedPhrases.add("Brand impersonation / false urgency: \"" + kw + "\"");
                outcome.detectedTriggers.add(kw);
            }
        }

        outcome.scamScoreBonus = Math.min(outcome.scamScoreBonus, 95);
        return outcome;
    }
}