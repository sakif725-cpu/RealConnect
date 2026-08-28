package com.realconnect.app;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.TextUtils;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;
import org.webrtc.SessionDescription;
import java.util.List;

public class ChatActivity extends AppCompatActivity {

    private String selfPhone;
    private String targetPhone;
    private String targetName;
    private String chatId;

    private MessageAdapter adapter;
    private RecyclerView recyclerView;
    private EditText editInput;
    private ChatRepository chatRepo;

    private SignalingClient signalingClient;
    private boolean isProcessingCall = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_chat);

        SharedPreferences prefs = getSharedPreferences("ProfilePrefs", Context.MODE_PRIVATE);
        selfPhone = prefs.getString("phone", "");

        targetPhone = getIntent().getStringExtra("CONTACT_PHONE");
        targetName = getIntent().getStringExtra("CONTACT_NAME");

        if (selfPhone.isEmpty()) {
            Toast.makeText(this, "Please set your phone number in Profile first.", Toast.LENGTH_LONG).show();
            finish();
            return;
        }

        if (targetPhone == null || targetPhone.isEmpty()) {
            Toast.makeText(this, "Target contact phone missing.", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        chatRepo = ChatRepository.getInstance(this);
        chatId = ChatRepository.getChatId(selfPhone, targetPhone);

        initViews();
        loadLocalHistory();
        setupRealtimeListener();
    }

    private void initViews() {
        ImageButton btnBack = findViewById(R.id.btn_chat_back);
        ImageButton btnCall = findViewById(R.id.btn_chat_call);
        TextView textName = findViewById(R.id.text_chat_header_name);
        TextView textPhone = findViewById(R.id.text_chat_header_phone);
        FloatingActionButton btnSend = findViewById(R.id.btn_send_message);
        editInput = findViewById(R.id.edit_message_input);
        recyclerView = findViewById(R.id.recycler_messages);

        View chatHeader = findViewById(R.id.chat_header);
        ViewCompat.setOnApplyWindowInsetsListener(chatHeader, (v, insets) -> {
            Insets statusBarInsets = insets.getInsets(WindowInsetsCompat.Type.statusBars() | WindowInsetsCompat.Type.displayCutout());
            int topOffset = statusBarInsets.top > 0 ? statusBarInsets.top : (int) (16 * getResources().getDisplayMetrics().density);
            v.setPadding(
                    v.getPaddingLeft(),
                    topOffset + (int) (4 * getResources().getDisplayMetrics().density),
                    v.getPaddingRight(),
                    (int) (10 * getResources().getDisplayMetrics().density)
            );
            return insets;
        });

        ImageView imgAvatar = findViewById(R.id.img_chat_header_avatar);
        loadHeaderAvatar(imgAvatar, targetPhone, targetName);

        textName.setText(targetName != null && !targetName.isEmpty() ? targetName : targetPhone);
        textPhone.setText(targetPhone);

        btnBack.setOnClickListener(v -> finish());

        btnCall.setOnClickListener(v -> {
            Intent callIntent = new Intent(ChatActivity.this, CallingActivity.class);
            callIntent.putExtra("CONTACT_NAME", targetName);
            callIntent.putExtra("CONTACT_PHONE", targetPhone);
            startActivity(callIntent);
        });

        LinearLayoutManager layoutManager = new LinearLayoutManager(this);
        layoutManager.setStackFromEnd(true);
        recyclerView.setLayoutManager(layoutManager);

        adapter = new MessageAdapter(selfPhone, this::showMessageOptionsDialog);
        recyclerView.setAdapter(adapter);

        btnSend.setOnClickListener(v -> sendMessage());

        editInput.setOnEditorActionListener((v, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_SEND) {
                sendMessage();
                return true;
            }
            return false;
        });
    }

    private void showMessageOptionsDialog(Message message) {
        if (isFinishing() || isDestroyed()) return;

        android.app.Dialog floatingDialog = new android.app.Dialog(this);
        floatingDialog.requestWindowFeature(android.view.Window.FEATURE_NO_TITLE);
        View dialogView = android.view.LayoutInflater.from(this).inflate(R.layout.dialog_message_options, null);
        floatingDialog.setContentView(dialogView);

        View actionCopy = dialogView.findViewById(R.id.action_copy_msg);
        View actionShare = dialogView.findViewById(R.id.action_share_msg);
        View actionDelete = dialogView.findViewById(R.id.action_delete_msg);

        // 1. Copy Message Text
        actionCopy.setOnClickListener(v -> {
            android.content.ClipboardManager clipboard = (android.content.ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
            android.content.ClipData clip = android.content.ClipData.newPlainText("Message Text", message.getText());
            if (clipboard != null) {
                clipboard.setPrimaryClip(clip);
                Toast.makeText(this, "Message copied", Toast.LENGTH_SHORT).show();
            }
            floatingDialog.dismiss();
        });

        // 2. Share Message
        actionShare.setOnClickListener(v -> {
            floatingDialog.dismiss();
            Intent shareIntent = new Intent(Intent.ACTION_SEND);
            shareIntent.setType("text/plain");
            shareIntent.putExtra(Intent.EXTRA_TEXT, message.getText());
            startActivity(Intent.createChooser(shareIntent, "Share Message"));
        });

        // 3. Delete Message
        actionDelete.setOnClickListener(v -> {
            floatingDialog.dismiss();
            new com.google.android.material.dialog.MaterialAlertDialogBuilder(this)
                    .setTitle("Delete Message")
                    .setMessage("Are you sure you want to delete this message?")
                    .setPositiveButton("Delete", (d, w) -> {
                        chatRepo.deleteMessage(message.getId());
                        loadLocalHistory();
                        Toast.makeText(this, "Message deleted", Toast.LENGTH_SHORT).show();
                    })
                    .setNegativeButton("Cancel", null)
                    .show();
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

    private void loadHeaderAvatar(ImageView imgAvatar, String phone, String name) {
        if (imgAvatar == null) return;

        String displayName = (name != null && !name.trim().isEmpty()) ? name : (phone != null ? phone : "?");
        Bitmap initialAvatar = ImageUtils.createAvatarWithInitial(displayName, 120, Color.parseColor("#E2E8F0"), Color.parseColor("#0F172A"));
        imgAvatar.setPadding(0, 0, 0, 0);
        imgAvatar.setImageTintList(null);
        imgAvatar.setColorFilter(null);
        imgAvatar.setScaleType(ImageView.ScaleType.CENTER_CROP);
        imgAvatar.setImageBitmap(initialAvatar);

        String cleanTarget = ChatRepository.cleanPhone(phone);
        if (!cleanTarget.isEmpty()) {
            FirebaseDatabase.getInstance().getReference("users")
                    .child(cleanTarget)
                    .child("profileImageBase64")
                    .addListenerForSingleValueEvent(new ValueEventListener() {
                        @Override
                        public void onDataChange(@NonNull DataSnapshot snapshot) {
                            try {
                                if (isFinishing() || isDestroyed()) return;
                                String base64 = snapshot.getValue(String.class);
                                if (base64 != null && !base64.trim().isEmpty()) {
                                    Bitmap photo = ImageUtils.base64ToBitmap(base64);
                                    if (photo != null) {
                                        runOnUiThread(() -> {
                                            imgAvatar.setPadding(0, 0, 0, 0);
                                            imgAvatar.setImageTintList(null);
                                            imgAvatar.setColorFilter(null);
                                            imgAvatar.setScaleType(ImageView.ScaleType.CENTER_CROP);
                                            imgAvatar.setImageBitmap(photo);
                                        });
                                    }
                                }
                            } catch (Exception ignored) {}
                        }

                        @Override
                        public void onCancelled(@NonNull DatabaseError error) {}
                    });
        }
    }

    private void loadLocalHistory() {
        List<Message> localMessages = chatRepo.getLocalMessages(chatId);
        adapter.setMessages(localMessages);
        if (adapter.getItemCount() > 0) {
            recyclerView.scrollToPosition(adapter.getItemCount() - 1);
        }
        chatRepo.markAsRead(chatId, selfPhone);
    }

    private void setupRealtimeListener() {
        chatRepo.startListeningForMessages(chatId, message -> runOnUiThread(() -> {
            adapter.addMessage(message);
            if (adapter.getItemCount() > 0) {
                recyclerView.smoothScrollToPosition(adapter.getItemCount() - 1);
            }
            chatRepo.markAsRead(chatId, selfPhone);
        }));
    }

    private void sendMessage() {
        String text = editInput.getText().toString().trim();
        if (TextUtils.isEmpty(text)) return;

        editInput.setText("");

        Message sentMessage = chatRepo.sendMessage(selfPhone, targetPhone, text);
        adapter.addMessage(sentMessage);
        if (adapter.getItemCount() > 0) {
            recyclerView.smoothScrollToPosition(adapter.getItemCount() - 1);
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        isProcessingCall = false;
        new Handler(Looper.getMainLooper()).postDelayed(this::setupIncomingCallListener, 300);
    }

    @Override
    protected void onPause() {
        super.onPause();
        destroySignaling();
    }

    private void destroySignaling() {
        if (signalingClient != null) {
            signalingClient.destroy();
            signalingClient = null;
        }
    }

    private void setupIncomingCallListener() {
        if (selfPhone == null || selfPhone.isEmpty()) return;

        SignalingClient.clearNode(selfPhone);
        signalingClient = new SignalingClient(selfPhone, new SignalingClient.SignalingInterface() {
            @Override
            public void onRemoteOfferReceived(String callerPhone, SessionDescription description) {
                if (isProcessingCall) return;
                isProcessingCall = true;

                destroySignaling();

                AiService.checkSpam(callerPhone, isSpam -> {
                    Intent intent = new Intent(ChatActivity.this, CallingActivity.class);
                    intent.putExtra("IS_INCOMING", true);
                    intent.putExtra("IS_SPAM", isSpam);
                    intent.putExtra("REMOTE_OFFER", description.description);
                    intent.putExtra("CONTACT_PHONE", callerPhone);

                    String callerName = ContactRepository.getInstance(ChatActivity.this).findContactByNumber(callerPhone);
                    intent.putExtra("CONTACT_NAME", callerName != null ? callerName : callerPhone);

                    startActivity(intent);
                });
            }
        });
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        destroySignaling();
        if (chatRepo != null && chatId != null) {
            chatRepo.stopListeningForMessages(chatId);
        }
    }
}