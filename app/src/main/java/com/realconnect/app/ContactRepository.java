package com.realconnect.app;

import android.content.Context;
import android.content.SharedPreferences;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

public class ContactRepository {
    public interface OnContactsChangedListener {
        void onContactsChanged();
    }

    public interface OnNameResolvedListener {
        void onResolved(String displayName);
    }

    private static ContactRepository instance;
    private final ContactDao contactDao;
    private final Context appContext;
    private final SharedPreferences directoryPrefs;
    private final List<OnContactsChangedListener> listeners = new CopyOnWriteArrayList<>();

    private ContactRepository(Context context) {
        this.appContext = context.getApplicationContext();
        this.directoryPrefs = appContext.getSharedPreferences("UserDirectoryPrefs", Context.MODE_PRIVATE);
        AppDatabase db = AppDatabase.getInstance(appContext);
        contactDao = db.contactDao();
        
        // Clean up legacy dummy contacts if present
        for (Contact c : contactDao.getAllContacts()) {
            if ("+1 (555) 019-2834".equals(c.getPhoneNumber()) ||
                "+1 (555) 492-1002".equals(c.getPhoneNumber()) ||
                "+1 (800) 900-3311".equals(c.getPhoneNumber())) {
                contactDao.delete(c);
            }
        }
    }

    public static synchronized ContactRepository getInstance(Context context) {
        if (instance == null) {
            instance = new ContactRepository(context);
        }
        return instance;
    }

    public void addListener(OnContactsChangedListener listener) {
        if (listener != null && !listeners.contains(listener)) {
            listeners.add(listener);
        }
    }

    public void removeListener(OnContactsChangedListener listener) {
        if (listener != null) {
            listeners.remove(listener);
        }
    }

    private void notifyListeners() {
        for (OnContactsChangedListener listener : listeners) {
            try {
                listener.onContactsChanged();
            } catch (Exception ignored) {}
        }
    }

    public List<Contact> getContacts() {
        return contactDao.getAllContacts();
    }

    public void addContact(Contact contact) {
        contactDao.insert(contact);
        notifyListeners();
    }

    public void deleteContact(Contact contact) {
        contactDao.delete(contact);
        notifyListeners();
    }

    public void updateContact(Contact oldContact, Contact newContact) {
        if (!oldContact.getPhoneNumber().equals(newContact.getPhoneNumber())) {
            contactDao.delete(oldContact);
            contactDao.insert(newContact);
        } else {
            contactDao.update(newContact);
        }
        notifyListeners();
    }

    public String findContactByNumber(String number) {
        if (number == null || number.isEmpty()) return null;
        
        // First try exact match
        String name = contactDao.findNameByNumber(number);
        if (name != null) return name;

        // Fallback: compare digits only for formatted numbers
        String digitsOnly = number.replaceAll("[^0-9]", "");
        List<Contact> all = contactDao.getAllContacts();
        for (Contact contact : all) {
            String contactDigits = contact.getPhoneNumber().replaceAll("[^0-9]", "");
            if (contactDigits.equals(digitsOnly)) {
                return contact.getName();
            }
        }
        return null;
    }

    public String getCachedRegisteredName(String phone) {
        String clean = ChatRepository.cleanPhone(phone);
        if (clean.isEmpty()) return null;
        return directoryPrefs.getString("reg_name_" + clean, null);
    }

    public void saveCachedRegisteredName(String phone, String name) {
        String clean = ChatRepository.cleanPhone(phone);
        if (!clean.isEmpty() && name != null && !name.trim().isEmpty()) {
            directoryPrefs.edit().putString("reg_name_" + clean, name.trim()).apply();
        }
    }

    public String getDisplayName(String phone) {
        if (phone == null || phone.isEmpty()) return "Unknown";
        String savedContact = findContactByNumber(phone);
        if (savedContact != null && !savedContact.trim().isEmpty()) {
            return savedContact;
        }
        String registeredName = getCachedRegisteredName(phone);
        if (registeredName != null && !registeredName.trim().isEmpty()) {
            return registeredName;
        }
        return phone;
    }

    public void resolveCallerName(String phone, OnNameResolvedListener listener) {
        if (phone == null || phone.isEmpty()) {
            if (listener != null) listener.onResolved("Unknown");
            return;
        }

        // 1. If manually saved in local contacts, use user's saved name
        String savedContact = findContactByNumber(phone);
        if (savedContact != null && !savedContact.trim().isEmpty()) {
            if (listener != null) listener.onResolved(savedContact);
            return;
        }

        // 2. Check local registered username cache
        String cached = getCachedRegisteredName(phone);
        if (cached != null && !cached.trim().isEmpty()) {
            if (listener != null) listener.onResolved(cached);
        }

        // 3. Query Firebase Realtime Database for registered username
        String clean = ChatRepository.cleanPhone(phone);
        if (clean.isEmpty()) {
            if (cached == null && listener != null) listener.onResolved(phone);
            return;
        }

        FirebaseDatabase.getInstance().getReference("users")
                .child(clean)
                .child("name")
                .addListenerForSingleValueEvent(new ValueEventListener() {
                    @Override
                    public void onDataChange(@NonNull DataSnapshot snapshot) {
                        try {
                            String remoteName = snapshot.getValue(String.class);
                            if (remoteName != null && !remoteName.trim().isEmpty()) {
                                saveCachedRegisteredName(phone, remoteName);
                                String currentSaved = findContactByNumber(phone);
                                if (currentSaved == null) {
                                    if (listener != null) listener.onResolved(remoteName);
                                }
                            } else {
                                if (cached == null && listener != null) {
                                    listener.onResolved(phone);
                                }
                            }
                        } catch (Exception e) {
                            if (cached == null && listener != null) listener.onResolved(phone);
                        }
                    }

                    @Override
                    public void onCancelled(@NonNull DatabaseError error) {
                        if (cached == null && listener != null) {
                            listener.onResolved(phone);
                        }
                    }
                });
    }
}