package com.realconnect.app;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentTransaction;
import com.google.android.material.bottomnavigation.BottomNavigationView;
import org.webrtc.SessionDescription;

public class MainActivity extends AppCompatActivity {

    private SignalingClient signalingClient;
    private String currentRegisteredPhone = null;
    private boolean isProcessingCall = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_main);

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.bottom_navigation), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(0, 0, 0, systemBars.bottom);
            return insets;
        });

        BottomNavigationView bottomNav = findViewById(R.id.bottom_navigation);
        bottomNav.setOnItemSelectedListener(item -> {
            Fragment selectedFragment = null;
            int itemId = item.getItemId();

            if (itemId == R.id.nav_contacts) {
                selectedFragment = new ContactsFragment();
            } else if (itemId == R.id.nav_call) {
                selectedFragment = new CallFragment();
            } else if (itemId == R.id.nav_profile) {
                selectedFragment = new ProfileFragment();
            }

            if (selectedFragment != null) {
                loadFragment(selectedFragment);
            }
            return true;
        });

        if (savedInstanceState == null) {
            bottomNav.setSelectedItemId(R.id.nav_contacts);
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        isProcessingCall = false;
        // Small delay to ensure any previous activity's cleanup has finished
        new Handler(Looper.getMainLooper()).postDelayed(this::setupIncomingCallListener, 500);
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
            currentRegisteredPhone = null;
        }
    }

    private void setupIncomingCallListener() {
        SharedPreferences prefs = getSharedPreferences("ProfilePrefs", Context.MODE_PRIVATE);
        String selfPhone = prefs.getString("phone", null);

        if (selfPhone != null && !selfPhone.isEmpty() && !selfPhone.equals(currentRegisteredPhone)) {
            // Force clear stale data before listening
            SignalingClient.clearNode(selfPhone);
            
            currentRegisteredPhone = selfPhone;
            signalingClient = new SignalingClient(selfPhone, new SignalingClient.SignalingInterface() {
                @Override
                public void onRemoteOfferReceived(String callerPhone, SessionDescription description) {
                    if (isProcessingCall) return;
                    isProcessingCall = true;

                    // Stop listening immediately to avoid consuming ICE candidates while checking spam
                    destroySignaling();

                    // AI Spam Detection: Check number before showing call UI
                    AiService.checkSpam(callerPhone, spamResponse -> {
                        Intent intent = new Intent(MainActivity.this, CallingActivity.class);
                        intent.putExtra("IS_INCOMING", true);
                        intent.putExtra("IS_SPAM", spamResponse != null && spamResponse.isSpam);
                        intent.putExtra("SPAM_REASON", spamResponse != null ? spamResponse.reason : null);
                        intent.putExtra("REMOTE_OFFER", description.description);
                        intent.putExtra("CONTACT_PHONE", callerPhone);
                        
                        String callerName = ContactRepository.getInstance(MainActivity.this).findContactByNumber(callerPhone);
                        intent.putExtra("CONTACT_NAME", callerName != null ? callerName : callerPhone);

                        startActivity(intent);
                    });
                }
            });
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        destroySignaling();
    }

    private void loadFragment(Fragment fragment) {
        getSupportFragmentManager().beginTransaction()
                .setTransition(FragmentTransaction.TRANSIT_FRAGMENT_FADE)
                .replace(R.id.fragment_container, fragment)
                .commit();
    }
}
