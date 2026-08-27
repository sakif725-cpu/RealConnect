package com.realconnect.app;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.View;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;
import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import com.google.android.material.button.MaterialButton;

public class AuthActivity extends AppCompatActivity {

    private LinearLayout layoutStepPhone;
    private LinearLayout layoutStepOtp;
    private EditText editPhone;
    private EditText editOtp;
    private TextView textOtpSubtitle;

    private String enteredPhone = "";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_auth);

        layoutStepPhone = findViewById(R.id.layout_step_phone);
        layoutStepOtp = findViewById(R.id.layout_step_otp);
        editPhone = findViewById(R.id.edit_phone_input);
        editOtp = findViewById(R.id.edit_otp_input);
        textOtpSubtitle = findViewById(R.id.text_otp_subtitle);

        MaterialButton btnSendOtp = findViewById(R.id.btn_send_otp);
        MaterialButton btnVerifyOtp = findViewById(R.id.btn_verify_otp);
        TextView btnChangeNumber = findViewById(R.id.btn_change_number);
        ImageButton btnBack = findViewById(R.id.btn_auth_back);

        btnSendOtp.setOnClickListener(v -> handleSendOtp());
        btnVerifyOtp.setOnClickListener(v -> handleVerifyOtp());
        btnChangeNumber.setOnClickListener(v -> showPhoneStep());

        btnBack.setOnClickListener(v -> {
            if (layoutStepOtp.getVisibility() == View.VISIBLE) {
                showPhoneStep();
            } else {
                finish();
            }
        });
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
        layoutStepOtp.setVisibility(View.VISIBLE);
        textOtpSubtitle.setText("Enter the verification code sent to " + enteredPhone);
        editOtp.setText("");
        editOtp.requestFocus();
    }

    private void showPhoneStep() {
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

        // Dummy Verification: Accepts any entered OTP code
        saveUserProfile(enteredPhone);

        Toast.makeText(this, "Verified successfully!", Toast.LENGTH_SHORT).show();

        Intent intent = new Intent(AuthActivity.this, MainActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);
        finish();
    }

    private void saveUserProfile(String phone) {
        SharedPreferences prefs = getSharedPreferences("ProfilePrefs", Context.MODE_PRIVATE);
        SharedPreferences.Editor editor = prefs.edit();
        editor.putString("phone", phone);
        if (!prefs.contains("name")) {
            editor.putString("name", "User " + (phone.length() > 4 ? phone.substring(phone.length() - 4) : phone));
        }
        editor.apply();
    }
}