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
    private ActivityResultLauncher<String[]> imagePickerLauncher;
    private SharedPreferences sharedPreferences;

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        sharedPreferences = requireContext().getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        
        // Use OpenDocument to allow for persistable URI permissions
        imagePickerLauncher = registerForActivityResult(
                new ActivityResultContracts.OpenDocument(),
                uri -> {
                    if (uri != null && profileImage != null) {
                        try {
                            // Request permanent access to this file
                            requireContext().getContentResolver().takePersistableUriPermission(
                                    uri, Intent.FLAG_GRANT_READ_URI_PERMISSION);
                            
                            updateProfileImageUI(uri);
                            sharedPreferences.edit().putString(KEY_IMAGE_URI, uri.toString()).apply();
                            Toast.makeText(getContext(), R.string.msg_profile_photo_updated, Toast.LENGTH_SHORT).show();
                        } catch (Exception e) {
                            e.printStackTrace();
                            updateProfileImageUI(null);
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
                imagePickerLauncher.launch(new String[]{"image/*"});
            });
        }

        setupOption(view.findViewById(R.id.option_account), R.drawable.ic_profile, R.string.option_account, v -> showEditProfileDialog());
        setupOption(view.findViewById(R.id.option_notifications), R.drawable.ic_notifications, R.string.option_notifications, null);
        setupOption(view.findViewById(R.id.option_privacy), R.drawable.ic_privacy, R.string.option_privacy, null);

        view.findViewById(R.id.btn_logout).setOnClickListener(v -> {
            Toast.makeText(getContext(), R.string.logout, Toast.LENGTH_SHORT).show();
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
        View dialogView = LayoutInflater.from(getContext()).inflate(R.layout.dialog_edit_profile, null);
        TextInputEditText editName = dialogView.findViewById(R.id.edit_name);
        TextInputEditText editPhone = dialogView.findViewById(R.id.edit_phone);
        TextInputEditText editEmail = dialogView.findViewById(R.id.edit_email);

        // Pre-fill with current values
        editName.setText(profileName.getText());
        editPhone.setText(profilePhone.getText());
        editEmail.setText(profileEmail.getText());

        new AlertDialog.Builder(requireContext())
                .setTitle(R.string.dialog_edit_profile_title)
                .setView(dialogView)
                .setPositiveButton(R.string.action_save, (dialog, which) -> {
                    String name = editName.getText().toString().trim();
                    String phone = editPhone.getText().toString().trim();
                    String email = editEmail.getText().toString().trim();

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

                        Toast.makeText(getContext(), R.string.msg_profile_updated, Toast.LENGTH_SHORT).show();
                    }
                })
                .setNegativeButton(R.string.action_cancel, null)
                .show();
    }
}
