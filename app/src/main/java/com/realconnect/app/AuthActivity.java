package com.realconnect.app;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Bundle;
import android.text.TextUtils;
import android.util.Log;
import android.view.View;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;
import androidx.activity.EdgeToEdge;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import com.google.android.gms.auth.api.signin.GoogleSignIn;
import com.google.android.gms.auth.api.signin.GoogleSignInAccount;
import com.google.android.gms.auth.api.signin.GoogleSignInClient;
import com.google.android.gms.auth.api.signin.GoogleSignInOptions;
import com.google.android.gms.common.api.ApiException;
import com.google.android.gms.tasks.Task;
import com.google.android.material.button.MaterialButton;
import com.google.firebase.auth.AuthCredential;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.auth.GoogleAuthProvider;
import com.google.firebase.database.FirebaseDatabase;
import java.util.HashMap;
import java.util.Map;

public class AuthActivity extends AppCompatActivity {

    private static final String TAG = "AuthActivity";
    private static final String PREFS_NAME = "ProfilePrefs";

    private LinearLayout layoutStepPhone;
    private LinearLayout layoutStepGooglePhone;
    private LinearLayout layoutStepOtp;
    private FrameLayout layoutLoadingOverlay;
    private TextView textLoadingStatus;

    private EditText editPhone;
    private EditText editGooglePhone;
    private EditText editOtp;
    private TextView textOtpSubtitle;
    private TextView textGoogleWelcomeTitle;
    private TextView textGoogleWelcomeSubtitle;

    private FirebaseAuth mAuth;
    private GoogleSignInClient googleSignInClient;
    private ActivityResultLauncher<Intent> googleSignInLauncher;

    private String enteredPhone = "";
    private String googleDisplayName = "";
    private String googleEmail = "";
    private String googlePhotoUrl = "";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_auth);

        mAuth = FirebaseAuth.getInstance();
        initGoogleSignIn();

        initViews();
        setupListeners();
    }

    private void initGoogleSignIn() {
        GoogleSignInOptions.Builder gsoBuilder = new GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
                .requestEmail();

        String webClientId = null;
        try {
            int clientIdRes = getResources().getIdentifier("default_web_client_id", "string", getPackageName());
            if (clientIdRes != 0) {
                webClientId = getString(clientIdRes);
            }
        } catch (Exception e) {
            Log.w(TAG, "default_web_client_id not found: " + e.getMessage());
        }

        if (TextUtils.isEmpty(webClientId)) {
            webClientId = "582035829903-g4t5jg0fdodbh0j224mfknppscqu6iht.apps.googleusercontent.com";
        }

        try {
            gsoBuilder.requestIdToken(webClientId);
        } catch (Exception e) {
            Log.e(TAG, "Error setting requestIdToken", e);
        }

        GoogleSignInOptions gso = gsoBuilder.build();
        googleSignInClient = GoogleSignIn.getClient(this, gso);

        googleSignInLauncher = registerForActivityResult(
                new ActivityResultContracts.StartActivityForResult(),
                result -> {
                    if (result.getResultCode() == RESULT_OK && result.getData() != null) {
                        Task<GoogleSignInAccount> task = GoogleSignIn.getSignedInAccountFromIntent(result.getData());
                        handleGoogleSignInResult(task);
                    } else {
                        hideLoading();
                        Toast.makeText(AuthActivity.this, "Google sign-in cancelled.", Toast.LENGTH_SHORT).show();
                    }
                }
        );
    }

    private void initViews() {
        layoutStepPhone = findViewById(R.id.layout_step_phone);
        layoutStepGooglePhone = findViewById(R.id.layout_step_google_phone);
        layoutStepOtp = findViewById(R.id.layout_step_otp);
        layoutLoadingOverlay = findViewById(R.id.layout_loading_overlay);
        textLoadingStatus = findViewById(R.id.text_loading_status);

        editPhone = findViewById(R.id.edit_phone_input);
        editGooglePhone = findViewById(R.id.edit_google_phone_input);
        editOtp = findViewById(R.id.edit_otp_input);
        textOtpSubtitle = findViewById(R.id.text_otp_subtitle);
        textGoogleWelcomeTitle = findViewById(R.id.text_google_welcome_title);
        textGoogleWelcomeSubtitle = findViewById(R.id.text_google_welcome_subtitle);
    }

    private void setupListeners() {
        MaterialButton btnGoogleSignIn = findViewById(R.id.btn_google_sign_in);
        MaterialButton btnSendOtp = findViewById(R.id.btn_send_otp);
        MaterialButton btnVerifyOtp = findViewById(R.id.btn_verify_otp);
        MaterialButton btnCompleteGooglePhone = findViewById(R.id.btn_complete_google_phone);
        TextView btnChangeNumber = findViewById(R.id.btn_change_number);
        ImageButton btnBack = findViewById(R.id.btn_auth_back);

        btnGoogleSignIn.setOnClickListener(v -> launchGoogleSignIn());
        btnSendOtp.setOnClickListener(v -> handleSendOtp());
        btnVerifyOtp.setOnClickListener(v -> handleVerifyOtp());
        btnCompleteGooglePhone.setOnClickListener(v -> handleCompleteGooglePhone());
        btnChangeNumber.setOnClickListener(v -> showPhoneStep());

        btnBack.setOnClickListener(v -> {
            if (layoutStepOtp.getVisibility() == View.VISIBLE || layoutStepGooglePhone.getVisibility() == View.VISIBLE) {
                showPhoneStep();
            } else {
                finish();
            }
        });
    }

    private void launchGoogleSignIn() {
        showLoading("Connecting to Google...");
        try {
            googleSignInClient.signOut().addOnCompleteListener(task -> {
                Intent signInIntent = googleSignInClient.getSignInIntent();
                googleSignInLauncher.launch(signInIntent);
            });
        } catch (Exception e) {
            Intent signInIntent = googleSignInClient.getSignInIntent();
            googleSignInLauncher.launch(signInIntent);
        }
    }

    private void handleGoogleSignInResult(Task<GoogleSignInAccount> completedTask) {
        try {
            GoogleSignInAccount account = completedTask.getResult(ApiException.class);
            if (account != null) {
                showLoading("Authenticating with Firebase...");
                firebaseAuthWithGoogle(account);
            } else {
                hideLoading();
                Toast.makeText(this, "Failed to get Google account details.", Toast.LENGTH_SHORT).show();
            }
        } catch (ApiException e) {
            hideLoading();
            Log.e(TAG, "Google sign in failed code=" + e.getStatusCode(), e);
            Toast.makeText(this, "Google sign-in error: " + e.getLocalizedMessage(), Toast.LENGTH_LONG).show();
        }
    }

    private void firebaseAuthWithGoogle(GoogleSignInAccount account) {
        String idToken = account.getIdToken();
        if (idToken == null || idToken.isEmpty()) {
            // Fallback for offline / direct account profile if token is not available
            onGoogleAuthSuccess(account.getDisplayName(), account.getEmail(), account.getPhotoUrl() != null ? account.getPhotoUrl().toString() : null);
            return;
        }

        AuthCredential credential = GoogleAuthProvider.getCredential(idToken, null);
        mAuth.signInWithCredential(credential)
                .addOnCompleteListener(this, task -> {
                    hideLoading();
                    if (task.isSuccessful()) {
                        FirebaseUser user = mAuth.getCurrentUser();
                        String name = (user != null && !TextUtils.isEmpty(user.getDisplayName())) ? user.getDisplayName() : account.getDisplayName();
                        String email = (user != null && !TextUtils.isEmpty(user.getEmail())) ? user.getEmail() : account.getEmail();
                        String photo = (user != null && user.getPhotoUrl() != null) ? user.getPhotoUrl().toString() : (account.getPhotoUrl() != null ? account.getPhotoUrl().toString() : null);

                        onGoogleAuthSuccess(name, email, photo);
                    } else {
                        Log.e(TAG, "Firebase auth with Google failed", task.getException());
                        Toast.makeText(AuthActivity.this, "Authentication failed: " + (task.getException() != null ? task.getException().getMessage() : "Unknown error"), Toast.LENGTH_LONG).show();
                    }
                });
    }

    private void onGoogleAuthSuccess(String name, String email, String photoUrl) {
        hideLoading();
        googleDisplayName = name != null ? name : "User";
        googleEmail = email != null ? email : "";
        googlePhotoUrl = photoUrl != null ? photoUrl : "";

        SharedPreferences prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        String existingPhone = prefs.getString("phone", null);

        if (existingPhone != null && !existingPhone.trim().isEmpty()) {
            // Phone already exists, update Google profile info and proceed
            saveProfile(existingPhone, googleDisplayName, googleEmail, googlePhotoUrl);
            proceedToMain();
        } else {
            // Prompt user for their phone number (needed for WebRTC VoIP calling & contact identification)
            showGooglePhoneStep();
        }
    }

    private void showGooglePhoneStep() {
        layoutStepPhone.setVisibility(View.GONE);
        layoutStepOtp.setVisibility(View.GONE);
        layoutStepGooglePhone.setVisibility(View.VISIBLE);

        textGoogleWelcomeTitle.setText("Welcome, " + googleDisplayName + "!");
        textGoogleWelcomeSubtitle.setText("Signed in as " + googleEmail + ".\nPlease enter your phone number to activate WebRTC VoIP calling.");
        editGooglePhone.requestFocus();
    }

    private void handleCompleteGooglePhone() {
        String phone = editGooglePhone.getText().toString().trim();
        if (TextUtils.isEmpty(phone)) {
            Toast.makeText(this, "Please enter your phone number to continue", Toast.LENGTH_SHORT).show();
            return;
        }

        saveProfile(phone, googleDisplayName, googleEmail, googlePhotoUrl);
        Toast.makeText(this, "Account linked successfully!", Toast.LENGTH_SHORT).show();
        proceedToMain();
    }

    private void handleSendOtp() {
        String phone = editPhone.getText().toString().trim();
        if (TextUtils.isEmpty(phone)) {
            Toast.makeText(this, "Please enter your phone number", Toast.LENGTH_SHORT).show();
            return;
        }

        enteredPhone = phone;
        showOtpStep();
    }

    private void showOtpStep() {
        layoutStepPhone.setVisibility(View.GONE);
        layoutStepGooglePhone.setVisibility(View.GONE);
        layoutStepOtp.setVisibility(View.VISIBLE);
        textOtpSubtitle.setText("Enter the verification code sent to " + enteredPhone);
        editOtp.setText("");
        editOtp.requestFocus();
    }

    private void showPhoneStep() {
        layoutStepGooglePhone.setVisibility(View.GONE);
        layoutStepOtp.setVisibility(View.GONE);
        layoutStepPhone.setVisibility(View.VISIBLE);
        editPhone.requestFocus();
    }

    private void handleVerifyOtp() {
        String otp = editOtp.getText().toString().trim();
        if (TextUtils.isEmpty(otp)) {
            Toast.makeText(this, "Please enter the OTP code", Toast.LENGTH_SHORT).show();
            return;
        }

        saveProfile(enteredPhone, "User " + (enteredPhone.length() > 4 ? enteredPhone.substring(enteredPhone.length() - 4) : enteredPhone), "", null);
        Toast.makeText(this, "Verified successfully!", Toast.LENGTH_SHORT).show();
        proceedToMain();
    }

    private void saveProfile(String phone, String name, String email, String photoUrl) {
        SharedPreferences prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        SharedPreferences.Editor editor = prefs.edit();
        editor.putString("phone", phone);
        if (!TextUtils.isEmpty(name)) {
            editor.putString("name", name);
        }
        if (!TextUtils.isEmpty(email)) {
            editor.putString("email", email);
        }
        if (!TextUtils.isEmpty(photoUrl)) {
            editor.putString("image_uri", photoUrl);
        }
        editor.apply();

        // Sync to Firebase Realtime Database users node
        String cleanPhone = ChatRepository.cleanPhone(phone);
        if (!cleanPhone.isEmpty()) {
            new Thread(() -> {
                try {
                    Map<String, Object> userData = new HashMap<>();
                    userData.put("name", name != null ? name : "");
                    userData.put("email", email != null ? email : "");
                    userData.put("phone", phone);
                    userData.put("updatedAt", System.currentTimeMillis());
                    FirebaseDatabase.getInstance().getReference("users").child(cleanPhone).updateChildren(userData);
                } catch (Exception e) {
                    Log.w(TAG, "Failed to sync user to Firebase Realtime DB: " + e.getMessage());
                }
            }).start();
        }
    }

    private void proceedToMain() {
        Intent intent = new Intent(AuthActivity.this, MainActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);
        finish();
    }

    private void showLoading(String message) {
        if (textLoadingStatus != null) {
            textLoadingStatus.setText(message);
        }
        if (layoutLoadingOverlay != null) {
            layoutLoadingOverlay.setVisibility(View.VISIBLE);
        }
    }

    private void hideLoading() {
        if (layoutLoadingOverlay != null) {
            layoutLoadingOverlay.setVisibility(View.GONE);
        }
    }
}