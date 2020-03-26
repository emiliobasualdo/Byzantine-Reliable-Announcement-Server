package pt.tecnico.server;


import pt.tecnico.model.Announcement;

import java.security.*;
import java.util.HashMap;
import java.util.List;
import java.util.Map;


public class Twitter {

    private Map<PublicKey, User> publicKeys = new HashMap<>();
    private Board generalBoard = new Board();

    private User publicKeyCheck(PublicKey key) {
        User user = publicKeys.get(key);
        if (user == null) throw new IllegalArgumentException("No such key");
        return user;
    }

    private void messageAndAnnouncemntsCheck(String message, List<Announcement> announcements) {
        if (message == null || message.length() == 0 || message.length() == 255) throw new IllegalArgumentException("Message must have between 0 and 255 characters");
        if (announcements != null && announcements.size() > 1000) throw new IllegalArgumentException("Announcements list can not have more than 1000 items");
    }

    private void numberheck(int number) {
        if (number < 0 ) throw new IllegalArgumentException("Number must be positive");
    }

    public void register(PublicKey publicKey, String name) {
        if (publicKeys.containsKey(publicKey) && !publicKeys.get(publicKey).getName().equals(name)) throw new IllegalArgumentException("Key already exists but name does not match");
        if (name.length() == 0) throw new IllegalArgumentException("Name can not be null");
        System.out.println("Registering "+ name);
        publicKeys.put(publicKey, new User(publicKey, name)); // TODO should the keys be already pre-loaded?
    }

    public boolean post(PublicKey key, String message, List<Announcement> announcements) {
        // TODO validate that each of the announcements made does actually exist?
        User user = publicKeyCheck(key);
        messageAndAnnouncemntsCheck(message, announcements);
        System.out.println("posting "+ message);
        user.post(message, announcements);
        return true;
    }

    public boolean postGeneral(PublicKey key, String message, List<Announcement> announcements) {
        publicKeyCheck(key);
        messageAndAnnouncemntsCheck(message, announcements);
        System.out.println("posting in general"+ message);
        generalBoard.post(new Announcement(key, message, announcements));
        return true;
    }

    public List<Announcement> read(PublicKey key, int number) {
        User user = publicKeyCheck(key);
        numberheck(number);
        System.out.println("reading");
        return user.read(number);
    }

    public List<Announcement> readGeneral(int number) {
        numberheck(number);
        System.out.println("reading general");
        return generalBoard.get(number);
    }
}