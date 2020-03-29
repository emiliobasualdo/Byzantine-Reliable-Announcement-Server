package pt.tecnico.server;

import pt.tecnico.model.Announcement;
import pt.tecnico.model.ServerInt;

import java.security.PublicKey;
import java.util.HashMap;
import java.util.List;
import java.util.Map;


/**
 * Twitter base class to abstract the DBMS layer
 */
public class Twitter implements ServerInt {
    private Map<PublicKey, User> publicKeys = new HashMap<>(); //TODO: populate the hashmap with db values
    private Board generalBoard = new Board(); //TODO: generate keypair for the general board
    private Connect conn = new Connect(generalBoard); //init the database with the general board

    /**
     * Check if a public key is already registered
     *
     * @param key
     * @return the User corresponding to the key if found, null otherwise
     * @throws IllegalArgumentException
     */
    private User publicKeyCheck(PublicKey key) throws IllegalArgumentException {
        User user = publicKeys.get(key);
        if (user == null) throw new IllegalArgumentException("No such key");
        return user;
    }

    /**
     * Check if an announcement posting request has a correct message length and a refers correct announcement count
     * specified
     *
     * @param message
     * @param announcements
     * @throws IllegalArgumentException
     */
    private void postCheck(String message, List<Announcement> announcements) throws IllegalArgumentException {
        if (message == null || message.length() == 0 || message.length() == 255)
            throw new IllegalArgumentException("Message length must be between 0 and 255 characters");
        if (announcements != null && announcements.size() > 1000)
            throw new IllegalArgumentException("Announcements list can not have more than 1000 items");
    }

    /**
     * Check if an announcement reading request has a correct number specified
     *
     * @param number
     */
    private void readCheck(int number) {
        if (number < 0) throw new IllegalArgumentException("Number must be positive");
    }


    @Override
    public void register(PublicKey publicKey) throws IllegalArgumentException {
        publicKeys.put(publicKey, new User(publicKey, "User1")); //TODO: choose the username
        conn.insertBoard(publicKey);
    }

    @Override
    public boolean post(PublicKey key, String message, List<Announcement> announcements) throws IllegalArgumentException {
        publicKeyCheck(key);
        postCheck(message, announcements);
        return conn.insertAnnouncement(key, key, message);
    }

    @Override
    public boolean postGeneral(PublicKey key, String message, List<Announcement> announcements) throws IllegalArgumentException {
        publicKeyCheck(key);
        postCheck(message, announcements);
        return conn.insertAnnouncement(generalBoard.getPublicKey(), key, message);
    }

    @Override
    public List<Announcement> read(PublicKey key, int number) throws IllegalArgumentException {
        readCheck(number);
        return null; //TODO
    }

    @Override
    public List<Announcement> readGeneral(int number) throws IllegalArgumentException {
        readCheck(number);
        return null; //TODO
    }
}