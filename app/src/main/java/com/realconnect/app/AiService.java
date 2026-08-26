package com.realconnect.app;

import android.util.Base64;
import android.util.Log;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;
import retrofit2.Retrofit;
import retrofit2.converter.gson.GsonConverterFactory;

/**
 * Service to handle AI-based features: Anti-spam, Voice Detection, and Speaker Verification.
 * This service now makes real API calls using Retrofit.
 */
public class AiService {

    private static final String TAG = "AiService";
    private static final String BASE_URL = "https://your-ai-api-endpoint.com/"; // Replace with your real AI backend URL
    private static AiApiService apiService;

    public interface AiCallback<T> {
        void onResult(T result);
    }

    private static AiApiService getApi() {
        if (apiService == null) {
            Retrofit retrofit = new Retrofit.Builder()
                    .baseUrl(BASE_URL)
                    .addConverterFactory(GsonConverterFactory.create())
                    .build();
            apiService = retrofit.create(AiApiService.class);
        }
        return apiService;
    }

    public static void checkSpam(String phoneNumber, AiCallback<Boolean> callback) {
        getApi().checkSpam(phoneNumber).enqueue(new Callback<AiApiService.SpamResponse>() {
            @Override
            public void onResponse(Call<AiApiService.SpamResponse> call, Response<AiApiService.SpamResponse> response) {
                if (response.isSuccessful() && response.body() != null) {
                    callback.onResult(response.body().isSpam);
                } else {
                    callback.onResult(false); // Default to safe if API fails
                }
            }

            @Override
            public void onFailure(Call<AiApiService.SpamResponse> call, Throwable t) {
                Log.e(TAG, "Spam Check Failed", t);
                callback.onResult(false);
            }
        });
    }

    public static void detectBot(byte[] voiceData, AiCallback<Boolean> callback) {
        String audioBase64 = voiceData != null ? Base64.encodeToString(voiceData, Base64.DEFAULT) : "";
        getApi().analyzeVoice(new AiApiService.VoiceData(audioBase64)).enqueue(new Callback<AiApiService.VoiceAnalysisResponse>() {
            @Override
            public void onResponse(Call<AiApiService.VoiceAnalysisResponse> call, Response<AiApiService.VoiceAnalysisResponse> response) {
                if (response.isSuccessful() && response.body() != null) {
                    callback.onResult(response.body().isBot);
                } else {
                    callback.onResult(false);
                }
            }

            @Override
            public void onFailure(Call<AiApiService.VoiceAnalysisResponse> call, Throwable t) {
                Log.e(TAG, "Bot Detection Failed", t);
                callback.onResult(false);
            }
        });
    }

    public static void verifySpeaker(String phoneNumber, byte[] voiceData, AiCallback<String> callback) {
        String audioBase64 = voiceData != null ? Base64.encodeToString(voiceData, Base64.DEFAULT) : "";
        getApi().verifySpeaker(new AiApiService.SpeakerData(phoneNumber, audioBase64)).enqueue(new Callback<AiApiService.VerificationResponse>() {
            @Override
            public void onResponse(Call<AiApiService.VerificationResponse> call, Response<AiApiService.VerificationResponse> response) {
                if (response.isSuccessful() && response.body() != null) {
                    callback.onResult(response.body().verified ? "Verified" : "Unverified");
                } else {
                    callback.onResult("Error");
                }
            }

            @Override
            public void onFailure(Call<AiApiService.VerificationResponse> call, Throwable t) {
                Log.e(TAG, "Speaker Verification Failed", t);
                callback.onResult("Failed");
            }
        });
    }
}
