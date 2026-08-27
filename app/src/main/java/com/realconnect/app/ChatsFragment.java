package com.realconnect.app;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.Toast;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.floatingactionbutton.FloatingActionButton;
import java.util.List;

public class ChatsFragment extends Fragment {

    private ChatPreviewAdapter adapter;
    private View layoutEmpty;
    private String selfPhone;
    private ChatRepository.OnMessageReceivedListener messageListener;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_chats, container, false);

        SharedPreferences prefs = requireContext().getSharedPreferences("ProfilePrefs", Context.MODE_PRIVATE);
        selfPhone = prefs.getString("phone", "");

        RecyclerView recyclerView = view.findViewById(R.id.recycler_chats);
        recyclerView.setLayoutManager(new LinearLayoutManager(getContext()));

        layoutEmpty = view.findViewById(R.id.layout_empty_chats);

        adapter = new ChatPreviewAdapter(requireContext(), selfPhone, (contactName, contactPhone) -> {
            openChatActivity(contactName, contactPhone);
        });

        recyclerView.setAdapter(adapter);

        FloatingActionButton btnNewChat = view.findViewById(R.id.btn_new_chat);
        btnNewChat.setOnClickListener(v -> showNewChatOptions());

        messageListener = message -> {
            if (isAdded() && getActivity() != null) {
                getActivity().runOnUiThread(this::loadRecentChats);
            }
        };
        ChatRepository.getInstance(requireContext()).addGlobalListener(messageListener);

        return view;
    }

    @Override
    public void onResume() {
        super.onResume();
        loadRecentChats();
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        if (messageListener != null && getContext() != null) {
            ChatRepository.getInstance(requireContext()).removeGlobalListener(messageListener);
        }
    }

    private void loadRecentChats() {
        if (!isAdded() || getContext() == null) return;
        List<Message> recentChats = ChatRepository.getInstance(requireContext()).getRecentChats();
        if (recentChats == null || recentChats.isEmpty()) {
            layoutEmpty.setVisibility(View.VISIBLE);
            adapter.setChats(null);
        } else {
            layoutEmpty.setVisibility(View.GONE);
            adapter.setChats(recentChats);
        }
    }

    private void showNewChatOptions() {
        String[] options = {"Select from Contacts", "Enter Phone Number"};
        new MaterialAlertDialogBuilder(requireContext())
                .setTitle("Start New Chat")
                .setItems(options, (dialog, which) -> {
                    if (which == 0) {
                        showContactPickerDialog();
                    } else {
                        showDirectNumberDialog();
                    }
                })
                .show();
    }

    private void showDirectNumberDialog() {
        View dialogView = LayoutInflater.from(getContext()).inflate(R.layout.dialog_add_contact, null);
        EditText editName = dialogView.findViewById(R.id.edit_name);
        EditText editPhone = dialogView.findViewById(R.id.edit_phone);

        new MaterialAlertDialogBuilder(requireContext())
                .setTitle("New Conversation")
                .setView(dialogView)
                .setPositiveButton("Chat", (dialog, which) -> {
                    String name = editName.getText().toString().trim();
                    String phone = editPhone.getText().toString().trim();
                    if (!TextUtils.isEmpty(phone)) {
                        openChatActivity(name, phone);
                    } else {
                        Toast.makeText(getContext(), "Enter a phone number", Toast.LENGTH_SHORT).show();
                    }
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void showContactPickerDialog() {
        List<Contact> contacts = ContactRepository.getInstance(requireContext()).getContacts();
        if (contacts.isEmpty()) {
            Toast.makeText(getContext(), "No contacts found. Use 'Enter Phone Number' instead.", Toast.LENGTH_SHORT).show();
            showDirectNumberDialog();
            return;
        }

        String[] contactItems = new String[contacts.size()];
        for (int i = 0; i < contacts.size(); i++) {
            contactItems[i] = contacts.get(i).getName() + " (" + contacts.get(i).getPhoneNumber() + ")";
        }

        new MaterialAlertDialogBuilder(requireContext())
                .setTitle("Select Contact to Chat")
                .setItems(contactItems, (dialog, which) -> {
                    Contact selected = contacts.get(which);
                    openChatActivity(selected.getName(), selected.getPhoneNumber());
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void openChatActivity(String name, String phone) {
        Intent intent = new Intent(getActivity(), ChatActivity.class);
        intent.putExtra("CONTACT_NAME", name);
        intent.putExtra("CONTACT_PHONE", phone);
        startActivity(intent);
    }
}