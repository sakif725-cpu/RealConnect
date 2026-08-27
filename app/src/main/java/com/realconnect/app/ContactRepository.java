package com.realconnect.app;

import android.content.Context;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

public class ContactRepository {
    public interface OnContactsChangedListener {
        void onContactsChanged();
    }

    private static ContactRepository instance;
    private final ContactDao contactDao;
    private final List<OnContactsChangedListener> listeners = new CopyOnWriteArrayList<>();

    private ContactRepository(Context context) {
        AppDatabase db = AppDatabase.getInstance(context);
        contactDao = db.contactDao();
        
        // Initial data if database is empty
        if (contactDao.getAllContacts().isEmpty()) {
            contactDao.insert(new Contact("Alice Smith", "+1 (555) 019-2834"));
            contactDao.insert(new Contact("Bob Johnson", "+1 (555) 492-1002"));
            contactDao.insert(new Contact("Secure Relay 1", "+1 (800) 900-3311"));
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
}