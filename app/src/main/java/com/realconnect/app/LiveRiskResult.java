package com.realconnect.app;

import java.util.ArrayList;
import java.util.List;

public class LiveRiskResult {
    public enum Level {
        LOW,
        MEDIUM,
        HIGH
    }

    private Level level;
    private int riskScore; // 0 to 100
    private String summary;
    private String recommendation;
    private boolean isBot;
    private boolean isSpam;
    private boolean isTrustedContact;
    private boolean isContextEvaluated;
    private int listeningDurationSeconds;
    private String transcriptExcerpt = "";
    private String callerIntent = "";
    private final List<String> indicators = new ArrayList<>();
    private final List<String> flaggedKeywords = new ArrayList<>();

    public LiveRiskResult(Level level, int riskScore, String summary, String recommendation) {
        this.level = level;
        this.riskScore = riskScore;
        this.summary = summary;
        this.recommendation = recommendation;
    }

    public Level getLevel() { return level; }
    public void setLevel(Level level) { this.level = level; }

    public int getRiskScore() { return riskScore; }
    public void setRiskScore(int riskScore) { this.riskScore = riskScore; }

    public String getSummary() { return summary; }
    public void setSummary(String summary) { this.summary = summary; }

    public String getRecommendation() { return recommendation; }
    public void setRecommendation(String recommendation) { this.recommendation = recommendation; }

    public boolean isBot() { return isBot; }
    public void setBot(boolean bot) { isBot = bot; }

    public boolean isSpam() { return isSpam; }
    public void setSpam(boolean spam) { isSpam = spam; }

    public boolean isTrustedContact() { return isTrustedContact; }
    public void setTrustedContact(boolean trustedContact) { this.isTrustedContact = trustedContact; }

    public boolean isContextEvaluated() { return isContextEvaluated; }
    public void setContextEvaluated(boolean contextEvaluated) { isContextEvaluated = contextEvaluated; }

    public int getListeningDurationSeconds() { return listeningDurationSeconds; }
    public void setListeningDurationSeconds(int listeningDurationSeconds) { this.listeningDurationSeconds = listeningDurationSeconds; }

    public String getTranscriptExcerpt() { return transcriptExcerpt; }
    public void setTranscriptExcerpt(String transcriptExcerpt) { this.transcriptExcerpt = transcriptExcerpt; }

    public String getCallerIntent() { return callerIntent; }
    public void setCallerIntent(String callerIntent) { this.callerIntent = callerIntent; }

    public List<String> getIndicators() { return indicators; }
    public void addIndicator(String indicator) { this.indicators.add(indicator); }

    public List<String> getFlaggedKeywords() { return flaggedKeywords; }
    public void addFlaggedKeyword(String keyword) {
        if (!this.flaggedKeywords.contains(keyword)) {
            this.flaggedKeywords.add(keyword);
        }
    }
}