package pt.tecnico.server;


import pt.tecnico.model.Announcement;
import pt.tecnico.model.ServerInt;

import java.security.*;
import java.util.HashMap;
import java.util.List;
import java.util.Map;


public class Twitter implements ServerInt {

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


    @Override
    public void register(PublicKey publicKey) throws IllegalArgumentException {

    }

    @Override
    public boolean post(PublicKey key, String message, List<Integer> announcements) throws IllegalArgumentException {
        return false;
    }

    @Override
    public boolean postGeneral(PublicKey key, String message, List<Integer> announcements) throws IllegalArgumentException {
        return false;
    }

    @Override
    public List<Announcement> read(PublicKey key, int number) throws IllegalArgumentException {
        return null;
    }

    @Override
    public List<Announcement> readGeneral(int number) throws IllegalArgumentException {
        return null;
    }
}