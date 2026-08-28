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
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.floatingactionbutton.FloatingActionButton;
import java.util.List;

public class CallFragment extends Fragment {

    private View layoutCallLogs;
    private View layoutDialer;
    private View layoutEmptyLogs;
    private TextView btnClearLogs;

    private RecyclerView recyclerCallLogs;
    private CallLogAdapter callLogAdapter;
    private CallLogRepository.OnCallLogsChangedListener callLogsListener;

    private TextView textPhoneNumber;
    private TextView textContactName;
    private ImageButton btnDelete;
    private ImageButton btnAddToContacts;
    private final StringBuilder phoneNumber = new StringBuilder();
    private ToneGenerator toneGenerator;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_call, container, false);

        try {
            toneGenerator = new ToneGenerator(AudioManager.STREAM_DTMF, 80);
        } catch (Exception ignored) {}

        initCallLogsView(view);
        initDialerView(view);

        return view;
    }

    private void initCallLogsView(View view) {
        layoutCallLogs = view.findViewById(R.id.layout_call_logs_container);
        layoutEmptyLogs = view.findViewById(R.id.layout_empty_call_logs);
        btnClearLogs = view.findViewById(R.id.btn_clear_logs);
        recyclerCallLogs = view.findViewById(R.id.recycler_call_logs);
        recyclerCallLogs.setLayoutManager(new LinearLayoutManager(getContext()));

        callLogAdapter = new CallLogAdapter(new CallLogAdapter.OnCallLogActionListener() {
            @Override
            public void onCall(String phone, String name) {
                initiateCall(phone, name);
            }

            @Override
            public void onLongClick(CallLogAdapter.GroupedCallLog group) {
                showCallLogOptions(group);
            }
        });
        recyclerCallLogs.setAdapter(callLogAdapter);

        FloatingActionButton btnOpenDialpad = view.findViewById(R.id.btn_open_dialpad);
        btnOpenDialpad.setOnClickListener(v -> showDialerView());

        btnClearLogs.setOnClickListener(v -> {
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

            textTitle.setText("Clear History");
            textMsg.setText("Are you sure you want to clear all call logs from your history?");
            imgIcon.setImageResource(R.drawable.ic_delete);
            imgIcon.setColorFilter(android.graphics.Color.parseColor("#EF4444"));
            iconBg.setCardBackgroundColor(android.graphics.Color.parseColor("#FEF2F2"));
            btnAction.setText("Clear All");
            btnAction.setBackgroundColor(android.graphics.Color.parseColor("#EF4444"));

            btnCancel.setOnClickListener(cv -> confirmDialog.dismiss());
            btnAction.setOnClickListener(cv -> {
                confirmDialog.dismiss();
                CallLogRepository.getInstance(requireContext()).clearCallLogs();
                loadCallLogs();
                Toast.makeText(getContext(), "Call history cleared", Toast.LENGTH_SHORT).show();
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

        callLogsListener = () -> {
            if (isAdded() && getActivity() != null) {
                getActivity().runOnUiThread(this::loadCallLogs);
            }
        };
        CallLogRepository.getInstance(requireContext()).addListener(callLogsListener);
    }

    private void initDialerView(View view) {
        layoutDialer = view.findViewById(R.id.layout_dialer_container);
        textPhoneNumber = view.findViewById(R.id.text_phone_number);
        textContactName = view.findViewById(R.id.text_contact_name);
        btnDelete = view.findViewById(R.id.btn_delete);
        btnAddToContacts = view.findViewById(R.id.btn_add_to_contacts);
        FloatingActionButton btnMakeCall = view.findViewById(R.id.btn_make_call);
        ImageButton btnCloseDialpad = view.findViewById(R.id.btn_close_dialpad);

        btnCloseDialpad.setOnClickListener(v -> showCallLogsView());

        int[] buttonIds = {
                R.id.btn_0, R.id.btn_1, R.id.btn_2, R.id.btn_3,
                R.id.btn_4, R.id.btn_5, R.id.btn_6, R.id.btn_7,
                R.id.btn_8, R.id.btn_9, R.id.btn_star, R.id.btn_hash
        };

        for (int id : buttonIds) {
            Button button = view.findViewById(id);
            if (button != null) {
                button.setOnClickListener(v -> {
                    String digit = button.getText().toString();
                    appendDigit(digit);
                    playTone(digit);
                });
            }
        }

        btnDelete.setOnClickListener(v -> deleteDigit());
        btnDelete.setOnLongClickListener(v -> {
            clearDigits();
            return true;
        });

        btnAddToContacts.setOnClickListener(v -> showAddContactDialog(phoneNumber.toString()));

        btnMakeCall.setOnClickListener(v -> {
            String number = phoneNumber.toString();
            if (!TextUtils.isEmpty(number)) {
                initiateCall(number, null);
            } else {
                Toast.makeText(getContext(), "Enter a number to call", Toast.LENGTH_SHORT).show();
            }
        });
    }

    @Override
    public void onResume() {
        super.onResume();
        loadCallLogs();
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        if (callLogsListener != null && getContext() != null) {
            CallLogRepository.getInstance(requireContext()).removeListener(callLogsListener);
        }
    }

    private void showDialerView() {
        layoutCallLogs.setVisibility(View.GONE);
        layoutDialer.setVisibility(View.VISIBLE);
    }

    private void showCallLogsView() {
        layoutDialer.setVisibility(View.GONE);
        layoutCallLogs.setVisibility(View.VISIBLE);
        loadCallLogs();
    }

    private void loadCallLogs() {
        if (!isAdded() || getContext() == null) return;
        List<CallLogEntry> logs = CallLogRepository.getInstance(requireContext()).getCallLogs();
        if (logs == null || logs.isEmpty()) {
            layoutEmptyLogs.setVisibility(View.VISIBLE);
            btnClearLogs.setVisibility(View.GONE);
            callLogAdapter.setCallLogs(null);
        } else {
            layoutEmptyLogs.setVisibility(View.GONE);
            btnClearLogs.setVisibility(View.VISIBLE);
            callLogAdapter.setCallLogs(logs);
        }
    }

    private void appendDigit(String digit) {
        phoneNumber.append(digit);
        updateDialerUi();
    }

    private void deleteDigit() {
        if (phoneNumber.length() > 0) {
            phoneNumber.deleteCharAt(phoneNumber.length() - 1);
            updateDialerUi();
        }
    }

    private void clearDigits() {
        phoneNumber.setLength(0);
        updateDialerUi();
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

    private void updateDialerUi() {
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

    private void initiateCall(String number, @Nullable String name) {
        String contactName = name != null ? name : ContactRepository.getInstance(requireContext()).findContactByNumber(number);
        Intent intent = new Intent(getActivity(), CallingActivity.class);
        intent.putExtra("CONTACT_NAME", contactName != null ? contactName : number);
        intent.putExtra("CONTACT_PHONE", number);
        startActivity(intent);
    }

    private void showCallLogOptions(CallLogAdapter.GroupedCallLog group) {
        if (!isAdded() || getContext() == null) return;
        CallLogEntry entry = group.getLatestEntry();
        String phoneNumber = entry.getPhoneNumber();
        String contactName = (entry.getContactName() != null && !entry.getContactName().trim().isEmpty())
                ? entry.getContactName() : phoneNumber;

        android.app.Dialog floatingDialog = new android.app.Dialog(requireContext());
        floatingDialog.requestWindowFeature(android.view.Window.FEATURE_NO_TITLE);
        View dialogView = LayoutInflater.from(getContext()).inflate(R.layout.dialog_call_options, null);
        floatingDialog.setContentView(dialogView);

        View actionCall = dialogView.findViewById(R.id.action_call_log);
        View actionCopy = dialogView.findViewById(R.id.action_copy_call_phone);
        View actionBlock = dialogView.findViewById(R.id.action_block_call_phone);
        View actionDelete = dialogView.findViewById(R.id.action_delete_call_log);

        android.widget.TextView textBlockTitle = dialogView.findViewById(R.id.text_block_title);
        android.widget.ImageView imgBlockIcon = dialogView.findViewById(R.id.img_block_icon);

        // 1. Call
        actionCall.setOnClickListener(v -> {
            floatingDialog.dismiss();
            initiateCall(phoneNumber, entry.getContactName());
        });

        // 2. Copy
        actionCopy.setOnClickListener(v -> {
            android.content.ClipboardManager clipboard = (android.content.ClipboardManager) requireContext().getSystemService(android.content.Context.CLIPBOARD_SERVICE);
            android.content.ClipData clip = android.content.ClipData.newPlainText("Phone Number", phoneNumber);
            if (clipboard != null) {
                clipboard.setPrimaryClip(clip);
                Toast.makeText(getContext(), "Copied " + phoneNumber, Toast.LENGTH_SHORT).show();
            }
            floatingDialog.dismiss();
        });

        // 3. Block / Unblock
        boolean isCurrentlyBlocked = BlockedNumbersManager.isBlocked(requireContext(), phoneNumber);
        if (isCurrentlyBlocked) {
            textBlockTitle.setText("Unblock");
            textBlockTitle.setTextColor(android.graphics.Color.parseColor("#10B981"));
            imgBlockIcon.setImageResource(R.drawable.ic_contacts);
            imgBlockIcon.setColorFilter(android.graphics.Color.parseColor("#10B981"));
        } else {
            textBlockTitle.setText("Block");
            textBlockTitle.setTextColor(android.graphics.Color.parseColor("#D97706"));
            imgBlockIcon.setImageResource(R.drawable.ic_block);
            imgBlockIcon.setColorFilter(android.graphics.Color.parseColor("#D97706"));
        }

        actionBlock.setOnClickListener(v -> {
            floatingDialog.dismiss();
            if (isCurrentlyBlocked) {
                BlockedNumbersManager.unblockNumber(requireContext(), phoneNumber);
                Toast.makeText(getContext(), "Unblocked " + contactName, Toast.LENGTH_SHORT).show();
            } else {
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

                textTitle.setText("Block Number");
                textMsg.setText("You will no longer receive calls or messages from " + contactName + " (" + phoneNumber + ").");
                imgIcon.setImageResource(R.drawable.ic_block);
                imgIcon.setColorFilter(android.graphics.Color.parseColor("#D97706"));
                iconBg.setCardBackgroundColor(android.graphics.Color.parseColor("#FFFBEB"));
                btnAction.setText("Block");
                btnAction.setBackgroundColor(android.graphics.Color.parseColor("#D97706"));

                btnCancel.setOnClickListener(cv -> confirmDialog.dismiss());
                btnAction.setOnClickListener(cv -> {
                    confirmDialog.dismiss();
                    BlockedNumbersManager.blockNumber(requireContext(), phoneNumber);
                    Toast.makeText(getContext(), "Blocked " + contactName, Toast.LENGTH_SHORT).show();
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
            }
        });

        // 4. Delete
        actionDelete.setOnClickListener(v -> {
            floatingDialog.dismiss();
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

            textTitle.setText("Delete Call Log");
            textMsg.setText("Are you sure you want to delete this call log from your history?");
            imgIcon.setImageResource(R.drawable.ic_delete);
            imgIcon.setColorFilter(android.graphics.Color.parseColor("#EF4444"));
            iconBg.setCardBackgroundColor(android.graphics.Color.parseColor("#FEF2F2"));
            btnAction.setText("Delete");
            btnAction.setBackgroundColor(android.graphics.Color.parseColor("#EF4444"));

            btnCancel.setOnClickListener(cv -> confirmDialog.dismiss());
            btnAction.setOnClickListener(cv -> {
                confirmDialog.dismiss();
                for (int id : group.getEntryIds()) {
                    CallLogRepository.getInstance(requireContext()).deleteCallLog(id);
                }
                loadCallLogs();
                Toast.makeText(getContext(), "Call log deleted", Toast.LENGTH_SHORT).show();
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

        floatingDialog.show();
        if (floatingDialog.getWindow() != null) {
            floatingDialog.getWindow().setBackgroundDrawable(new android.graphics.drawable.ColorDrawable(android.graphics.Color.TRANSPARENT));
            floatingDialog.getWindow().setLayout(
                    android.view.ViewGroup.LayoutParams.WRAP_CONTENT,
                    android.view.ViewGroup.LayoutParams.WRAP_CONTENT
            );
            floatingDialog.getWindow().setGravity(android.view.Gravity.CENTER);
        }
    }

    private void showAddContactDialog(String number) {
        android.app.Dialog formDialog = new android.app.Dialog(requireContext());
        formDialog.requestWindowFeature(android.view.Window.FEATURE_NO_TITLE);
        View dialogView = LayoutInflater.from(getContext()).inflate(R.layout.dialog_contact_form, null);
        formDialog.setContentView(dialogView);

        android.widget.TextView textTitle = dialogView.findViewById(R.id.text_form_title);
        android.widget.TextView textSubtitle = dialogView.findViewById(R.id.text_form_subtitle);
        EditText editName = dialogView.findViewById(R.id.edit_form_name);
        EditText editPhone = dialogView.findViewById(R.id.edit_form_phone);
        View btnCancel = dialogView.findViewById(R.id.btn_form_cancel);
        com.google.android.material.button.MaterialButton btnSubmit = dialogView.findViewById(R.id.btn_form_submit);

        textTitle.setText("New Contact");
        textSubtitle.setText("Save number to your contacts");
        btnSubmit.setText("Save");

        if (number != null) {
            editPhone.setText(number);
        }

        btnCancel.setOnClickListener(v -> formDialog.dismiss());
        btnSubmit.setOnClickListener(v -> {
            String name = editName.getText().toString().trim();
            String phone = editPhone.getText().toString().trim();

            if (!TextUtils.isEmpty(name) && !TextUtils.isEmpty(phone)) {
                ContactRepository.getInstance(requireContext()).addContact(new Contact(name, phone));
                formDialog.dismiss();
                Toast.makeText(getContext(), "Contact saved: " + name, Toast.LENGTH_SHORT).show();
                updateDialerUi();
            } else {
                Toast.makeText(getContext(), R.string.error_empty_fields, Toast.LENGTH_SHORT).show();
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

    @Override
    public void onDestroy() {
        super.onDestroy();
        if (toneGenerator != null) {
            toneGenerator.release();
        }
    }
}