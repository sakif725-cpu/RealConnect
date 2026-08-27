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

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_contacts, container, false);

        RecyclerView recyclerView = view.findViewById(R.id.recycler_contacts);
        recyclerView.setLayoutManager(new LinearLayoutManager(getContext()));

        List<Contact> contactList = ContactRepository.getInstance(requireContext()).getContacts();

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
                adapter.getFilter().filter(s);
            }

            @Override
            public void afterTextChanged(Editable s) {}
        });

        view.findViewById(R.id.btn_add_contact).setOnClickListener(v -> showAddContactDialog(null));

        return view;
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
        String[] options = {"Edit", "Delete"};
        new MaterialAlertDialogBuilder(requireContext())
                .setTitle(contact.getName())
                .setItems(options, (dialog, which) -> {
                    if (which == 0) {
                        showEditContactDialog(contact);
                    } else if (which == 1) {
                        showDeleteConfirmationDialog(contact);
                    }
                })
                .show();
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
                        
                        if (editSearch != null) editSearch.setText("");
                        
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
        List<Contact> updatedList = ContactRepository.getInstance(requireContext()).getContacts();
        adapter.setContacts(updatedList);
    }
}
