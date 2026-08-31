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
        String passedChatId = getIntent().getStringExtra("CHAT_ID");

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
        chatId = (passedChatId != null && !passedChatId.isEmpty())
                ? passedChatId
                : ChatRepository.getChatId(selfPhone, targetPhone);

        initViews();
        loadLocalHistory();
        setupRealtimeListener();
        setupMagicEventListener();
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

        View cardAvatar = findViewById(R.id.card_chat_header_avatar);
        View.OnClickListener whipClickListener = v -> {
            if (WhipEffectManager.isWhipEnabled(ChatActivity.this)) {
                long now = System.currentTimeMillis();
                if (now - lastWhipTime > 7000) {
                    localWhipStreak = 0;
                }
                lastWhipTime = now;
                localWhipStreak++;
                if (localWhipStreak > 5) {
                    localWhipStreak = 1;
                }
                final int currentHit = localWhipStreak;
                WhipEffectManager.triggerWhip(ChatActivity.this, imgAvatar, currentHit, () -> {
                    if (currentHit >= 5) {
                        localWhipStreak = 0;
                    }
                });
                sendMagicWhipEvent(currentHit);
            }
        };
        imgAvatar.setOnClickListener(whipClickListener);
        if (cardAvatar != null) {
            cardAvatar.setOnClickListener(whipClickListener);
        }

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

        adapter = new MessageAdapter(selfPhone, this::showMessageOptionsDialog, this::showThreatDetailsDialog);
        recyclerView.setAdapter(adapter);

        View chatInputBar = findViewById(R.id.chat_input_bar);
        ViewCompat.setOnApplyWindowInsetsListener(chatInputBar, (v, insets) -> {
            Insets navBarInsets = insets.getInsets(WindowInsetsCompat.Type.navigationBars() | WindowInsetsCompat.Type.ime());
            v.setPadding(
                    v.getPaddingLeft(),
                    (int) (10 * getResources().getDisplayMetrics().density),
                    v.getPaddingRight(),
                    navBarInsets.bottom + (int) (10 * getResources().getDisplayMetrics().density)
            );
            return insets;
        });

        btnSend.setOnClickListener(v -> sendMessage());

        editInput.setOnEditorActionListener((v, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_SEND) {
                sendMessage();
                return true;
            }
            return false;
        });
    }

    private void showThreatDetailsDialog(Message message, AiMessageThreatAnalyzer.ThreatReport report) {
        if (isFinishing() || isDestroyed() || message == null || report == null) return;

        android.app.Dialog threatDialog = new android.app.Dialog(this);
        threatDialog.requestWindowFeature(android.view.Window.FEATURE_NO_TITLE);
        View dialogView = android.view.LayoutInflater.from(this).inflate(R.layout.dialog_suspicious_message_details, null);
        threatDialog.setContentView(dialogView);

        com.google.android.material.card.MaterialCardView cardIconBg = dialogView.findViewById(R.id.card_threat_icon_bg);
        ImageView imgShieldIcon = dialogView.findViewById(R.id.img_threat_shield_icon);
        View btnClose = dialogView.findViewById(R.id.btn_threat_close);
        com.google.android.material.card.MaterialCardView cardSeverityBadge = dialogView.findViewById(R.id.card_threat_severity_badge);
        TextView textLevel = dialogView.findViewById(R.id.text_threat_level);
        TextView textScore = dialogView.findViewById(R.id.text_threat_score);
        TextView textCategory = dialogView.findViewById(R.id.text_threat_category);
        TextView textFlaggedQuote = dialogView.findViewById(R.id.text_flagged_message_quote);
        TextView textExplanation = dialogView.findViewById(R.id.text_threat_explanation);
        TextView textIndicators = dialogView.findViewById(R.id.text_threat_indicators);
        TextView textRecommendation = dialogView.findViewById(R.id.text_threat_recommendation);
        com.google.android.material.button.MaterialButton btnBlockSender = dialogView.findViewById(R.id.btn_threat_block_sender);
        com.google.android.material.button.MaterialButton btnDeleteMsg = dialogView.findViewById(R.id.btn_threat_delete_msg);
        com.google.android.material.button.MaterialButton btnDismiss = dialogView.findViewById(R.id.btn_threat_dismiss);

        // Bind data
        textCategory.setText(report.category);
        textFlaggedQuote.setText("\"" + message.getText() + "\"");
        textExplanation.setText(report.explanation);
        textRecommendation.setText(report.recommendation);

        if (report.isSuspicious) {
            textLevel.setText(report.level == AiMessageThreatAnalyzer.ThreatLevel.CRITICAL
                    ? "🚨 CRITICAL THREAT DETECTED" : "⚠️ SUSPICIOUS MESSAGE DETECTED");
            textScore.setText(report.riskScore + "% Risk");
        } else {
            textLevel.setText("🛡️ MESSAGE EVALUATED AS SAFE");
            textScore.setText("Safe (0% Risk)");
        }

        try {
            int color = Color.parseColor(report.level.colorHex);
            int bg = Color.parseColor(report.level.bgHex);
            int stroke = Color.parseColor(report.level.strokeHex);

            cardIconBg.setCardBackgroundColor(bg);
            imgShieldIcon.setColorFilter(color);
            cardSeverityBadge.setCardBackgroundColor(bg);
            cardSeverityBadge.setStrokeColor(stroke);
            textLevel.setTextColor(color);
            textScore.setTextColor(color);
        } catch (Exception ignored) {}

        if (report.indicators != null && !report.indicators.isEmpty()) {
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < report.indicators.size(); i++) {
                sb.append(report.indicators.get(i));
                if (i < report.indicators.size() - 1) sb.append("\n");
            }
            textIndicators.setText(sb.toString());
            textIndicators.setVisibility(View.VISIBLE);
        } else {
            textIndicators.setVisibility(View.GONE);
        }

        // Deep async AI enrichment
        AiMessageThreatAnalyzer.analyzeAsync(message.getId(), message.getText(), enriched -> {
            if (!isFinishing() && !isDestroyed() && enriched != null && threatDialog.isShowing()) {
                textExplanation.setText(enriched.explanation);
                textRecommendation.setText(enriched.recommendation);
                if (enriched.indicators != null && !enriched.indicators.isEmpty()) {
                    StringBuilder sb = new StringBuilder();
                    for (int i = 0; i < enriched.indicators.size(); i++) {
                        sb.append(enriched.indicators.get(i));
                        if (i < enriched.indicators.size() - 1) sb.append("\n");
                    }
                    textIndicators.setText(sb.toString());
                    textIndicators.setVisibility(View.VISIBLE);
                }
            }
        });

        btnClose.setOnClickListener(v -> threatDialog.dismiss());

        // Block Sender Action
        btnBlockSender.setOnClickListener(v -> {
            threatDialog.dismiss();
            BlockedNumbersManager.blockNumber(this, targetPhone);
            Toast.makeText(this, "Blocked " + (targetName != null && !targetName.isEmpty() ? targetName : targetPhone), Toast.LENGTH_SHORT).show();
        });

        // Delete Message Action
        btnDeleteMsg.setOnClickListener(v -> {
            threatDialog.dismiss();
            chatRepo.deleteMessage(message.getId());
            loadLocalHistory();
            Toast.makeText(this, "Suspicious message deleted", Toast.LENGTH_SHORT).show();
        });

        // Dismiss / Mark Safe Action
        btnDismiss.setOnClickListener(v -> {
            threatDialog.dismiss();
            AiMessageThreatAnalyzer.markAsDismissed(message.getId());
            adapter.notifyDataSetChanged();
            Toast.makeText(this, "Warning dismissed", Toast.LENGTH_SHORT).show();
        });

        threatDialog.show();
        if (threatDialog.getWindow() != null) {
            threatDialog.getWindow().setBackgroundDrawable(new android.graphics.drawable.ColorDrawable(android.graphics.Color.TRANSPARENT));
            threatDialog.getWindow().setLayout(
                    android.view.ViewGroup.LayoutParams.MATCH_PARENT,
                    android.view.ViewGroup.LayoutParams.WRAP_CONTENT
            );
            threatDialog.getWindow().setGravity(android.view.Gravity.CENTER);
        }
    }

    private void showMessageOptionsDialog(Message message) {
        if (isFinishing() || isDestroyed()) return;

        android.app.Dialog floatingDialog = new android.app.Dialog(this);
        floatingDialog.requestWindowFeature(android.view.Window.FEATURE_NO_TITLE);
        View dialogView = android.view.LayoutInflater.from(this).inflate(R.layout.dialog_message_options, null);
        floatingDialog.setContentView(dialogView);

        View actionCopy = dialogView.findViewById(R.id.action_copy_msg);
        View actionShare = dialogView.findViewById(R.id.action_share_msg);
        View actionScanAi = dialogView.findViewById(R.id.action_scan_ai);
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

        // 3. AI Security Scan
        if (actionScanAi != null) {
            actionScanAi.setOnClickListener(v -> {
                floatingDialog.dismiss();
                AiMessageThreatAnalyzer.ThreatReport report = AiMessageThreatAnalyzer.analyzeSync(message.getId(), message.getText());
                showThreatDetailsDialog(message, report);
            });
        }

        // 4. Delete Message
        actionDelete.setOnClickListener(v -> {
            floatingDialog.dismiss();

            android.app.Dialog confirmDialog = new android.app.Dialog(this);
            confirmDialog.requestWindowFeature(android.view.Window.FEATURE_NO_TITLE);
            View confirmView = android.view.LayoutInflater.from(this).inflate(R.layout.dialog_confirm_action, null);
            confirmDialog.setContentView(confirmView);

            android.widget.TextView textTitle = confirmView.findViewById(R.id.text_confirm_title);
            android.widget.TextView textMsg = confirmView.findViewById(R.id.text_confirm_message);
            android.widget.ImageView imgIcon = confirmView.findViewById(R.id.img_confirm_icon);
            com.google.android.material.card.MaterialCardView iconBg = confirmView.findViewById(R.id.card_confirm_icon_bg);
            com.google.android.material.button.MaterialButton btnAction = confirmView.findViewById(R.id.btn_confirm_action);
            View btnCancel = confirmView.findViewById(R.id.btn_confirm_cancel);

            textTitle.setText("Delete Message");
            textMsg.setText("Are you sure you want to delete this message?");
            imgIcon.setImageResource(R.drawable.ic_delete);
            imgIcon.setColorFilter(android.graphics.Color.parseColor("#EF4444"));
            iconBg.setCardBackgroundColor(android.graphics.Color.parseColor("#FEF2F2"));
            btnAction.setText("Delete");
            btnAction.setBackgroundColor(android.graphics.Color.parseColor("#EF4444"));

            btnCancel.setOnClickListener(cv -> confirmDialog.dismiss());
            btnAction.setOnClickListener(cv -> {
                confirmDialog.dismiss();
                chatRepo.deleteMessage(message.getId());
                loadLocalHistory();
                Toast.makeText(this, "Message deleted", Toast.LENGTH_SHORT).show();
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

    private void loadHeaderAvatar(ImageView imgAvatar, String phone, String name) {
        AvatarHelper.loadAvatar(this, imgAvatar, phone, name);
    }

    private ChatRepository.OnMessageReceivedListener globalMessageListener;

    private void loadLocalHistory() {
        String passedChatId = getIntent().getStringExtra("CHAT_ID");
        List<Message> localMessages = chatRepo.getLocalMessages(passedChatId, selfPhone, targetPhone);
        adapter.setMessages(localMessages);
        if (adapter.getItemCount() > 0) {
            recyclerView.scrollToPosition(adapter.getItemCount() - 1);
        }
        chatRepo.markAsRead(chatId, selfPhone);
    }

    private void setupRealtimeListener() {
        String passedChatId = getIntent().getStringExtra("CHAT_ID");
        chatRepo.startListeningForMessages(selfPhone, targetPhone, passedChatId, message -> runOnUiThread(() -> {
            if (message != null) {
                adapter.addMessage(message);
                if (adapter.getItemCount() > 0) {
                    recyclerView.smoothScrollToPosition(adapter.getItemCount() - 1);
                }
                chatRepo.markAsRead(chatId, selfPhone);
            }
        }));

        globalMessageListener = message -> {
            if (message != null) {
                String cleanSelf = ChatRepository.cleanPhone(selfPhone);
                String cleanTarget = ChatRepository.cleanPhone(targetPhone);
                String msgSender = ChatRepository.cleanPhone(message.getSenderPhone());
                String msgReceiver = ChatRepository.cleanPhone(message.getReceiverPhone());
                String msgChatId = message.getChatId();

                boolean belongsToThisChat = (chatId != null && chatId.equals(msgChatId))
                        || (passedChatId != null && passedChatId.equals(msgChatId))
                        || (cleanSelf.equals(msgSender) && cleanTarget.equals(msgReceiver))
                        || (cleanTarget.equals(msgSender) && cleanSelf.equals(msgReceiver))
                        || (targetPhone != null && (targetPhone.equals(message.getSenderPhone()) || targetPhone.equals(message.getReceiverPhone())));

                if (belongsToThisChat) {
                    runOnUiThread(() -> {
                        adapter.addMessage(message);
                        if (adapter.getItemCount() > 0) {
                            recyclerView.smoothScrollToPosition(adapter.getItemCount() - 1);
                        }
                        chatRepo.markAsRead(chatId, selfPhone);
                    });
                }
            }
        };
        chatRepo.addGlobalListener(globalMessageListener);
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
        loadLocalHistory();
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

                // INSTANT RINGING: Resolve local contact name synchronously and launch CallingActivity immediately!
                String localName = ContactRepository.getInstance(ChatActivity.this).findContactByNumber(callerPhone);
                String displayName = (localName != null && !localName.isEmpty()) ? localName : callerPhone;

                Intent intent = new Intent(ChatActivity.this, CallingActivity.class);
                intent.putExtra("IS_INCOMING", true);
                intent.putExtra("IS_SPAM", false);
                intent.putExtra("REMOTE_OFFER", description.description);
                intent.putExtra("CONTACT_PHONE", callerPhone);
                intent.putExtra("CONTACT_NAME", displayName);

                startActivity(intent);
            }
        });
    }

    private int localWhipStreak = 0;
    private long lastWhipTime = 0;
    private com.google.firebase.database.ValueEventListener magicEventListener;

    private void sendMagicWhipEvent(int hitCount) {
        if (chatId == null || chatId.isEmpty()) return;
        java.util.Map<String, Object> whipData = new java.util.HashMap<>();
        whipData.put("action", "whip");
        whipData.put("hitCount", hitCount);
        whipData.put("sender", selfPhone);
        whipData.put("timestamp", com.google.firebase.database.ServerValue.TIMESTAMP);

        com.google.firebase.database.FirebaseDatabase.getInstance()
                .getReference("chats")
                .child(chatId)
                .child("magic_event")
                .setValue(whipData);
    }

    private void setupMagicEventListener() {
        if (chatId == null || chatId.isEmpty()) return;
        long activityStartTime = System.currentTimeMillis();

        magicEventListener = new com.google.firebase.database.ValueEventListener() {
            @Override
            public void onDataChange(@NonNull com.google.firebase.database.DataSnapshot snapshot) {
                try {
                    if (isFinishing() || isDestroyed()) return;
                    if (!snapshot.exists()) return;

                    String action = snapshot.child("action").getValue(String.class);
                    String sender = snapshot.child("sender").getValue(String.class);
                    Long timestamp = snapshot.child("timestamp").getValue(Long.class);
                    Long hitCountLong = snapshot.child("hitCount").getValue(Long.class);
                    int hitCount = (hitCountLong != null) ? hitCountLong.intValue() : 1;

                    if ("whip".equals(action) && sender != null && !ChatRepository.cleanPhone(sender).equals(ChatRepository.cleanPhone(selfPhone))) {
                        if (timestamp != null && (timestamp >= (activityStartTime - 2000) || (System.currentTimeMillis() - timestamp) < 7000)) {
                            if (WhipEffectManager.isWhipEnabled(ChatActivity.this)) {
                                runOnUiThread(() -> {
                                    ImageView imgAvatar = findViewById(R.id.img_chat_header_avatar);
                                    WhipEffectManager.triggerWhip(ChatActivity.this, imgAvatar, hitCount, () -> {});
                                    String name = (targetName != null && !targetName.isEmpty() ? targetName : targetPhone);
                                    if (hitCount >= 5) {
                                        Toast.makeText(ChatActivity.this, "💥 " + name + "'s profile picture shattered into pieces!", Toast.LENGTH_SHORT).show();
                                    } else {
                                        Toast.makeText(ChatActivity.this, "💥 Whipped by " + name + "! (" + hitCount + "/5)", Toast.LENGTH_SHORT).show();
                                    }
                                });
                            }
                        }
                    }
                } catch (Exception ignored) {}
            }

            @Override
            public void onCancelled(@NonNull com.google.firebase.database.DatabaseError error) {}
        };

        com.google.firebase.database.FirebaseDatabase.getInstance()
                .getReference("chats")
                .child(chatId)
                .child("magic_event")
                .addValueEventListener(magicEventListener);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (globalMessageListener != null) {
            chatRepo.removeGlobalListener(globalMessageListener);
        }
        destroySignaling();
        if (chatRepo != null && chatId != null) {
            chatRepo.stopListeningForMessages(chatId);
        }
        if (magicEventListener != null && chatId != null) {
            com.google.firebase.database.FirebaseDatabase.getInstance()
                    .getReference("chats")
                    .child(chatId)
                    .child("magic_event")
                    .removeEventListener(magicEventListener);
        }
    }
}