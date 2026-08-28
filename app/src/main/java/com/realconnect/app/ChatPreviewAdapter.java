package com.realconnect.app;

import android.content.Context;
import android.graphics.Color;
import android.graphics.Typeface;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class ChatPreviewAdapter extends RecyclerView.Adapter<ChatPreviewAdapter.ChatPreviewViewHolder> {

    public interface OnChatSelectedListener {
        void onChatSelected(String contactName, String contactPhone);
    }

    public interface OnChatLongClickListener {
        void onChatLongClick(Message message, String contactName, String contactPhone);
    }

    private final Context context;
    private final String selfPhone;
    private final OnChatSelectedListener listener;
    private final OnChatLongClickListener longClickListener;
    private final List<Message> chatList = new ArrayList<>();
    private final SimpleDateFormat timeFormat = new SimpleDateFormat("hh:mm a", Locale.getDefault());
    private final SimpleDateFormat dateFormat = new SimpleDateFormat("MMM dd", Locale.getDefault());

    public ChatPreviewAdapter(Context context, String selfPhone, OnChatSelectedListener listener, OnChatLongClickListener longClickListener) {
        this.context = context;
        this.selfPhone = ChatRepository.cleanPhone(selfPhone);
        this.listener = listener;
        this.longClickListener = longClickListener;
    }

    public void setChats(List<Message> chats) {
        chatList.clear();
        if (chats != null) {
            chatList.addAll(chats);
        }
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public ChatPreviewViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_chat_preview, parent, false);
        return new ChatPreviewViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ChatPreviewViewHolder holder, int position) {
        Message message = chatList.get(position);

        String cleanSender = ChatRepository.cleanPhone(message.getSenderPhone());
        String targetPhone = cleanSender.equals(selfPhone) ? message.getReceiverPhone() : message.getSenderPhone();

        String displayName = ContactRepository.getInstance(context).getDisplayName(targetPhone);

        holder.textName.setText(displayName != null && !displayName.isEmpty() ? displayName : "Unknown");
        holder.textLastMessage.setText(message.getText());

        AvatarHelper.loadAvatar(context, holder.imgAvatar, targetPhone, displayName);

        long diffDays = (System.currentTimeMillis() - message.getTimestamp()) / (1000 * 60 * 60 * 24);
        if (diffDays == 0) {
            holder.textTime.setText(timeFormat.format(new Date(message.getTimestamp())));
        } else {
            holder.textTime.setText(dateFormat.format(new Date(message.getTimestamp())));
        }

        // Unread message count badge for this conversation
        int unreadCount = ChatRepository.getInstance(context).getUnreadCountForChat(message.getChatId());
        if (unreadCount > 0) {
            holder.badgeUnreadCount.setVisibility(View.VISIBLE);
            holder.badgeUnreadCount.setText(unreadCount > 99 ? "99+" : String.valueOf(unreadCount));
            holder.textLastMessage.setTextColor(Color.parseColor("#0F172A"));
            holder.textLastMessage.setTypeface(null, Typeface.BOLD);
            holder.textTime.setTextColor(Color.parseColor("#0EA5E9"));
        } else {
            holder.badgeUnreadCount.setVisibility(View.GONE);
            holder.textLastMessage.setTextColor(Color.parseColor("#64748B"));
            holder.textLastMessage.setTypeface(null, Typeface.NORMAL);
            holder.textTime.setTextColor(Color.parseColor("#94A3B8"));
        }

        holder.itemView.setOnClickListener(v -> {
            if (listener != null) {
                listener.onChatSelected(displayName, targetPhone);
            }
        });

        holder.itemView.setOnLongClickListener(v -> {
            if (longClickListener != null) {
                longClickListener.onChatLongClick(message, displayName, targetPhone);
                return true;
            }
            return false;
        });
    }

    @Override
    public int getItemCount() {
        return chatList.size();
    }

    static class ChatPreviewViewHolder extends RecyclerView.ViewHolder {
        TextView textName, textLastMessage, textTime, badgeUnreadCount;
        android.widget.ImageView imgAvatar;

        ChatPreviewViewHolder(@NonNull View itemView) {
            super(itemView);
            textName = itemView.findViewById(R.id.text_chat_name);
            textLastMessage = itemView.findViewById(R.id.text_chat_last_message);
            textTime = itemView.findViewById(R.id.text_chat_time);
            badgeUnreadCount = itemView.findViewById(R.id.badge_unread_count);
            imgAvatar = itemView.findViewById(R.id.img_chat_avatar);
        }
    }
}