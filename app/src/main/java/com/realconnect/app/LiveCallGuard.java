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

    public static void assessCallRisk(Context context, String phone, String name, boolean isPreFlaggedSpam, RiskAssessmentCallback callback) {
        String contactName = ContactRepository.getInstance(context).findContactByNumber(phone);
        boolean isKnownContact = (contactName != null && !contactName.trim().isEmpty());

        AiService.checkSpam(phone, isSpamApi -> {
            boolean isSpam = isPreFlaggedSpam || isSpamApi;
            FraudIntelligenceEngine.FraudAssessment assessment = FraudIntelligenceEngine.evaluate("", isKnownContact, isSpam, false);
            LiveRiskResult result = new LiveRiskResult(
                    assessment.riskLevel,
                    assessment.fraudScore,
                    assessment.detailedSummary,
                    assessment.actionRecommendation
            );
            result.setContextEvaluated(true);
            result.setSpam(isSpam);
            result.setTrustedContact(isKnownContact);
            for (String ind : assessment.detectedIndicators) {
                result.addIndicator(ind);
            }
            new Handler(Looper.getMainLooper()).post(() -> callback.onRiskEvaluated(result));
        });
    }

    public static void showLiveRiskSheet(AppCompatActivity activity, LiveRiskResult result) {
        if (activity == null || activity.isFinishing() || activity.isDestroyed() || result == null) return;

        BottomSheetDialog dialog = new BottomSheetDialog(activity);
        View view = LayoutInflater.from(activity).inflate(R.layout.dialog_live_risk, null);
        dialog.setContentView(view);

        // Section 1: Live Input Conversation Views
        TextView textAudioContent = view.findViewById(R.id.text_input_audio_content);
        TextView textAudioSampling = view.findViewById(R.id.text_audio_sampling_info);

        String excerpt = result.getTranscriptExcerpt();
        if (excerpt != null && !excerpt.trim().isEmpty()) {
            textAudioContent.setText("\"" + excerpt.trim() + "\"");
            textAudioContent.setTextColor(Color.parseColor("#F1F5F9"));
        } else {
            textAudioContent.setText("Monitoring live conversation text & spoken dialogue...");
            textAudioContent.setTextColor(Color.parseColor("#94A3B8"));
        }

        int duration = result.getListeningDurationSeconds();
        if (duration < 10) {
            textAudioSampling.setText("• Call active: " + duration + "s (AI text evaluation triggers at 10s)");
        } else {
            textAudioSampling.setText("• Analyzed " + duration + "s of real-time conversation text");
        }

        // Section 2: Caller Intention (AI Analyzed)
        TextView textCallerIntent = view.findViewById(R.id.text_caller_intent);
        if (textCallerIntent != null) {
            String intent = result.getCallerIntent();
            if (intent != null && !intent.trim().isEmpty()) {
                textCallerIntent.setText(intent.trim());
            } else {
                textCallerIntent.setText("AI is analyzing live conversation intent...");
            }
        }

        // Section 3: AI Risk Assessment Views
        MaterialCardView cardRisk = view.findViewById(R.id.card_risk_score);
        TextView textLevelBadge = view.findViewById(R.id.text_risk_level_badge);
        TextView textSummary = view.findViewById(R.id.text_risk_summary);
        ImageView iconStatus = view.findViewById(R.id.icon_risk_status);
        TextView textRecommendation = view.findViewById(R.id.text_risk_recommendation);
        LinearLayout layoutList = view.findViewById(R.id.layout_indicators_list);

        layoutList.removeAllViews();

        if (!result.getFlaggedKeywords().isEmpty()) {
            TextView tvKeywords = new TextView(activity);
            tvKeywords.setText("🚩 High-Risk Triggers: " + String.join(", ", result.getFlaggedKeywords()));
            tvKeywords.setTextColor(Color.parseColor("#FCA5A5"));
            tvKeywords.setTextSize(13);
            tvKeywords.setPadding(0, 4, 0, 4);
            layoutList.addView(tvKeywords);
        }

        for (String indicator : result.getIndicators()) {
            TextView tv = new TextView(activity);
            tv.setText(indicator);
            tv.setTextColor(Color.parseColor("#CBD5E1"));
            tv.setTextSize(13);
            tv.setPadding(0, 3, 0, 3);
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
            textLevelBadge.setText("MODERATE RISK (" + result.getRiskScore() + "%)");
            textLevelBadge.setTextColor(Color.parseColor("#EAB308"));
            iconStatus.setImageResource(R.drawable.ic_privacy);
            iconStatus.setColorFilter(Color.parseColor("#EAB308"));
        } else {
            cardRisk.setStrokeColor(Color.parseColor("#22C55E"));
            cardRisk.setCardBackgroundColor(Color.parseColor("#3322C55E"));
            textLevelBadge.setText("SAFE (" + result.getRiskScore() + "%)");
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