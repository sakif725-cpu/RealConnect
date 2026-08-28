package com.realconnect.app;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.Fragment;
import com.google.android.material.textfield.TextInputEditText;

public class ProfileFragment extends Fragment {

    private static final String PREFS_NAME = "ProfilePrefs";
    private static final String KEY_NAME = "name";
    private static final String KEY_PHONE = "phone";
    private static final String KEY_EMAIL = "email";
    private static final String KEY_IMAGE_URI = "image_uri";

    private ImageView profileImage;
    private TextView profileName, profilePhone, profileEmail;
    private ActivityResultLauncher<String> imagePickerLauncher;
    private SharedPreferences sharedPreferences;

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        sharedPreferences = requireContext().getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        
        // Save picked image directly into internal app storage for 100% Android 12-16 compatibility
        imagePickerLauncher = registerForActivityResult(
                new ActivityResultContracts.GetContent(),
                uri -> {
                    if (uri != null && profileImage != null) {
                        try {
                            java.io.File avatarFile = new java.io.File(requireContext().getFilesDir(), "profile_avatar.jpg");
                            try (java.io.InputStream in = requireContext().getContentResolver().openInputStream(uri);
                                 java.io.OutputStream out = new java.io.FileOutputStream(avatarFile)) {
                                byte[] buffer = new byte[8192];
                                int len;
                                while ((len = in.read(buffer)) != -1) {
                                    out.write(buffer, 0, len);
                                }
                            }

                            Uri localUri = Uri.fromFile(avatarFile);
                            updateProfileImageUI(localUri);
                            sharedPreferences.edit().putString(KEY_IMAGE_URI, localUri.toString()).apply();
                            Toast.makeText(getContext(), R.string.msg_profile_photo_updated, Toast.LENGTH_SHORT).show();

                            String selfPhone = sharedPreferences.getString(KEY_PHONE, "");
                            String cleanPhone = ChatRepository.cleanPhone(selfPhone);
                            if (!cleanPhone.isEmpty()) {
                                new Thread(() -> {
                                    try {
                                        String base64 = ImageUtils.uriToBase64(requireContext(), localUri, 240);
                                        if (base64 != null) {
                                            com.google.firebase.database.FirebaseDatabase.getInstance().getReference("users")
                                                    .child(cleanPhone)
                                                    .child("profileImageBase64")
                                                    .setValue(base64);
                                        }
                                    } catch (Exception ignored) {}
                                }).start();
                            }
                        } catch (Exception e) {
                            android.util.Log.e("ProfileFragment", "Error saving profile avatar", e);
                        }
                    }
                }
        );
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_profile, container, false);
        
        profileImage = view.findViewById(R.id.profile_image);
        profileName = view.findViewById(R.id.profile_name);
        profilePhone = view.findViewById(R.id.profile_phone);
        profileEmail = view.findViewById(R.id.profile_email);
        
        loadProfileData();

        View profileImageContainer = view.findViewById(R.id.profile_image_container);
        if (profileImageContainer != null) {
            profileImageContainer.setOnClickListener(v -> {
                imagePickerLauncher.launch("image/*");
            });
        }

        setupOption(view.findViewById(R.id.option_account), R.drawable.ic_profile, R.string.option_account, v -> showEditProfileDialog());
        setupOption(view.findViewById(R.id.option_notifications), R.drawable.ic_notifications, R.string.option_notifications, null);
        setupOption(view.findViewById(R.id.option_privacy), R.drawable.ic_privacy, R.string.option_privacy, v -> {
            Intent intent = new Intent(getActivity(), PrivacyActivity.class);
            startActivity(intent);
        });

        view.findViewById(R.id.btn_logout).setOnClickListener(v -> {
            android.app.Dialog confirmDialog = new android.app.Dialog(requireContext());
            confirmDialog.requestWindowFeature(android.view.Window.FEATURE_NO_TITLE);
            View confirmView = LayoutInflater.from(getContext()).inflate(R.layout.dialog_confirm_action, null);
            confirmDialog.setContentView(confirmView);

            android.widget.TextView textTitle = confirmView.findViewById(R.id.text_confirm_title);
            android.widget.TextView textMsg = confirmView.findViewById(R.id.text_confirm_message);
            android.widget.ImageView imgIcon = confirmView.findViewById(R.id.img_confirm_icon);
            com.google.android.material.card.MaterialCardView iconBg = confirmView.findViewById(R.id.card_confirm_icon_bg);
            com.google.android.material.button.MaterialButton btnAction = confirmView.findViewById(R.id.btn_confirm_action);
            View btnCancel = confirmView.findViewById(R.id.btn_confirm_cancel);

            textTitle.setText("Log Out");
            textMsg.setText("Are you sure you want to log out of RealConnect?");
            imgIcon.setImageResource(R.drawable.ic_block);
            imgIcon.setColorFilter(android.graphics.Color.parseColor("#EF4444"));
            iconBg.setCardBackgroundColor(android.graphics.Color.parseColor("#FEF2F2"));
            btnAction.setText("Log Out");
            btnAction.setBackgroundColor(android.graphics.Color.parseColor("#EF4444"));

            btnCancel.setOnClickListener(cv -> confirmDialog.dismiss());
            btnAction.setOnClickListener(cv -> {
                confirmDialog.dismiss();
                sharedPreferences.edit().clear().apply();
                Intent intent = new Intent(getActivity(), WelcomeActivity.class);
                intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
                startActivity(intent);
            });

            confirmDialog.show();
            if (confirmDialog.getWindow() != null) {
                confirmDialog.getWindow().setBackgroundDrawable(new android.graphics.drawable.ColorDrawable(android.graphics.Color.TRANSPARENT));
                confirmDialog.getWindow().setLayout(
                        android.view.ViewGroup.LayoutParams.MATCH_PARENT,
                        android.view.ViewGroup.LayoutParams.WRAP_CONTENT
                );
                confirmDialog.getWindow().setGravity(android.view.Gravity.CENTER);
            }
        });
        
        return view;
    }

    private void loadProfileData() {
        String name = sharedPreferences.getString(KEY_NAME, getString(R.string.profile_name));
        String phone = sharedPreferences.getString(KEY_PHONE, getString(R.string.profile_phone));
        String email = sharedPreferences.getString(KEY_EMAIL, getString(R.string.profile_email));
        String imageUriStr = sharedPreferences.getString(KEY_IMAGE_URI, null);

        profileName.setText(name);
        profilePhone.setText(phone);
        profileEmail.setText(email);

        if (imageUriStr != null) {
            updateProfileImageUI(Uri.parse(imageUriStr));
        }
    }

    private void updateProfileImageUI(Uri uri) {
        if (profileImage == null) return;
        
        if (uri == null) {
            profileImage.setImageResource(R.drawable.ic_contacts);
            return;
        }

        try {
            profileImage.setPadding(0, 0, 0, 0);
            profileImage.setBackground(null);
            profileImage.setImageTintList(null);
            profileImage.setColorFilter(null);
            profileImage.setImageURI(uri);
        } catch (SecurityException e) {
            // Permission lost, revert to default
            profileImage.setImageResource(R.drawable.ic_contacts);
            sharedPreferences.edit().remove(KEY_IMAGE_URI).apply();
        }
    }
    
    private void setupOption(View view, int iconRes, int titleRes, View.OnClickListener customListener) {
        if (view == null) return;

        ImageView icon = view.findViewById(R.id.option_icon);
        TextView title = view.findViewById(R.id.option_title);
        
        if (icon != null) icon.setImageResource(iconRes);
        if (title != null) title.setText(titleRes);
        
        if (customListener != null) {
            view.setOnClickListener(customListener);
        } else {
            view.setOnClickListener(v -> {
                Toast.makeText(getContext(), titleRes, Toast.LENGTH_SHORT).show();
            });
        }
    }

    private void showEditProfileDialog() {
        android.app.Dialog formDialog = new android.app.Dialog(requireContext());
        formDialog.requestWindowFeature(android.view.Window.FEATURE_NO_TITLE);
        View dialogView = LayoutInflater.from(getContext()).inflate(R.layout.dialog_profile_form, null);
        formDialog.setContentView(dialogView);

        com.google.android.material.textfield.TextInputEditText editName = dialogView.findViewById(R.id.edit_profile_form_name);
        com.google.android.material.textfield.TextInputEditText editPhone = dialogView.findViewById(R.id.edit_profile_form_phone);
        com.google.android.material.textfield.TextInputEditText editEmail = dialogView.findViewById(R.id.edit_profile_form_email);
        View btnCancel = dialogView.findViewById(R.id.btn_profile_form_cancel);
        com.google.android.material.button.MaterialButton btnSubmit = dialogView.findViewById(R.id.btn_profile_form_submit);

        // Pre-fill with current values
        editName.setText(profileName.getText());
        editPhone.setText(profilePhone.getText());
        editEmail.setText(profileEmail.getText());

        btnCancel.setOnClickListener(v -> formDialog.dismiss());
        btnSubmit.setOnClickListener(v -> {
            String name = editName.getText() != null ? editName.getText().toString().trim() : "";
            String phone = editPhone.getText() != null ? editPhone.getText().toString().trim() : "";
            String email = editEmail.getText() != null ? editEmail.getText().toString().trim() : "";

            if (name.isEmpty() || phone.isEmpty() || email.isEmpty()) {
                Toast.makeText(getContext(), R.string.error_empty_fields, Toast.LENGTH_SHORT).show();
            } else {
                profileName.setText(name);
                profilePhone.setText(phone);
                profileEmail.setText(email);

                sharedPreferences.edit()
                        .putString(KEY_NAME, name)
                        .putString(KEY_PHONE, phone)
                        .putString(KEY_EMAIL, email)
                        .apply();

                formDialog.dismiss();
                Toast.makeText(getContext(), R.string.msg_profile_updated, Toast.LENGTH_SHORT).show();
            }
        });

        formDialog.show();
        if (formDialog.getWindow() != null) {
            formDialog.getWindow().setBackgroundDrawable(new android.graphics.drawable.ColorDrawable(android.graphics.Color.TRANSPARENT));
            formDialog.getWindow().setLayout(
                    android.view.ViewGroup.LayoutParams.MATCH_PARENT,
                    android.view.ViewGroup.LayoutParams.WRAP_CONTENT
            );
            formDialog.getWindow().setGravity(android.view.Gravity.CENTER);
        }
    }
}
