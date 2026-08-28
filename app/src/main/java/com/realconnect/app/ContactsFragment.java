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
                    android.view.ViewGroup.LayoutParams.WRAP_CONTENT,
                    android.view.ViewGroup.LayoutParams.WRAP_CONTENT
            );
        }

        View actionCopy = dialogView.findViewById(R.id.action_copy_number);
        View actionShare = dialogView.findViewById(R.id.action_share_contact);
        View actionEdit = dialogView.findViewById(R.id.action_edit_contact);
        View actionBlock = dialogView.findViewById(R.id.action_block_contact);
        View actionDelete = dialogView.findViewById(R.id.action_delete_contact);

        android.widget.TextView textBlockTitle = dialogView.findViewById(R.id.text_block_title);
        android.widget.ImageView imgBlockIcon = dialogView.findViewById(R.id.img_block_icon);

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
            String shareBody = "Contact: " + contact.getName() + "\nPhone: " + contact.getPhoneNumber();
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
                BlockedNumbersManager.unblockNumber(requireContext(), contact.getPhoneNumber());
                Toast.makeText(getContext(), "Unblocked " + contact.getName(), Toast.LENGTH_SHORT).show();
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
                textMsg.setText("Are you sure you want to block calls and messages from " + contact.getName() + " (" + contact.getPhoneNumber() + ")?");
                imgIcon.setImageResource(R.drawable.ic_block);
                imgIcon.setColorFilter(android.graphics.Color.parseColor("#D97706"));
                iconBg.setCardBackgroundColor(android.graphics.Color.parseColor("#FFFBEB"));
                btnAction.setText("Block");
                btnAction.setBackgroundColor(android.graphics.Color.parseColor("#D97706"));

                btnCancel.setOnClickListener(cv -> confirmDialog.dismiss());
                btnAction.setOnClickListener(cv -> {
                    confirmDialog.dismiss();
                    BlockedNumbersManager.blockNumber(requireContext(), contact.getPhoneNumber());
                    Toast.makeText(getContext(), "Blocked " + contact.getName(), Toast.LENGTH_SHORT).show();
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

        // 5. Delete Contact
        actionDelete.setOnClickListener(v -> {
            floatingDialog.dismiss();
            showDeleteConfirmationDialog(contact);
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

    private void showEditContactDialog(Contact contact) {
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

        textTitle.setText("Edit Contact");
        textSubtitle.setText("Update contact details below");
        btnSubmit.setText("Save");

        editName.setText(contact.getName());
        editPhone.setText(contact.getPhoneNumber());

        btnCancel.setOnClickListener(v -> formDialog.dismiss());
        btnSubmit.setOnClickListener(v -> {
            String name = editName.getText().toString().trim();
            String phone = editPhone.getText().toString().trim();

            if (!TextUtils.isEmpty(name) && !TextUtils.isEmpty(phone)) {
                Contact updatedContact = new Contact(name, phone);
                ContactRepository.getInstance(requireContext()).updateContact(contact, updatedContact);
                refreshContacts();
                formDialog.dismiss();
                Toast.makeText(getContext(), "Contact updated", Toast.LENGTH_SHORT).show();
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

    private void showDeleteConfirmationDialog(Contact contact) {
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

        textTitle.setText("Delete Contact");
        textMsg.setText("Are you sure you want to delete " + contact.getName() + " from your contacts list?");
        imgIcon.setImageResource(R.drawable.ic_delete);
        imgIcon.setColorFilter(android.graphics.Color.parseColor("#EF4444"));
        iconBg.setCardBackgroundColor(android.graphics.Color.parseColor("#FEF2F2"));
        btnAction.setText("Delete");
        btnAction.setBackgroundColor(android.graphics.Color.parseColor("#EF4444"));

        btnCancel.setOnClickListener(v -> confirmDialog.dismiss());
        btnAction.setOnClickListener(v -> {
            confirmDialog.dismiss();
            ContactRepository.getInstance(requireContext()).deleteContact(contact);
            refreshContacts();
            Toast.makeText(getContext(), "Contact deleted", Toast.LENGTH_SHORT).show();
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

    private void showAddContactDialog(@Nullable String prefilledPhone) {
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
        textSubtitle.setText("Add contact to your phonebook");
        btnSubmit.setText("Add");

        if (prefilledPhone != null) {
            editPhone.setText(prefilledPhone);
        }

        btnCancel.setOnClickListener(v -> formDialog.dismiss());
        btnSubmit.setOnClickListener(v -> {
            String name = editName.getText().toString().trim();
            String phone = editPhone.getText().toString().trim();

            if (!TextUtils.isEmpty(name) && !TextUtils.isEmpty(phone)) {
                ContactRepository.getInstance(requireContext()).addContact(new Contact(name, phone));
                if (editSearch != null && !TextUtils.isEmpty(editSearch.getText())) {
                    editSearch.setText("");
                }
                refreshContacts();
                formDialog.dismiss();
                Toast.makeText(getContext(), "Contact added: " + name, Toast.LENGTH_SHORT).show();
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