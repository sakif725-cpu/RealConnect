package com.realconnect.app;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;

public class WelcomeActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Check if user has already onboarded / logged in with their phone number or Firebase account
        SharedPreferences prefs = getSharedPreferences("ProfilePrefs", Context.MODE_PRIVATE);
        String selfPhone = prefs.getString("phone", null);
        FirebaseUser currentUser = FirebaseAuth.getInstance().getCurrentUser();

        if (selfPhone != null && !selfPhone.trim().isEmpty()) {
            // Existing user: Skip welcome and go straight to the main app
            Intent intent = new Intent(WelcomeActivity.this, MainActivity.class);
            startActivity(intent);
            finish();
            return;
        }

        // New user: Show the Welcome screen
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_welcome);

        FloatingActionButton btnStart = findViewById(R.id.btn_start_app);
        btnStart.setOnClickListener(v -> {
            Intent intent = new Intent(WelcomeActivity.this, AuthActivity.class);
            startActivity(intent);
        });
    }
}