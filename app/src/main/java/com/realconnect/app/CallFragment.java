package com.realconnect.app;

import android.content.Intent;
import android.media.AudioManager;
import android.media.ToneGenerator;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.TextView;
import android.widget.Toast;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

public class CallFragment extends Fragment {

    private TextView textPhoneNumber;
    private TextView textContactName;
    private ImageButton btnAddToContacts;
    private StringBuilder phoneNumber = new StringBuilder();
    private ToneGenerator toneGenerator;

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        try {
            toneGenerator = new ToneGenerator(AudioManager.STREAM_DTMF, 80);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_call, container, false);

        textPhoneNumber = view.findViewById(R.id.text_phone_number);
        textContactName = view.findViewById(R.id.text_contact_name);
        btnAddToContacts = view.findViewById(R.id.btn_add_to_contacts);

        // Setup dial pad click listeners
        int[] buttonIds = {
                R.id.btn_0, R.id.btn_1, R.id.btn_2, R.id.btn_3, R.id.btn_4,
                R.id.btn_5, R.id.btn_6, R.id.btn_7, R.id.btn_8, R.id.btn_9,
                R.id.btn_star, R.id.btn_hash
        };

        View.OnClickListener dialListener = v -> {
            if (v instanceof Button) {
                String digit = ((Button) v).getText().toString();
                phoneNumber.append(digit);
                playTone(digit);
                updateUi();
            }
        };

        for (int id : buttonIds) {
            view.findViewById(id).setOnClickListener(dialListener);
        }

        view.findViewById(R.id.btn_delete).setOnClickListener(v -> {
            if (phoneNumber.length() > 0) {
                phoneNumber.deleteCharAt(phoneNumber.length() - 1);
                updateUi();
            }
        });

        btnAddToContacts.setOnClickListener(v -> showAddContactDialog(phoneNumber.toString()));

        view.findViewById(R.id.btn_make_call).setOnClickListener(v -> {
            String number = phoneNumber.toString();
            if (!number.isEmpty()) {
                initiateCall(number);
            }
        });

        return view;
    }

    private void playTone(String digit) {
        if (toneGenerator == null) return;
        
        int tone = -1;
        switch (digit) {
            case "0": tone = ToneGenerator.TONE_DTMF_0; break;
            case "1": tone = ToneGenerator.TONE_DTMF_1; break;
            case "2": tone = ToneGenerator.TONE_DTMF_2; break;
            case "3": tone = ToneGenerator.TONE_DTMF_3; break;
            case "4": tone = ToneGenerator.TONE_DTMF_4; break;
            case "5": tone = ToneGenerator.TONE_DTMF_5; break;
            case "6": tone = ToneGenerator.TONE_DTMF_6; break;
            case "7": tone = ToneGenerator.TONE_DTMF_7; break;
            case "8": tone = ToneGenerator.TONE_DTMF_8; break;
            case "9": tone = ToneGenerator.TONE_DTMF_9; break;
            case "*": tone = ToneGenerator.TONE_DTMF_S; break;
            case "#": tone = ToneGenerator.TONE_DTMF_P; break;
        }
        
        if (tone != -1) {
            toneGenerator.startTone(tone, 150);
        }
    }

    private void updateUi() {
        String number = phoneNumber.toString();
        textPhoneNumber.setText(number);
        
        String contactName = ContactRepository.getInstance(requireContext()).findContactByNumber(number);
        if (contactName != null) {
            textContactName.setText(contactName);
            btnAddToContacts.setVisibility(View.GONE);
        } else {
            textContactName.setText("");
            btnAddToContacts.setVisibility(number.length() > 0 ? View.VISIBLE : View.INVISIBLE);
        }
    }

    private void initiateCall(String number) {
        String contactName = ContactRepository.getInstance(requireContext()).findContactByNumber(number);
        Intent intent = new Intent(getActivity(), CallingActivity.class);
        intent.putExtra("CONTACT_NAME", contactName != null ? contactName : number);
        intent.putExtra("CONTACT_PHONE", number);
        startActivity(intent);
    }

    private void showAddContactDialog(String number) {
        View dialogView = LayoutInflater.from(getContext()).inflate(R.layout.dialog_add_contact, null);
        EditText editName = dialogView.findViewById(R.id.edit_name);
        EditText editPhone = dialogView.findViewById(R.id.edit_phone);

        editPhone.setText(number);

        new MaterialAlertDialogBuilder(requireContext())
                .setTitle(R.string.dialog_add_contact_title)
                .setView(dialogView)
                .setPositiveButton(R.string.action_add, (dialog, which) -> {
                    String name = editName.getText().toString().trim();
                    String phone = editPhone.getText().toString().trim();

                    if (!TextUtils.isEmpty(name) && !TextUtils.isEmpty(phone)) {
                        ContactRepository.getInstance(requireContext()).addContact(new Contact(name, phone));
                        Toast.makeText(getContext(), "Contact saved", Toast.LENGTH_SHORT).show();
                        updateUi();
                    } else {
                        Toast.makeText(getContext(), R.string.error_empty_fields, Toast.LENGTH_SHORT).show();
                    }
                })
                .setNegativeButton(R.string.action_cancel, null)
                .show();
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        if (toneGenerator != null) {
            toneGenerator.release();
        }
    }
}
