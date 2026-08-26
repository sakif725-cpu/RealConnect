package com.realconnect.app;

import android.content.Context;
import java.util.List;

public class ContactRepository {
    private static ContactRepository instance;
    private final ContactDao contactDao;

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

    public List<Contact> getContacts() {
        return contactDao.getAllContacts();
    }

    public void addContact(Contact contact) {
        contactDao.insert(contact);
    }

    public void deleteContact(Contact contact) {
        contactDao.delete(contact);
    }

    public void updateContact(Contact oldContact, Contact newContact) {
        // Since phoneNumber is the primary key and might change during edit, 
        // we handle it by deleting the old and inserting the new if keys differ,
        // or just updating if they are the same.
        if (!oldContact.getPhoneNumber().equals(newContact.getPhoneNumber())) {
            contactDao.delete(oldContact);
            contactDao.insert(newContact);
        } else {
            contactDao.update(newContact);
        }
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
