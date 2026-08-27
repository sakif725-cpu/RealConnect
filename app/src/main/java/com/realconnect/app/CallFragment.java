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
            new MaterialAlertDialogBuilder(requireContext())
                    .setTitle("Clear History")
                    .setMessage("Are you sure you want to clear all call logs?")
                    .setPositiveButton("Clear All", (dialog, which) -> {
                        CallLogRepository.getInstance(requireContext()).clearCallLogs();
                        loadCallLogs();
                    })
                    .setNegativeButton("Cancel", null)
                    .show();
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
        CallLogEntry entry = group.getLatestEntry();
        String title = (entry.getContactName() != null && !entry.getContactName().isEmpty())
                ? entry.getContactName() : entry.getPhoneNumber();
        String[] options = {"Call " + title, "Delete from history"};
        new MaterialAlertDialogBuilder(requireContext())
                .setTitle("Call Details")
                .setItems(options, (dialog, which) -> {
                    if (which == 0) {
                        initiateCall(entry.getPhoneNumber(), entry.getContactName());
                    } else if (which == 1) {
                        for (int id : group.getEntryIds()) {
                            CallLogRepository.getInstance(requireContext()).deleteCallLog(id);
                        }
                    }
                })
                .show();
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
                        updateDialerUi();
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