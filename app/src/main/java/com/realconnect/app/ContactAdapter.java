package com.realconnect.app;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Filter;
import android.widget.Filterable;
import android.widget.ImageView;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import java.util.ArrayList;
import java.util.List;

public class ContactAdapter extends RecyclerView.Adapter<ContactAdapter.ViewHolder> implements Filterable {

    private List<Contact> contactsFull;
    private List<Contact> contacts;
    private final OnContactActionListener listener;

    public interface OnContactActionListener {
        void onContactSelected(Contact contact);
        void onCallAction(Contact contact);
        void onMessageAction(Contact contact);
        void onContactLongClick(Contact contact);
    }

    public ContactAdapter(List<Contact> contacts, OnContactActionListener listener) {
        this.contactsFull = new ArrayList<>(contacts);
        this.contacts = new ArrayList<>(contactsFull);
        this.listener = listener;
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_contact, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        Contact contact = contacts.get(position);
        holder.nameText.setText(contact.getName());
        holder.phoneText.setText(contact.getPhoneNumber());
        
        holder.itemView.setOnClickListener(v -> listener.onContactSelected(contact));
        holder.callAction.setOnClickListener(v -> listener.onCallAction(contact));
        if (holder.messageAction != null) {
            holder.messageAction.setOnClickListener(v -> listener.onMessageAction(contact));
        }
        
        holder.itemView.setOnLongClickListener(v -> {
            listener.onContactLongClick(contact);
            return true;
        });
    }

    @Override
    public int getItemCount() {
        return contacts.size();
    }

    public void setContacts(List<Contact> newContacts) {
        this.contactsFull = new ArrayList<>(newContacts);
        this.contacts = new ArrayList<>(contactsFull);
        notifyDataSetChanged();
    }

    public void updateList() {
        this.contacts = new ArrayList<>(contactsFull);
        notifyDataSetChanged();
    }

    @Override
    public Filter getFilter() {
        return contactFilter;
    }

    private final Filter contactFilter = new Filter() {
        @Override
        protected FilterResults performFiltering(CharSequence constraint) {
            List<Contact> filteredList = new ArrayList<>();
            if (constraint == null || constraint.length() == 0) {
                filteredList.addAll(contactsFull);
            } else {
                String filterPattern = constraint.toString().toLowerCase().trim();
                for (Contact item : contactsFull) {
                    if (item.getName().toLowerCase().contains(filterPattern) || 
                        item.getPhoneNumber().contains(filterPattern)) {
                        filteredList.add(item);
                    }
                }
            }
            FilterResults results = new FilterResults();
            results.values = filteredList;
            return results;
        }

        @Override
        protected void publishResults(CharSequence constraint, FilterResults results) {
            contacts.clear();
            if (results.values != null) {
                contacts.addAll((List) results.values);
            }
            notifyDataSetChanged();
        }
    };

    static class ViewHolder extends RecyclerView.ViewHolder {
        TextView nameText;
        TextView phoneText;
        ImageView callAction;
        ImageView messageAction;
        ImageView avatarImage;

        ViewHolder(View itemView) {
            super(itemView);
            nameText = itemView.findViewById(R.id.text_contact_name);
            phoneText = itemView.findViewById(R.id.text_contact_phone);
            callAction = itemView.findViewById(R.id.image_call_action);
            messageAction = itemView.findViewById(R.id.image_message_action);
            avatarImage = itemView.findViewById(R.id.image_avatar);
        }
    }
}
