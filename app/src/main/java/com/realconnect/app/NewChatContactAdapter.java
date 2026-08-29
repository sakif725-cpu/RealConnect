package com.realconnect.app;

import android.content.Context;
import android.graphics.Color;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Filter;
import android.widget.Filterable;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import com.google.android.material.card.MaterialCardView;
import java.util.ArrayList;
import java.util.List;

public class NewChatContactAdapter extends RecyclerView.Adapter<NewChatContactAdapter.ViewHolder> implements Filterable {

    public interface OnContactClickListener {
        void onContactClick(Contact contact);
    }

    public interface OnFilterResultListener {
        void onFilterResult(int count, String query);
    }

    private final List<Contact> originalList;
    private final List<Contact> filteredList;
    private final OnContactClickListener clickListener;
    private OnFilterResultListener filterResultListener;

    private static final int[][] AVATAR_PALETTES = {
            {Color.parseColor("#E0F2FE"), Color.parseColor("#0284C7"), Color.parseColor("#BAE6FD")}, // Sky
            {Color.parseColor("#F3E8FF"), Color.parseColor("#7E22CE"), Color.parseColor("#E9D5FF")}, // Purple
            {Color.parseColor("#DCFCE7"), Color.parseColor("#15803D"), Color.parseColor("#BBF7D0")}, // Emerald
            {Color.parseColor("#FEF3C7"), Color.parseColor("#B45309"), Color.parseColor("#FDE68A")}, // Amber
            {Color.parseColor("#FFE4E6"), Color.parseColor("#BE123C"), Color.parseColor("#FECDD3")}, // Rose
            {Color.parseColor("#EDE9FE"), Color.parseColor("#6D28D9"), Color.parseColor("#DDD6FE")}  // Indigo
    };

    public NewChatContactAdapter(List<Contact> contacts, OnContactClickListener clickListener) {
        this.originalList = contacts != null ? new ArrayList<>(contacts) : new ArrayList<>();
        this.filteredList = new ArrayList<>(this.originalList);
        this.clickListener = clickListener;
    }

    public void setOnFilterResultListener(OnFilterResultListener listener) {
        this.filterResultListener = listener;
    }

    public void updateContacts(List<Contact> newContacts) {
        this.originalList.clear();
        if (newContacts != null) {
            this.originalList.addAll(newContacts);
        }
        this.filteredList.clear();
        this.filteredList.addAll(this.originalList);
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_new_chat_contact, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        Contact contact = filteredList.get(position);
        holder.textName.setText(contact.getName() != null && !contact.getName().isEmpty() ? contact.getName() : "Unknown");
        holder.textPhone.setText(contact.getPhoneNumber() != null ? contact.getPhoneNumber() : "");

        // Set Avatar Initial & Color
        String initial = getInitial(contact.getName(), contact.getPhoneNumber());
        holder.textInitial.setText(initial);

        int hash = Math.abs((contact.getName() + contact.getPhoneNumber()).hashCode());
        int[] palette = AVATAR_PALETTES[hash % AVATAR_PALETTES.length];
        holder.cardAvatar.setCardBackgroundColor(palette[0]);
        holder.textInitial.setTextColor(palette[1]);
        holder.cardAvatar.setStrokeColor(palette[2]);

        View.OnClickListener selectAction = v -> {
            if (clickListener != null) {
                clickListener.onContactClick(contact);
            }
        };

        holder.itemView.setOnClickListener(selectAction);
        holder.btnChat.setOnClickListener(selectAction);
    }

    private String getInitial(String name, String phone) {
        if (name != null && !name.trim().isEmpty()) {
            return String.valueOf(name.trim().charAt(0)).toUpperCase();
        }
        if (phone != null && !phone.trim().isEmpty()) {
            return "#";
        }
        return "?";
    }

    @Override
    public int getItemCount() {
        return filteredList.size();
    }

    @Override
    public Filter getFilter() {
        return new Filter() {
            @Override
            protected FilterResults performFiltering(CharSequence constraint) {
                String query = (constraint == null) ? "" : constraint.toString().trim().toLowerCase();
                List<Contact> filtered = new ArrayList<>();

                if (query.isEmpty()) {
                    filtered.addAll(originalList);
                } else {
                    for (Contact c : originalList) {
                        String name = c.getName() != null ? c.getName().toLowerCase() : "";
                        String phone = c.getPhoneNumber() != null ? c.getPhoneNumber().replaceAll("[^0-9+]", "") : "";
                        String cleanQuery = query.replaceAll("[^0-9+]", "");

                        if (name.contains(query) || (c.getPhoneNumber() != null && c.getPhoneNumber().toLowerCase().contains(query))
                                || (!cleanQuery.isEmpty() && phone.contains(cleanQuery))) {
                            filtered.add(c);
                        }
                    }
                }

                FilterResults results = new FilterResults();
                results.values = filtered;
                results.count = filtered.size();
                return results;
            }

            @Override
            protected void publishResults(CharSequence constraint, FilterResults results) {
                filteredList.clear();
                if (results.values != null) {
                    filteredList.addAll((List<Contact>) results.values);
                }
                notifyDataSetChanged();
                if (filterResultListener != null) {
                    filterResultListener.onFilterResult(filteredList.size(), constraint != null ? constraint.toString().trim() : "");
                }
            }
        };
    }

    public static class ViewHolder extends RecyclerView.ViewHolder {
        MaterialCardView cardAvatar;
        TextView textInitial;
        TextView textName;
        TextView textPhone;
        View btnChat;

        public ViewHolder(@NonNull View itemView) {
            super(itemView);
            cardAvatar = itemView.findViewById(R.id.card_sheet_avatar);
            textInitial = itemView.findViewById(R.id.text_sheet_avatar_initial);
            textName = itemView.findViewById(R.id.text_sheet_contact_name);
            textPhone = itemView.findViewById(R.id.text_sheet_contact_phone);
            btnChat = itemView.findViewById(R.id.btn_sheet_item_chat);
        }
    }
}

