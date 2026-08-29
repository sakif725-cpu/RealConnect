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
        btnNewChat.setOnClickListener(v -> showNewChatDialog());

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

    private void showNewChatDialog() {
        if (!isAdded() || getContext() == null) return;

        android.app.Dialog dialog = new android.app.Dialog(requireContext());
        dialog.requestWindowFeature(android.view.Window.FEATURE_NO_TITLE);
        View dialogView = LayoutInflater.from(getContext()).inflate(R.layout.dialog_new_chat, null);
        dialog.setContentView(dialogView);

        // 1. Close button
        View btnClose = dialogView.findViewById(R.id.btn_floating_close);
        if (btnClose != null) {
            btnClose.setOnClickListener(v -> dialog.dismiss());
        }

        // 2. Direct Number Action
        View cardDirectNumber = dialogView.findViewById(R.id.action_direct_phone);
        if (cardDirectNumber != null) {
            cardDirectNumber.setOnClickListener(v -> {
                dialog.dismiss();
                showDirectNumberDialog();
            });
        }

        // 3. New Contact Action
        View cardNewContact = dialogView.findViewById(R.id.action_new_contact);
        if (cardNewContact != null) {
            cardNewContact.setOnClickListener(v -> {
                dialog.dismiss();
                showAddNewContactDialog();
            });
        }

        // 4. Contacts Recycler & Adapter
        RecyclerView recyclerContacts = dialogView.findViewById(R.id.recycler_floating_contacts);
        recyclerContacts.setLayoutManager(new LinearLayoutManager(getContext()));
        List<Contact> contacts = ContactRepository.getInstance(requireContext()).getContacts();

        TextView textContactsCount = dialogView.findViewById(R.id.text_floating_contacts_count);
        if (textContactsCount != null) {
            textContactsCount.setText("Contacts (" + contacts.size() + ")");
        }

        View layoutEmpty = dialogView.findViewById(R.id.layout_floating_empty);
        if (layoutEmpty != null) {
            layoutEmpty.setVisibility(contacts.isEmpty() ? View.VISIBLE : View.GONE);
        }

        NewChatContactAdapter contactAdapter = new NewChatContactAdapter(contacts, contact -> {
            dialog.dismiss();
            openChatActivity(contact.getName(), contact.getPhoneNumber());
        });
        recyclerContacts.setAdapter(contactAdapter);

        // 5. Instant Chat on Custom Query
        View cardInstantChat = dialogView.findViewById(R.id.card_floating_instant_chat);
        TextView textInstantQuery = dialogView.findViewById(R.id.text_floating_instant_query);

        // 6. Search Bar
        EditText editSearch = dialogView.findViewById(R.id.edit_floating_search);
        View btnClearSearch = dialogView.findViewById(R.id.btn_floating_search_clear);

        contactAdapter.setOnFilterResultListener((count, query) -> {
            if (textContactsCount != null) {
                textContactsCount.setText("Contacts (" + count + ")");
            }
            if (layoutEmpty != null) {
                layoutEmpty.setVisibility(count == 0 ? View.VISIBLE : View.GONE);
            }

            if (!query.isEmpty() && cardInstantChat != null && textInstantQuery != null) {
                cardInstantChat.setVisibility(View.VISIBLE);
                textInstantQuery.setText("Chat with \"" + query + "\"");
                cardInstantChat.setOnClickListener(v -> {
                    dialog.dismiss();
                    openChatActivity(query, query);
                });
            } else if (cardInstantChat != null) {
                cardInstantChat.setVisibility(View.GONE);
            }
        });

        if (editSearch != null) {
            editSearch.addTextChangedListener(new android.text.TextWatcher() {
                @Override
                public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

                @Override
                public void onTextChanged(CharSequence s, int start, int before, int count) {
                    contactAdapter.getFilter().filter(s);
                    if (btnClearSearch != null) {
                        btnClearSearch.setVisibility(s != null && s.length() > 0 ? View.VISIBLE : View.GONE);
                    }
                }

                @Override
                public void afterTextChanged(android.text.Editable s) {}
            });
        }

        if (btnClearSearch != null && editSearch != null) {
            btnClearSearch.setOnClickListener(v -> editSearch.setText(""));
        }

        dialog.show();
        if (dialog.getWindow() != null) {
            dialog.getWindow().setBackgroundDrawable(new android.graphics.drawable.ColorDrawable(android.graphics.Color.TRANSPARENT));
            dialog.getWindow().setLayout(
                    android.view.ViewGroup.LayoutParams.MATCH_PARENT,
                    android.view.ViewGroup.LayoutParams.WRAP_CONTENT
            );
            dialog.getWindow().setGravity(android.view.Gravity.CENTER);
        }
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

        textTitle.setText("Direct Conversation");
        textSubtitle.setText("Enter phone number to start chatting");
        btnSubmit.setText("Start Chat");

        btnCancel.setOnClickListener(v -> formDialog.dismiss());
        btnSubmit.setOnClickListener(v -> {
            String name = editName.getText().toString().trim();
            String phone = editPhone.getText().toString().trim();
            if (!TextUtils.isEmpty(phone)) {
                formDialog.dismiss();
                openChatActivity(name, phone);
            } else {
                Toast.makeText(getContext(), "Please enter a phone number", Toast.LENGTH_SHORT).show();
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

    private void showAddNewContactDialog() {
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
        textSubtitle.setText("Save contact and start conversation");
        btnSubmit.setText("Save & Chat");

        btnCancel.setOnClickListener(v -> formDialog.dismiss());
        btnSubmit.setOnClickListener(v -> {
            String name = editName.getText().toString().trim();
            String phone = editPhone.getText().toString().trim();
            if (!TextUtils.isEmpty(name) && !TextUtils.isEmpty(phone)) {
                Contact newContact = new Contact(name, phone);
                ContactRepository.getInstance(requireContext()).addContact(newContact);
                formDialog.dismiss();
                openChatActivity(name, phone);
            } else {
                Toast.makeText(getContext(), "Please fill in both name and phone number", Toast.LENGTH_SHORT).show();
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

        // 1. Copy Recent Message
        actionCopy.setOnClickListener(v -> {
            android.content.ClipboardManager clipboard = (android.content.ClipboardManager) requireContext().getSystemService(android.content.Context.CLIPBOARD_SERVICE);
            String textToCopy = (message != null && message.getText() != null && !message.getText().trim().isEmpty())
                    ? message.getText() : contactPhone;
            android.content.ClipData clip = android.content.ClipData.newPlainText("Message", textToCopy);
            if (clipboard != null) {
                clipboard.setPrimaryClip(clip);
                Toast.makeText(getContext(), "Message copied", Toast.LENGTH_SHORT).show();
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