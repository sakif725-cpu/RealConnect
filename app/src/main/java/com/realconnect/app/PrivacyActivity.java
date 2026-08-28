package com.realconnect.app;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.ImageButton;
import android.widget.TextView;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import java.util.List;

public class PrivacyActivity extends AppCompatActivity {

    private TextView textRecordingsCount;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_privacy);

        View header = findViewById(R.id.privacy_header);
        ViewCompat.setOnApplyWindowInsetsListener(header, (v, insets) -> {
            Insets statusBarInsets = insets.getInsets(WindowInsetsCompat.Type.statusBars() | WindowInsetsCompat.Type.displayCutout());
            int topOffset = statusBarInsets.top > 0 ? statusBarInsets.top : (int) (16 * getResources().getDisplayMetrics().density);
            v.setPadding(
                    v.getPaddingLeft(),
                    topOffset + (int) (4 * getResources().getDisplayMetrics().density),
                    v.getPaddingRight(),
                    (int) (12 * getResources().getDisplayMetrics().density)
            );
            return insets;
        });

        ImageButton btnBack = findViewById(R.id.btn_privacy_back);
        btnBack.setOnClickListener(v -> finish());

        // 1. Recordings (Functional)
        findViewById(R.id.card_option_recordings).setOnClickListener(v -> {
            Intent intent = new Intent(PrivacyActivity.this, RecordingsActivity.class);
            startActivity(intent);
        });

        // 2. Change Phone Number
        findViewById(R.id.card_option_change_phone).setOnClickListener(v -> {
            android.app.Dialog infoDialog = new android.app.Dialog(PrivacyActivity.this);
            infoDialog.requestWindowFeature(android.view.Window.FEATURE_NO_TITLE);
            View infoView = getLayoutInflater().inflate(R.layout.dialog_confirm_action, null);
            infoDialog.setContentView(infoView);

            android.widget.TextView textTitle = infoView.findViewById(R.id.text_confirm_title);
            android.widget.TextView textMsg = infoView.findViewById(R.id.text_confirm_message);
            android.widget.ImageView imgIcon = infoView.findViewById(R.id.img_confirm_icon);
            com.google.android.material.card.MaterialCardView iconBg = infoView.findViewById(R.id.card_confirm_icon_bg);
            com.google.android.material.button.MaterialButton btnAction = infoView.findViewById(R.id.btn_confirm_action);
            View btnCancel = infoView.findViewById(R.id.btn_confirm_cancel);

            textTitle.setText("Change Phone Number");
            textMsg.setText("Migrate your account information, chat messages, and call logs to a new mobile phone number securely.\n\nThis feature will be available in the upcoming update.");
            imgIcon.setImageResource(R.drawable.ic_call);
            imgIcon.setColorFilter(android.graphics.Color.parseColor("#0EA5E9"));
            iconBg.setCardBackgroundColor(android.graphics.Color.parseColor("#F0F9FF"));
            btnAction.setText("Got It");
            btnAction.setBackgroundColor(android.graphics.Color.parseColor("#0EA5E9"));
            btnCancel.setVisibility(View.GONE);

            btnAction.setOnClickListener(cv -> infoDialog.dismiss());

            infoDialog.show();
            if (infoDialog.getWindow() != null) {
                infoDialog.getWindow().setBackgroundDrawable(new android.graphics.drawable.ColorDrawable(android.graphics.Color.TRANSPARENT));
                infoDialog.getWindow().setLayout(
                        android.view.ViewGroup.LayoutParams.MATCH_PARENT,
                        android.view.ViewGroup.LayoutParams.WRAP_CONTENT
                );
                infoDialog.getWindow().setGravity(android.view.Gravity.CENTER);
            }
        });

        // 3. Blocked Numbers
        findViewById(R.id.card_option_blocked_numbers).setOnClickListener(v -> {
            java.util.Set<String> blockedSet = BlockedNumbersManager.getBlockedNumbers(PrivacyActivity.this);
            if (blockedSet.isEmpty()) {
                android.app.Dialog infoDialog = new android.app.Dialog(PrivacyActivity.this);
                infoDialog.requestWindowFeature(android.view.Window.FEATURE_NO_TITLE);
                View infoView = getLayoutInflater().inflate(R.layout.dialog_confirm_action, null);
                infoDialog.setContentView(infoView);

                android.widget.TextView textTitle = infoView.findViewById(R.id.text_confirm_title);
                android.widget.TextView textMsg = infoView.findViewById(R.id.text_confirm_message);
                android.widget.ImageView imgIcon = infoView.findViewById(R.id.img_confirm_icon);
                com.google.android.material.card.MaterialCardView iconBg = infoView.findViewById(R.id.card_confirm_icon_bg);
                com.google.android.material.button.MaterialButton btnAction = infoView.findViewById(R.id.btn_confirm_action);
                View btnCancel = infoView.findViewById(R.id.btn_confirm_cancel);

                textTitle.setText("Blocked Numbers");
                textMsg.setText("You have not blocked any numbers yet.\n\nTo block a contact or caller, long-press on any item in the Contacts, Calls, or Messages tab.");
                imgIcon.setImageResource(R.drawable.ic_block);
                imgIcon.setColorFilter(android.graphics.Color.parseColor("#D97706"));
                iconBg.setCardBackgroundColor(android.graphics.Color.parseColor("#FFFBEB"));
                btnAction.setText("Got It");
                btnAction.setBackgroundColor(android.graphics.Color.parseColor("#D97706"));
                btnCancel.setVisibility(View.GONE);

                btnAction.setOnClickListener(cv -> infoDialog.dismiss());

                infoDialog.show();
                if (infoDialog.getWindow() != null) {
                    infoDialog.getWindow().setBackgroundDrawable(new android.graphics.drawable.ColorDrawable(android.graphics.Color.TRANSPARENT));
                    infoDialog.getWindow().setLayout(
                            android.view.ViewGroup.LayoutParams.MATCH_PARENT,
                            android.view.ViewGroup.LayoutParams.WRAP_CONTENT
                    );
                    infoDialog.getWindow().setGravity(android.view.Gravity.CENTER);
                }
            } else {
                String[] blockedArr = blockedSet.toArray(new String[0]);
                new AlertDialog.Builder(PrivacyActivity.this)
                        .setTitle("Blocked Numbers (" + blockedArr.length + ")")
                        .setItems(blockedArr, (dialog, which) -> {
                            String selected = blockedArr[which];
                            android.app.Dialog unblockDialog = new android.app.Dialog(PrivacyActivity.this);
                            unblockDialog.requestWindowFeature(android.view.Window.FEATURE_NO_TITLE);
                            View unblockView = getLayoutInflater().inflate(R.layout.dialog_confirm_action, null);
                            unblockDialog.setContentView(unblockView);

                            android.widget.TextView textTitle = unblockView.findViewById(R.id.text_confirm_title);
                            android.widget.TextView textMsg = unblockView.findViewById(R.id.text_confirm_message);
                            android.widget.ImageView imgIcon = unblockView.findViewById(R.id.img_confirm_icon);
                            com.google.android.material.card.MaterialCardView iconBg = unblockView.findViewById(R.id.card_confirm_icon_bg);
                            com.google.android.material.button.MaterialButton btnAction = unblockView.findViewById(R.id.btn_confirm_action);
                            View btnCancel = unblockView.findViewById(R.id.btn_confirm_cancel);

                            textTitle.setText("Unblock Number");
                            textMsg.setText("Allow incoming calls and messages from " + selected + "?");
                            imgIcon.setImageResource(R.drawable.ic_contacts);
                            imgIcon.setColorFilter(android.graphics.Color.parseColor("#10B981"));
                            iconBg.setCardBackgroundColor(android.graphics.Color.parseColor("#ECFDF5"));
                            btnAction.setText("Unblock");
                            btnAction.setBackgroundColor(android.graphics.Color.parseColor("#10B981"));

                            btnCancel.setOnClickListener(cv -> unblockDialog.dismiss());
                            btnAction.setOnClickListener(cv -> {
                                unblockDialog.dismiss();
                                BlockedNumbersManager.unblockNumber(PrivacyActivity.this, selected);
                                android.widget.Toast.makeText(PrivacyActivity.this, "Unblocked " + selected, android.widget.Toast.LENGTH_SHORT).show();
                            });

                            unblockDialog.show();
                            if (unblockDialog.getWindow() != null) {
                                unblockDialog.getWindow().setBackgroundDrawable(new android.graphics.drawable.ColorDrawable(android.graphics.Color.TRANSPARENT));
                                unblockDialog.getWindow().setLayout(
                                        android.view.ViewGroup.LayoutParams.MATCH_PARENT,
                                        android.view.ViewGroup.LayoutParams.WRAP_CONTENT
                                );
                                unblockDialog.getWindow().setGravity(android.view.Gravity.CENTER);
                            }
                        })
                        .setPositiveButton("Close", null)
                        .show();
            }
        });

        // 4. Magic Settings
        findViewById(R.id.card_option_magic).setOnClickListener(v -> {
            android.app.Dialog magicDialog = new android.app.Dialog(PrivacyActivity.this);
            magicDialog.requestWindowFeature(android.view.Window.FEATURE_NO_TITLE);
            View magicView = getLayoutInflater().inflate(R.layout.dialog_magic_settings, null);
            magicDialog.setContentView(magicView);

            com.google.android.material.switchmaterial.SwitchMaterial switchWhip = magicView.findViewById(R.id.switch_whip_enabled);
            View btnDone = magicView.findViewById(R.id.btn_magic_done);

            switchWhip.setChecked(WhipEffectManager.isWhipEnabled(PrivacyActivity.this));
            switchWhip.setOnCheckedChangeListener((buttonView, isChecked) -> {
                WhipEffectManager.setWhipEnabled(PrivacyActivity.this, isChecked);
            });

            btnDone.setOnClickListener(cv -> magicDialog.dismiss());

            magicDialog.show();
            if (magicDialog.getWindow() != null) {
                magicDialog.getWindow().setBackgroundDrawable(new android.graphics.drawable.ColorDrawable(android.graphics.Color.TRANSPARENT));
                magicDialog.getWindow().setLayout(
                        android.view.ViewGroup.LayoutParams.MATCH_PARENT,
                        android.view.ViewGroup.LayoutParams.WRAP_CONTENT
                );
                magicDialog.getWindow().setGravity(android.view.Gravity.CENTER);
            }
        });
    }
}