package com.realconnect.app;

import retrofit2.Call;
import retrofit2.http.Body;
import retrofit2.http.GET;
import retrofit2.http.POST;
import retrofit2.http.Query;

public interface AiApiService {
    // Example endpoint for spam detection
    @GET("spam-check")
    Call<SpamResponse> checkSpam(@Query("number") String phoneNumber);

    // Example endpoint for voice analysis (bot detection)
    @POST("voice-analysis")
    Call<VoiceAnalysisResponse> analyzeVoice(@Body VoiceData data);

    // Example endpoint for speaker verification
    @POST("verify-speaker")
    Call<VerificationResponse> verifySpeaker(@Body SpeakerData data);

    // Data models
    class SpamResponse {
        public boolean isSpam;
        public String reason;
    }

    class VoiceAnalysisResponse {
        public boolean isBot;
        public float confidence;
    }

    class VerificationResponse {
        public boolean verified;
        public String status;
    }

    class VoiceData {
        public String audioBase64;
        public VoiceData(String audioBase64) { this.audioBase64 = audioBase64; }
    }

    class SpeakerData {
        public String phoneNumber;
        public String audioBase64;
        public SpeakerData(String phoneNumber, String audioBase64) {
            this.phoneNumber = phoneNumber;
            this.audioBase64 = audioBase64;
        }
    }
}
