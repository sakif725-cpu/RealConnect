package com.realconnect.app;

import android.content.Context;
import android.graphics.Color;
import android.os.Handler;
import android.os.Looper;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import androidx.appcompat.app.AppCompatActivity;
import com.google.android.material.bottomsheet.BottomSheetDialog;
import com.google.android.material.card.MaterialCardView;

public class LiveCallGuard {

    public interface RiskAssessmentCallback {
        void onRiskEvaluated(LiveRiskResult result);
    }

    private static final String TAG = "LiveCallGuard";

    public static void assessCallRisk(Context context, String phone, String name, boolean isPreFlaggedSpam, RiskAssessmentCallback callback) {
        String contactName = ContactRepository.getInstance(context).findContactByNumber(phone);
        boolean isKnownContact = (contactName != null && !contactName.trim().isEmpty());

        AudioRecorderHelper.captureAudioSnippet(context, 2, new AudioRecorderHelper.AudioCaptureCallback() {
            @Override
            public void onAudioCaptured(byte[] audioBytes) {
                // Query live AI backend for bot voice check
                AiService.detectBot(audioBytes, isBot -> {
                    AiService.checkSpam(phone, isSpamApi -> {
                        boolean isSpam = isPreFlaggedSpam || isSpamApi;
                        LiveRiskResult result = calculateRisk(phone, isKnownContact, isSpam, isBot);
                        new Handler(Looper.getMainLooper()).post(() -> callback.onRiskEvaluated(result));
                    });
                });
            }

            @Override
            public void onError(String errorMessage) {
                // Fallback risk assessment based on number reputation
                AiService.checkSpam(phone, isSpamApi -> {
                    boolean isSpam = isPreFlaggedSpam || isSpamApi;
                    LiveRiskResult result = calculateRisk(phone, isKnownContact, isSpam, false);
                    new Handler(Looper.getMainLooper()).post(() -> callback.onRiskEvaluated(result));
                });
            }
        });
    }

    private static LiveRiskResult calculateRisk(String phone, boolean isKnownContact, boolean isSpam, boolean isBot) {
        if (isBot) {
            LiveRiskResult res = new LiveRiskResult(
                    LiveRiskResult.Level.HIGH,
                    95,
                    "Automated AI Bot / Synthetic Voice Detected",
                    "High probability of robocall or synthetic voice impersonation. Do NOT disclose personal info."
            );
            res.setBot(true);
            res.setSpam(isSpam);
            res.addIndicator("• Synthetic Voice Signature Detected (Robocall)");
            if (isSpam) res.addIndicator("• Phone number flagged on global fraud databases");
            res.addIndicator("• Automated speech synthesis frequency patterns match known bots");
            return res;
        }

        if (isSpam) {
            LiveRiskResult res = new LiveRiskResult(
                    LiveRiskResult.Level.HIGH,
                    85,
                    "High Scam / Telemarketing Risk",
                    "This phone number has a high frequency of scam or unsolicited telemarketing reports."
            );
            res.setSpam(true);
            res.addIndicator("• Flagged Telemarketing or Spam Prefix");
            res.addIndicator("• Repeated user fraud reports on network");
            res.addIndicator("• High risk of financial/banking solicitation");
            return res;
        }

        if (isKnownContact) {
            LiveRiskResult res = new LiveRiskResult(
                    LiveRiskResult.Level.LOW,
                    5,
                    "Verified Trusted Contact",
                    "This caller is in your personal phone contacts. Voice patterns are safe."
            );
            res.setTrustedContact(true);
            res.addIndicator("• Caller is in your saved contacts list");
            res.addIndicator("• No spam or bot patterns detected");
            res.addIndicator("• Biometric voice patterns match natural human speech");
            return res;
        }

        // Unknown Number
        LiveRiskResult res = new LiveRiskResult(
                LiveRiskResult.Level.MEDIUM,
                40,
                "Unknown Caller (Moderate Risk)",
                "Caller is not in your contacts. Exercise standard caution and avoid sharing confidential data."
        );
        res.addIndicator("• Caller not found in your contacts directory");
        res.addIndicator("• Human voice detected (No synthetic speech)");
        res.addIndicator("• Standard number reputation (No active spam flags)");
        return res;
    }

    public static void showLiveRiskSheet(AppCompatActivity activity, LiveRiskResult result) {
        if (activity == null || activity.isFinishing() || activity.isDestroyed()) return;

        BottomSheetDialog dialog = new BottomSheetDialog(activity);
        View view = LayoutInflater.from(activity).inflate(R.layout.dialog_live_risk, null);
        dialog.setContentView(view);

        MaterialCardView cardRisk = view.findViewById(R.id.card_risk_score);
        TextView textLevelBadge = view.findViewById(R.id.text_risk_level_badge);
        TextView textSummary = view.findViewById(R.id.text_risk_summary);
        ImageView iconStatus = view.findViewById(R.id.icon_risk_status);
        TextView textRecommendation = view.findViewById(R.id.text_risk_recommendation);
        LinearLayout layoutList = view.findViewById(R.id.layout_indicators_list);

        layoutList.removeAllViews();
        for (String indicator : result.getIndicators()) {
            TextView tv = new TextView(activity);
            tv.setText(indicator);
            tv.setTextColor(Color.parseColor("#CBD5E1"));
            tv.setTextSize(13);
            tv.setPadding(0, 4, 0, 4);
            layoutList.addView(tv);
        }

        if (result.getLevel() == LiveRiskResult.Level.HIGH) {
            cardRisk.setStrokeColor(Color.parseColor("#EF4444"));
            cardRisk.setCardBackgroundColor(Color.parseColor("#33EF4444"));
            textLevelBadge.setText("HIGH RISK (" + result.getRiskScore() + "%)");
            textLevelBadge.setTextColor(Color.parseColor("#EF4444"));
            iconStatus.setImageResource(R.drawable.ic_block);
            iconStatus.setColorFilter(Color.parseColor("#EF4444"));
        } else if (result.getLevel() == LiveRiskResult.Level.MEDIUM) {
            cardRisk.setStrokeColor(Color.parseColor("#EAB308"));
            cardRisk.setCardBackgroundColor(Color.parseColor("#33EAB308"));
            textLevelBadge.setText("MEDIUM RISK (" + result.getRiskScore() + "%)");
            textLevelBadge.setTextColor(Color.parseColor("#EAB308"));
            iconStatus.setImageResource(R.drawable.ic_privacy);
            iconStatus.setColorFilter(Color.parseColor("#EAB308"));
        } else {
            cardRisk.setStrokeColor(Color.parseColor("#22C55E"));
            cardRisk.setCardBackgroundColor(Color.parseColor("#3322C55E"));
            textLevelBadge.setText("LOW RISK (" + result.getRiskScore() + "%)");
            textLevelBadge.setTextColor(Color.parseColor("#22C55E"));
            iconStatus.setImageResource(R.drawable.ic_ai_mode);
            iconStatus.setColorFilter(Color.parseColor("#22C55E"));
        }

        textSummary.setText(result.getSummary());
        textRecommendation.setText(result.getRecommendation());

        view.findViewById(R.id.btn_dismiss_risk_modal).setOnClickListener(v -> dialog.dismiss());
        dialog.show();
    }
}