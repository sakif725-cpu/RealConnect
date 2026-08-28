package com.realconnect.app;

import android.util.Base64;
import android.util.Log;
import okhttp3.OkHttpClient;
import okhttp3.logging.HttpLoggingInterceptor;
import java.util.concurrent.TimeUnit;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;
import retrofit2.Retrofit;
import retrofit2.converter.gson.GsonConverterFactory;

/**
 * Service to handle AI-based features: Anti-spam, Voice Detection, and Speaker Verification.
 * Connected to live AI backend: https://ai-detection-sys.onrender.com/
 */
public class AiService {

    private static final String TAG = "AiService";
    private static final String BASE_URL = "https://ai-detection-sys.onrender.com/";
    private static AiApiService apiService;

    public interface AiCallback<T> {
        void onResult(T result);
    }

    private static AiApiService getApi() {
        if (apiService == null) {
            HttpLoggingInterceptor loggingInterceptor = new HttpLoggingInterceptor();
            loggingInterceptor.setLevel(HttpLoggingInterceptor.Level.BODY);

            OkHttpClient okHttpClient = new OkHttpClient.Builder()
                    .addInterceptor(loggingInterceptor)
                    .connectTimeout(30, TimeUnit.SECONDS)
                    .readTimeout(30, TimeUnit.SECONDS)
                    .build();

            Retrofit retrofit = new Retrofit.Builder()
                    .baseUrl(BASE_URL)
                    .client(okHttpClient)
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
        analyzeVoiceBehavior(voiceData, response -> {
            if (response != null) {
                callback.onResult(response.isBot || response.isScammer);
            } else {
                callback.onResult(false);
            }
        });
    }

    public static void analyzeVoiceBehavior(byte[] voiceData, AiCallback<AiApiService.VoiceAnalysisResponse> callback) {
        String audioBase64 = voiceData != null ? Base64.encodeToString(voiceData, Base64.NO_WRAP) : "";
        getApi().analyzeVoice(new AiApiService.VoiceData(audioBase64)).enqueue(new Callback<AiApiService.VoiceAnalysisResponse>() {
            @Override
            public void onResponse(Call<AiApiService.VoiceAnalysisResponse> call, Response<AiApiService.VoiceAnalysisResponse> response) {
                if (response.isSuccessful() && response.body() != null) {
                    callback.onResult(response.body());
                } else {
                    callback.onResult(null);
                }
            }

            @Override
            public void onFailure(Call<AiApiService.VoiceAnalysisResponse> call, Throwable t) {
                Log.e(TAG, "Voice Analysis API Failed", t);
                callback.onResult(null);
            }
        });
    }

    public static void verifySpeaker(String phoneNumber, byte[] voiceData, AiCallback<String> callback) {
        String audioBase64 = voiceData != null ? Base64.encodeToString(voiceData, Base64.NO_WRAP) : "";
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
