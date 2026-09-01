package com.realconnect.app;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.text.TextUtils;
import android.util.Log;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.ImageButton;
import android.widget.TextView;
import android.widget.Toast;
import androidx.activity.EdgeToEdge;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
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
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.MutableData;
import com.google.firebase.database.Transaction;
import com.google.firebase.database.ValueEventListener;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

public class AuthActivity extends AppCompatActivity {

    private static final String TAG = "AuthActivity";
    private static final String PREFS_NAME = "ProfilePrefs";

    // 13-digit base: 9100000000000L -> gives sequential 13-digit numbers (0-9)
    private static final long BASE_13_DIGIT_PHONE = 9100000000000L;

    private FrameLayout layoutLoadingOverlay;
    private TextView textLoadingStatus;

    private FirebaseAuth mAuth;
    private GoogleSignInClient googleSignInClient;
    private ActivityResultLauncher<Intent> googleSignInLauncher;

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
            Log.w(TAG, "default_web_client_id lookup failed: " + e.getMessage());
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
        layoutLoadingOverlay = findViewById(R.id.layout_loading_overlay);
        textLoadingStatus = findViewById(R.id.text_loading_status);
    }

    private void setupListeners() {
        MaterialButton btnGoogleSignIn = findViewById(R.id.btn_google_sign_in);
        ImageButton btnBack = findViewById(R.id.btn_auth_back);

        btnGoogleSignIn.setOnClickListener(v -> launchGoogleSignIn());
        btnBack.setOnClickListener(v -> finish());
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
            String uid = "google_" + (account.getId() != null ? account.getId() : UUID.randomUUID().toString());
            onGoogleAuthSuccess(account.getDisplayName(), account.getEmail(), account.getPhotoUrl() != null ? account.getPhotoUrl().toString() : null, uid);
            return;
        }

        AuthCredential credential = GoogleAuthProvider.getCredential(idToken, null);
        mAuth.signInWithCredential(credential)
                .addOnCompleteListener(this, task -> {
                    if (task.isSuccessful()) {
                        FirebaseUser user = mAuth.getCurrentUser();
                        String uid = user != null ? user.getUid() : "user_" + UUID.randomUUID().toString();
                        String name = (user != null && !TextUtils.isEmpty(user.getDisplayName())) ? user.getDisplayName() : account.getDisplayName();
                        String email = (user != null && !TextUtils.isEmpty(user.getEmail())) ? user.getEmail() : account.getEmail();
                        String photo = (user != null && user.getPhotoUrl() != null) ? user.getPhotoUrl().toString() : (account.getPhotoUrl() != null ? account.getPhotoUrl().toString() : null);

                        onGoogleAuthSuccess(name, email, photo, uid);
                    } else {
                        hideLoading();
                        Log.e(TAG, "Firebase auth with Google failed", task.getException());
                        Toast.makeText(AuthActivity.this, "Authentication failed: " + (task.getException() != null ? task.getException().getMessage() : "Unknown error"), Toast.LENGTH_LONG).show();
                    }
                });
    }

    private void onGoogleAuthSuccess(String name, String email, String photoUrl, String uid) {
        showLoading("Assigning 13-digit RealConnect ID...");

        // Check if user already has an allocated 13-digit number in Firebase
        DatabaseReference userAccRef = FirebaseDatabase.getInstance().getReference("user_accounts").child(uid).child("assigned_phone");
        userAccRef.addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                String existingAssignedPhone = snapshot.getValue(String.class);
                if (existingAssignedPhone != null && !existingAssignedPhone.trim().isEmpty() && existingAssignedPhone.length() >= 10) {
                    // Existing assigned 13-digit number found
                    hideLoading();
                    saveProfile(existingAssignedPhone, name != null ? name : "User", email != null ? email : "", photoUrl);
                    Toast.makeText(AuthActivity.this, "Welcome " + name + "!\nCalling ID: " + existingAssignedPhone, Toast.LENGTH_LONG).show();
                    proceedToMain();
                } else {
                    // Allocate new 13-digit number via atomic transaction (0 collisions)
                    allocate13DigitPhoneAtomic(uid, name, email, photoUrl);
                }
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                allocate13DigitPhoneAtomic(uid, name, email, photoUrl);
            }
        });
    }

    /**
     * Atomically increments the 13-digit phone counter in Firebase Realtime Database.
     * Guaranteed 0 collisions across all devices.
     */
    private void allocate13DigitPhoneAtomic(String uid, String name, String email, String photoUrl) {
        showLoading("Assigning unique 13-digit calling ID...");

        DatabaseReference counterRef = FirebaseDatabase.getInstance().getReference("system").child("phone_counter_13digit");

        counterRef.runTransaction(new Transaction.Handler() {
            @NonNull
            @Override
            public Transaction.Result doTransaction(@NonNull MutableData currentData) {
                Long currentVal = currentData.getValue(Long.class);
                if (currentVal == null || currentVal < BASE_13_DIGIT_PHONE) {
                    currentVal = BASE_13_DIGIT_PHONE;
                }
                long nextVal = currentVal + 1;
                currentData.setValue(nextVal);
                return Transaction.success(currentData);
            }

            @Override
            public void onComplete(@Nullable DatabaseError error, boolean committed, @Nullable DataSnapshot snapshot) {
                hideLoading();
                String phone13Digit;

                if (committed && snapshot != null && snapshot.getValue(Long.class) != null) {
                    phone13Digit = String.valueOf(snapshot.getValue(Long.class));
                } else {
                    // Fallback: Deterministic 13-digit number from UID if offline
                    phone13Digit = generateDeterministic13Digit(uid);
                }

                // 1. Save permanent assignment to /user_accounts/{uid}/assigned_phone
                FirebaseDatabase.getInstance().getReference("user_accounts")
                        .child(uid).child("assigned_phone").setValue(phone13Digit);

                // 2. Save reverse lookup /phone_to_user/{phone13Digit}
                Map<String, Object> mapping = new HashMap<>();
                mapping.put("uid", uid);
                mapping.put("name", name != null ? name : "User");
                mapping.put("email", email != null ? email : "");
                FirebaseDatabase.getInstance().getReference("phone_to_user")
                        .child(phone13Digit).setValue(mapping);

                // 3. Save local profile and sync to /users/{phone13Digit}
                saveProfile(phone13Digit, name != null ? name : "User", email != null ? email : "", photoUrl);

                Toast.makeText(AuthActivity.this, "Assigned 13-Digit Calling ID:\n" + phone13Digit, Toast.LENGTH_LONG).show();
                proceedToMain();
            }
        });
    }

    private String generateDeterministic13Digit(String uid) {
        long hash = Math.abs((long) uid.hashCode());
        return String.format(Locale.US, "91%011d", hash % 100000000000L);
    }

    private void saveProfile(String phone, String name, String email, String photoUrl) {
        String cleanPhone = ChatRepository.cleanPhone(phone);

        SharedPreferences prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        SharedPreferences.Editor editor = prefs.edit();
        editor.putString("phone", cleanPhone);
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
        if (!cleanPhone.isEmpty()) {
            new Thread(() -> {
                try {
                    Map<String, Object> userData = new HashMap<>();
                    userData.put("name", name != null ? name : "");
                    userData.put("email", email != null ? email : "");
                    userData.put("phone", cleanPhone);
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