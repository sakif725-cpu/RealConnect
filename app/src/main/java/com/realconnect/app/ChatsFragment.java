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

        adapter = new ChatPreviewAdapter(
                requireContext(),
                selfPhone,
                (contactName, contactPhone) -> openChatActivity(contactName, contactPhone),
                (message, contactName, contactPhone) -> showChatOptionsDialog(message, contactName, contactPhone)
        );

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

        textTitle.setText("New Conversation");
        textSubtitle.setText("Enter details to start chatting");
        btnSubmit.setText("Chat");

        btnCancel.setOnClickListener(v -> formDialog.dismiss());
        btnSubmit.setOnClickListener(v -> {
            String name = editName.getText().toString().trim();
            String phone = editPhone.getText().toString().trim();
            if (!TextUtils.isEmpty(phone)) {
                formDialog.dismiss();
                openChatActivity(name, phone);
            } else {
                Toast.makeText(getContext(), "Enter a phone number", Toast.LENGTH_SHORT).show();
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

    private void showChatOptionsDialog(Message message, String contactName, String contactPhone) {
        if (!isAdded() || getContext() == null) return;

        android.app.Dialog floatingDialog = new android.app.Dialog(requireContext());
        floatingDialog.requestWindowFeature(android.view.Window.FEATURE_NO_TITLE);
        View dialogView = LayoutInflater.from(getContext()).inflate(R.layout.dialog_chat_options, null);
        floatingDialog.setContentView(dialogView);

        View actionCopy = dialogView.findViewById(R.id.action_copy_chat_phone);
        View actionBlock = dialogView.findViewById(R.id.action_block_chat);
        View actionDelete = dialogView.findViewById(R.id.action_delete_chat);

        android.widget.TextView textBlockTitle = dialogView.findViewById(R.id.text_block_title);
        android.widget.ImageView imgBlockIcon = dialogView.findViewById(R.id.img_block_icon);

        // 1. Copy Phone Number
        actionCopy.setOnClickListener(v -> {
            android.content.ClipboardManager clipboard = (android.content.ClipboardManager) requireContext().getSystemService(android.content.Context.CLIPBOARD_SERVICE);
            android.content.ClipData clip = android.content.ClipData.newPlainText("Phone Number", contactPhone);
            if (clipboard != null) {
                clipboard.setPrimaryClip(clip);
                Toast.makeText(getContext(), "Copied " + contactPhone, Toast.LENGTH_SHORT).show();
            }
            floatingDialog.dismiss();
        });

        // 2. Block / Unblock Number
        boolean isCurrentlyBlocked = BlockedNumbersManager.isBlocked(requireContext(), contactPhone);
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
                BlockedNumbersManager.unblockNumber(requireContext(), contactPhone);
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

                textTitle.setText("Block Contact");
                textMsg.setText("You will no longer receive calls or messages from " + contactName + " (" + contactPhone + ").");
                imgIcon.setImageResource(R.drawable.ic_block);
                imgIcon.setColorFilter(android.graphics.Color.parseColor("#D97706"));
                iconBg.setCardBackgroundColor(android.graphics.Color.parseColor("#FFFBEB"));
                btnAction.setText("Block");
                btnAction.setBackgroundColor(android.graphics.Color.parseColor("#D97706"));

                btnCancel.setOnClickListener(cv -> confirmDialog.dismiss());
                btnAction.setOnClickListener(cv -> {
                    confirmDialog.dismiss();
                    BlockedNumbersManager.blockNumber(requireContext(), contactPhone);
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

        // 3. Delete Conversation
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

            textTitle.setText("Delete Conversation");
            textMsg.setText("Are you sure you want to delete the chat history with " + contactName + "?");
            imgIcon.setImageResource(R.drawable.ic_delete);
            imgIcon.setColorFilter(android.graphics.Color.parseColor("#EF4444"));
            iconBg.setCardBackgroundColor(android.graphics.Color.parseColor("#FEF2F2"));
            btnAction.setText("Delete");
            btnAction.setBackgroundColor(android.graphics.Color.parseColor("#EF4444"));

            btnCancel.setOnClickListener(cv -> confirmDialog.dismiss());
            btnAction.setOnClickListener(cv -> {
                confirmDialog.dismiss();
                ChatRepository.getInstance(requireContext()).deleteChat(message.getChatId());
                loadRecentChats();
                Toast.makeText(getContext(), "Conversation deleted", Toast.LENGTH_SHORT).show();
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
}