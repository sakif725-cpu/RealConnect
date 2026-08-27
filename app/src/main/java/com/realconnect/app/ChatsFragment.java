package com.realconnect.app;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
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
        btnNewChat.setOnClickListener(v -> showContactPickerDialog());

        return view;
    }

    @Override
    public void onResume() {
        super.onResume();
        loadRecentChats();
    }

    private void loadRecentChats() {
        List<Message> recentChats = ChatRepository.getInstance(requireContext()).getRecentChats();
        if (recentChats == null || recentChats.isEmpty()) {
            layoutEmpty.setVisibility(View.VISIBLE);
            adapter.setChats(null);
        } else {
            layoutEmpty.setVisibility(View.GONE);
            adapter.setChats(recentChats);
        }
    }

    private void showContactPickerDialog() {
        List<Contact> contacts = ContactRepository.getInstance(requireContext()).getContacts();
        if (contacts.isEmpty()) {
            Toast.makeText(getContext(), "No contacts found. Add contacts first.", Toast.LENGTH_SHORT).show();
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