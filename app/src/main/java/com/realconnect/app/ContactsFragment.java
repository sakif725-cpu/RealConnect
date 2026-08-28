package com.realconnect.app;

import android.content.Intent;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextUtils;
import android.text.TextWatcher;
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
import java.util.List;

public class ContactsFragment extends Fragment {

    private ContactAdapter adapter;
    private EditText editSearch;
    private View layoutEmptyContacts;
    private ContactRepository.OnContactsChangedListener contactsListener;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_contacts, container, false);

        RecyclerView recyclerView = view.findViewById(R.id.recycler_contacts);
        recyclerView.setLayoutManager(new LinearLayoutManager(getContext()));
        layoutEmptyContacts = view.findViewById(R.id.layout_empty_contacts);

        List<Contact> contactList = ContactRepository.getInstance(requireContext()).getContacts();
        if (layoutEmptyContacts != null) {
            layoutEmptyContacts.setVisibility(contactList.isEmpty() ? View.VISIBLE : View.GONE);
        }

        adapter = new ContactAdapter(contactList, new ContactAdapter.OnContactActionListener() {
            @Override
            public void onContactSelected(Contact contact) {
                Toast.makeText(getContext(), contact.getName(), Toast.LENGTH_SHORT).show();
            }

            @Override
            public void onCallAction(Contact contact) {
                initiateCall(contact);
            }

            @Override
            public void onMessageAction(Contact contact) {
                initiateChat(contact);
            }

            @Override
            public void onContactLongClick(Contact contact) {
                showContactOptionsDialog(contact);
            }
        });

        recyclerView.setAdapter(adapter);

        // Setup Search
        editSearch = view.findViewById(R.id.edit_search);
        editSearch.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                if (adapter != null) {
                    adapter.getFilter().filter(s);
                }
            }

            @Override
            public void afterTextChanged(Editable s) {}
        });

        view.findViewById(R.id.btn_add_contact).setOnClickListener(v -> showAddContactDialog(null));

        // Reactive Real-Time Listener: Instantly updates contact list without needing tab switch
        contactsListener = () -> {
            if (isAdded() && getActivity() != null) {
                getActivity().runOnUiThread(this::refreshContacts);
            }
        };
        ContactRepository.getInstance(requireContext()).addListener(contactsListener);

        return view;
    }

    @Override
    public void onResume() {
        super.onResume();
        refreshContacts();
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        if (contactsListener != null && getContext() != null) {
            ContactRepository.getInstance(requireContext()).removeListener(contactsListener);
        }
    }

    private void initiateCall(Contact contact) {
        Intent intent = new Intent(getActivity(), CallingActivity.class);
        intent.putExtra("CONTACT_NAME", contact.getName());
        intent.putExtra("CONTACT_PHONE", contact.getPhoneNumber());
        startActivity(intent);
    }

    private void initiateChat(Contact contact) {
        Intent intent = new Intent(getActivity(), ChatActivity.class);
        intent.putExtra("CONTACT_NAME", contact.getName());
        intent.putExtra("CONTACT_PHONE", contact.getPhoneNumber());
        startActivity(intent);
    }

    private void showContactOptionsDialog(Contact contact) {
        if (!isAdded() || getContext() == null) return;

        android.app.Dialog floatingDialog = new android.app.Dialog(requireContext());
        floatingDialog.requestWindowFeature(android.view.Window.FEATURE_NO_TITLE);
        View dialogView = LayoutInflater.from(getContext()).inflate(R.layout.dialog_contact_options, null);
        floatingDialog.setContentView(dialogView);

        if (floatingDialog.getWindow() != null) {
            floatingDialog.getWindow().setBackgroundDrawable(new android.graphics.drawable.ColorDrawable(android.graphics.Color.TRANSPARENT));
            floatingDialog.getWindow().setLayout(
                    android.view.ViewGroup.LayoutParams.MATCH_PARENT,
                    android.view.ViewGroup.LayoutParams.WRAP_CONTENT
            );
        }

        android.widget.ImageView imgAvatar = dialogView.findViewById(R.id.img_sheet_avatar);
        android.widget.TextView textName = dialogView.findViewById(R.id.text_sheet_name);
        android.widget.TextView textPhone = dialogView.findViewById(R.id.text_sheet_phone);
        View btnClose = dialogView.findViewById(R.id.btn_floating_close);
        View btnQuickCall = dialogView.findViewById(R.id.btn_sheet_quick_call);
        View btnQuickMsg = dialogView.findViewById(R.id.btn_sheet_quick_message);

        View actionCopy = dialogView.findViewById(R.id.action_copy_number);
        View actionShare = dialogView.findViewById(R.id.action_share_contact);
        View actionEdit = dialogView.findViewById(R.id.action_edit_contact);
        View actionBlock = dialogView.findViewById(R.id.action_block_contact);
        View actionDelete = dialogView.findViewById(R.id.action_delete_contact);

        android.widget.TextView textBlockTitle = dialogView.findViewById(R.id.text_block_title);
        android.widget.ImageView imgBlockIcon = dialogView.findViewById(R.id.img_block_icon);
        com.google.android.material.card.MaterialCardView cardBlockIcon = dialogView.findViewById(R.id.card_block_icon);

        textName.setText(contact.getName());
        textPhone.setText(contact.getPhoneNumber());

        if (btnClose != null) {
            btnClose.setOnClickListener(v -> floatingDialog.dismiss());
        }

        // Avatar placeholder
        android.graphics.Bitmap avatarBitmap = ImageUtils.createAvatarWithInitial(
                contact.getName(), 180, android.graphics.Color.parseColor("#0EA5E9"), android.graphics.Color.WHITE
        );
        imgAvatar.setImageBitmap(avatarBitmap);

        // Fetch remote avatar if available
        String cleanPhone = ChatRepository.cleanPhone(contact.getPhoneNumber());
        if (!cleanPhone.isEmpty()) {
            com.google.firebase.database.FirebaseDatabase.getInstance().getReference("users")
                    .child(cleanPhone).child("profileImageBase64")
                    .addListenerForSingleValueEvent(new com.google.firebase.database.ValueEventListener() {
                        @Override
                        public void onDataChange(@NonNull com.google.firebase.database.DataSnapshot snapshot) {
                            String base64 = snapshot.getValue(String.class);
                            if (base64 != null && !base64.isEmpty() && isAdded()) {
                                android.graphics.Bitmap bmp = ImageUtils.base64ToBitmap(base64);
                                if (bmp != null) imgAvatar.setImageBitmap(bmp);
                            }
                        }
                        @Override public void onCancelled(@NonNull com.google.firebase.database.DatabaseError error) {}
                    });
        }

        // Quick Call & Message
        btnQuickCall.setOnClickListener(v -> {
            floatingDialog.dismiss();
            initiateCall(contact);
        });

        btnQuickMsg.setOnClickListener(v -> {
            floatingDialog.dismiss();
            initiateChat(contact);
        });

        // 1. Copy Phone Number
        actionCopy.setOnClickListener(v -> {
            android.content.ClipboardManager clipboard = (android.content.ClipboardManager) requireContext().getSystemService(android.content.Context.CLIPBOARD_SERVICE);
            android.content.ClipData clip = android.content.ClipData.newPlainText("Contact Phone", contact.getPhoneNumber());
            if (clipboard != null) {
                clipboard.setPrimaryClip(clip);
                Toast.makeText(getContext(), "Copied " + contact.getPhoneNumber(), Toast.LENGTH_SHORT).show();
            }
            floatingDialog.dismiss();
        });

        // 2. Share Contact
        actionShare.setOnClickListener(v -> {
            floatingDialog.dismiss();
            Intent shareIntent = new Intent(Intent.ACTION_SEND);
            shareIntent.setType("text/plain");
            String shareBody = "Name: " + contact.getName() + "\nPhone: " + contact.getPhoneNumber() + "\nShared via RealConnect";
            shareIntent.putExtra(Intent.EXTRA_TEXT, shareBody);
            startActivity(Intent.createChooser(shareIntent, "Share Contact"));
        });

        // 3. Edit Contact
        actionEdit.setOnClickListener(v -> {
            floatingDialog.dismiss();
            showEditContactDialog(contact);
        });

        // 4. Block / Unblock Contact
        boolean isCurrentlyBlocked = BlockedNumbersManager.isBlocked(requireContext(), contact.getPhoneNumber());
        if (isCurrentlyBlocked) {
            textBlockTitle.setText("Unblock Contact");
            textBlockTitle.setTextColor(android.graphics.Color.parseColor("#10B981"));
            cardBlockIcon.setCardBackgroundColor(android.graphics.Color.parseColor("#ECFDF5"));
            imgBlockIcon.setImageResource(R.drawable.ic_contacts);
            imgBlockIcon.setColorFilter(android.graphics.Color.parseColor("#10B981"));
        } else {
            textBlockTitle.setText("Block Contact");
            textBlockTitle.setTextColor(android.graphics.Color.parseColor("#D97706"));
            cardBlockIcon.setCardBackgroundColor(android.graphics.Color.parseColor("#FFFBEB"));
            imgBlockIcon.setImageResource(R.drawable.ic_block);
            imgBlockIcon.setColorFilter(android.graphics.Color.parseColor("#D97706"));
        }

        actionBlock.setOnClickListener(v -> {
            floatingDialog.dismiss();
            if (isCurrentlyBlocked) {
                BlockedNumbersManager.unblockNumber(requireContext(), contact.getPhoneNumber());
                Toast.makeText(getContext(), "Unblocked " + contact.getName(), Toast.LENGTH_SHORT).show();
            } else {
                new MaterialAlertDialogBuilder(requireContext())
                        .setTitle("Block " + contact.getName() + "?")
                        .setMessage("You will no longer receive phone calls or messages from " + contact.getPhoneNumber() + ".")
                        .setPositiveButton("Block", (d, w) -> {
                            BlockedNumbersManager.blockNumber(requireContext(), contact.getPhoneNumber());
                            Toast.makeText(getContext(), "Blocked " + contact.getName(), Toast.LENGTH_SHORT).show();
                        })
                        .setNegativeButton("Cancel", null)
                        .show();
            }
        });

        // 5. Delete Contact
        actionDelete.setOnClickListener(v -> {
            floatingDialog.dismiss();
            showDeleteConfirmationDialog(contact);
        });

        floatingDialog.show();
    }

    private void showEditContactDialog(Contact contact) {
        View dialogView = LayoutInflater.from(getContext()).inflate(R.layout.dialog_add_contact, null);
        EditText editName = dialogView.findViewById(R.id.edit_name);
        EditText editPhone = dialogView.findViewById(R.id.edit_phone);

        editName.setText(contact.getName());
        editPhone.setText(contact.getPhoneNumber());

        new MaterialAlertDialogBuilder(requireContext())
                .setTitle("Edit Contact")
                .setView(dialogView)
                .setPositiveButton("Save", (dialog, which) -> {
                    String name = editName.getText().toString().trim();
                    String phone = editPhone.getText().toString().trim();

                    if (!TextUtils.isEmpty(name) && !TextUtils.isEmpty(phone)) {
                        Contact updatedContact = new Contact(name, phone);
                        ContactRepository.getInstance(requireContext()).updateContact(contact, updatedContact);
                        refreshContacts();
                        Toast.makeText(getContext(), "Contact updated", Toast.LENGTH_SHORT).show();
                    } else {
                        Toast.makeText(getContext(), R.string.error_empty_fields, Toast.LENGTH_SHORT).show();
                    }
                })
                .setNegativeButton(R.string.action_cancel, null)
                .show();
    }

    private void showDeleteConfirmationDialog(Contact contact) {
        new MaterialAlertDialogBuilder(requireContext())
                .setTitle("Delete Contact")
                .setMessage("Are you sure you want to delete " + contact.getName() + "?")
                .setPositiveButton("Delete", (dialog, which) -> {
                    ContactRepository.getInstance(requireContext()).deleteContact(contact);
                    refreshContacts();
                    Toast.makeText(getContext(), "Contact deleted", Toast.LENGTH_SHORT).show();
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void showAddContactDialog(@Nullable String prefilledPhone) {
        View dialogView = LayoutInflater.from(getContext()).inflate(R.layout.dialog_add_contact, null);
        EditText editName = dialogView.findViewById(R.id.edit_name);
        EditText editPhone = dialogView.findViewById(R.id.edit_phone);

        if (prefilledPhone != null) {
            editPhone.setText(prefilledPhone);
        }

        new MaterialAlertDialogBuilder(requireContext())
                .setTitle(R.string.dialog_add_contact_title)
                .setView(dialogView)
                .setPositiveButton(R.string.action_add, (dialog, which) -> {
                    String name = editName.getText().toString().trim();
                    String phone = editPhone.getText().toString().trim();

                    if (!TextUtils.isEmpty(name) && !TextUtils.isEmpty(phone)) {
                        ContactRepository.getInstance(requireContext()).addContact(new Contact(name, phone));
                        if (editSearch != null && !TextUtils.isEmpty(editSearch.getText())) {
                            editSearch.setText("");
                        }
                        refreshContacts();
                        Toast.makeText(getContext(), "Contact added: " + name, Toast.LENGTH_SHORT).show();
                    } else {
                        Toast.makeText(getContext(), R.string.error_empty_fields, Toast.LENGTH_SHORT).show();
                    }
                })
                .setNegativeButton(R.string.action_cancel, null)
                .show();
    }

    private void refreshContacts() {
        if (!isAdded() || getContext() == null || adapter == null) return;
        List<Contact> updatedList = ContactRepository.getInstance(requireContext()).getContacts();
        adapter.setContacts(updatedList);
        if (layoutEmptyContacts != null) {
            layoutEmptyContacts.setVisibility(updatedList.isEmpty() ? View.VISIBLE : View.GONE);
        }
        if (editSearch != null && !TextUtils.isEmpty(editSearch.getText())) {
            adapter.getFilter().filter(editSearch.getText());
        }
    }
}