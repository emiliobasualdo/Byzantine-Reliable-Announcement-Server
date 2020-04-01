package pt.tecnico.server;

import pt.tecnico.model.Announcement;
import pt.tecnico.model.ServerInt;

import java.security.PublicKey;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;


/**
 * Twitter base class to abstract the DBMS layer
 */
public class Twitter implements ServerInt {
    private Map<PublicKey, User> publicKeys = new HashMap<>();
    private List<Board> boards = new ArrayList<>();
    private Connect conn;

    public Twitter() {
        conn = new Connect(this); //init the database connection
    }

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
     * Check if a public key is already registered as a board
     *
     * @param key PublicKey corresponding to the board
     * @return the Board corresponding to the key if found, null otherwise
     */
    private Board boardCheck(PublicKey key) {
        Board b = boards.stream().filter(board -> key == board.getPublicKey()).findAny().orElse(null); // invoke a stream on the list and return the first element that matches the filter predicate if any
        return b;
    }

    /**
     * Check if an announcement reading request has a correct number specified
     *
     * @param number
     */
    private void readCheck(int number) {
        if (number < 0) throw new IllegalArgumentException("Number must be positive");
    }

    /**
     * Register a user by adding its own board
     *
     * @param publicKey
     * @throws IllegalArgumentException
     */
    @Override
    public void register(PublicKey publicKey) throws IllegalArgumentException {
        Board b = new Board(publicKey);
        boards.add(b);
        conn.insertBoard(b);
    }

    /**
     * Post an announcement in the specified board, if it exists
     *
     * @param key
     * @param message
     * @param announcements
     * @param signature
     * @return
     * @throws IllegalArgumentException
     */
    @Override
    public boolean post(PublicKey key, String message, List<Integer> announcements, String signature) throws IllegalArgumentException {
        Board b = boardCheck(key);
        if (b == null) throw new IllegalArgumentException("No such board registered with this key");
        Announcement announcement = new Announcement(key, message, announcements, signature);
        return conn.insertAnnouncement(b, announcement);
    }

    @Override
    public boolean postGeneral(PublicKey key, String message, List<Integer> announcements, String signature) throws IllegalArgumentException {
        Board b = boards.get(0);
        if (b == null) throw new IllegalArgumentException("No general board registered"); // should never happen, in that case a keypair should have been generated earlier
        Announcement announcement = new Announcement(key, message, announcements, signature);
        return conn.insertAnnouncement(b, announcement); // the general board is the first one
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