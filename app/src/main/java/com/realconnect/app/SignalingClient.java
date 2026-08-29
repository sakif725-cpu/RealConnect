package com.realconnect.app;

import android.util.Log;
import com.google.firebase.database.ChildEventListener;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;
import com.google.gson.Gson;
import org.webrtc.IceCandidate;
import org.webrtc.SessionDescription;

import java.util.HashMap;
import java.util.Map;

public class SignalingClient {
    private static final String TAG = "SignalingClient";
    private final Gson gson = new Gson();
    private final DatabaseReference dbRef = FirebaseDatabase.getInstance().getReference("calls");
    private final String selfPhone;
    private final SignalingInterface callback;
    private ValueEventListener listener;
    private boolean isDestroyed = false;

    public interface SignalingInterface {
        default void onRemoteOfferReceived(String callerPhone, SessionDescription description) {}
        default void onRemoteAnswerReceived(SessionDescription description) {}
        default void onRemoteIceCandidateReceived(IceCandidate candidate) {}
        default void onCallEnded() {}
    }

    public static class SdpPayload {
        public String type;
        public String sdp;
        public long timestamp;
        public SdpPayload() {} 
        public SdpPayload(SessionDescription description) {
            this.type = description.type.canonicalForm();
            this.sdp = description.description;
            this.timestamp = System.currentTimeMillis();
        }
        public SessionDescription toSdp() {
            return new SessionDescription(SessionDescription.Type.fromCanonicalForm(type), sdp);
        }
    }

    public static class CandidatePayload {
        public String sdp;
        public String sdpMid;
        public int sdpMLineIndex;
        public CandidatePayload() {}
        public CandidatePayload(IceCandidate candidate) {
            this.sdp = candidate.sdp;
            this.sdpMid = candidate.sdpMid;
            this.sdpMLineIndex = candidate.sdpMLineIndex;
        }
        public IceCandidate toCandidate() {
            return new IceCandidate(sdpMid, sdpMLineIndex, sdp);
        }
    }

    public SignalingClient(String selfPhone, SignalingInterface callback) {
        this.selfPhone = ChatRepository.cleanPhone(selfPhone);
        this.callback = callback;
        listenForPayloads();
    }

    public static void clearNode(String phone) {
        String clean = ChatRepository.cleanPhone(phone);
        if (clean.isEmpty()) return;
        FirebaseDatabase.getInstance().getReference("calls").child(clean).removeValue();
    }

    public void sendOffer(String targetPhone, String senderPhone, SessionDescription sdp) {
        String cleanTarget = ChatRepository.cleanPhone(targetPhone);
        String cleanSender = ChatRepository.cleanPhone(senderPhone);
        dbRef.child(cleanTarget).child("caller").setValue(cleanSender);
        dbRef.child(cleanTarget).child("offer").setValue(gson.toJson(new SdpPayload(sdp)));
    }

    public void sendAnswer(String targetPhone, SessionDescription sdp) {
        String cleanTarget = ChatRepository.cleanPhone(targetPhone);
        dbRef.child(cleanTarget).child("answer").setValue(gson.toJson(new SdpPayload(sdp)));
    }

    public void sendIceCandidate(String targetPhone, IceCandidate candidate) {
        String cleanTarget = ChatRepository.cleanPhone(targetPhone);
        dbRef.child(cleanTarget).child("candidates").push().setValue(gson.toJson(new CandidatePayload(candidate)));
    }

    public void endCall(String targetPhone) {
        String cleanTarget = ChatRepository.cleanPhone(targetPhone);
        if (!cleanTarget.isEmpty()) {
            Map<String, Object> updates = new HashMap<>();
            updates.put("end", true);
            updates.put("offer", null);
            updates.put("caller", null);
            dbRef.child(cleanTarget).updateChildren(updates);
        }
        dbRef.child(selfPhone).removeValue();
    }

    private ChildEventListener candidatesListener;

    public void destroy() {
        isDestroyed = true;
        if (listener != null) {
            dbRef.child(selfPhone).removeEventListener(listener);
        }
        if (candidatesListener != null) {
            dbRef.child(selfPhone).child("candidates").removeEventListener(candidatesListener);
        }
    }

    private void listenForPayloads() {
        listener = new ValueEventListener() {
            @Override
            public void onDataChange(DataSnapshot snapshot) {
                if (isDestroyed || !snapshot.exists()) return;

                try {
                    if (snapshot.hasChild("end")) {
                        callback.onCallEnded();
                        dbRef.child(selfPhone).removeValue();
                        return;
                    }

                    if (snapshot.hasChild("offer")) {
                        String data = snapshot.child("offer").getValue(String.class);
                        String caller = snapshot.child("caller").getValue(String.class);
                        
                        // Consume immediately
                        dbRef.child(selfPhone).child("offer").removeValue();
                        dbRef.child(selfPhone).child("caller").removeValue();

                        if (data != null && caller != null) {
                            SdpPayload payload = gson.fromJson(data, SdpPayload.class);
                            // Only process if offer is less than 30 seconds old
                            if (System.currentTimeMillis() - payload.timestamp < 30000) {
                                callback.onRemoteOfferReceived(caller, payload.toSdp());
                            }
                        }
                    }

                    if (snapshot.hasChild("answer")) {
                        String data = snapshot.child("answer").getValue(String.class);
                        dbRef.child(selfPhone).child("answer").removeValue();
                        if (data != null) {
                            callback.onRemoteAnswerReceived(gson.fromJson(data, SdpPayload.class).toSdp());
                        }
                    }
                } catch (Exception e) {
                    Log.e(TAG, "Signaling logic error", e);
                }
            }

            @Override
            public void onCancelled(DatabaseError error) {}
        };
        dbRef.child(selfPhone).addValueEventListener(listener);

        // Dedicated ChildEventListener for ICE candidates to prevent dropping TURN/STUN relay candidates
        candidatesListener = new com.google.firebase.database.ChildEventListener() {
            @Override
            public void onChildAdded(DataSnapshot snapshot, String previousChildName) {
                if (isDestroyed || !snapshot.exists()) return;
                try {
                    String candidateStr = snapshot.getValue(String.class);
                    if (candidateStr != null) {
                        CandidatePayload payload = gson.fromJson(candidateStr, CandidatePayload.class);
                        if (payload != null) {
                            callback.onRemoteIceCandidateReceived(payload.toCandidate());
                        }
                        snapshot.getRef().removeValue();
                    }
                } catch (Exception e) {
                    Log.e(TAG, "Error parsing ICE candidate", e);
                }
            }

            @Override public void onChildChanged(DataSnapshot snapshot, String previousChildName) {}
            @Override public void onChildRemoved(DataSnapshot snapshot) {}
            @Override public void onChildMoved(DataSnapshot snapshot, String previousChildName) {}
            @Override public void onCancelled(DatabaseError error) {}
        };
        dbRef.child(selfPhone).child("candidates").addChildEventListener(candidatesListener);
    }
}