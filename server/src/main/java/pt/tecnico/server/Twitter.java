package pt.tecnico.server;

import pt.tecnico.model.Announcement;
import pt.tecnico.model.ServerInt;

import java.util.ArrayList;
import java.util.List;


/**
 * Twitter base class to abstract the DBMS layer
 */
public class Twitter implements ServerInt {
    //private Map<PublicKey, User> publicKeys = new HashMap<>();
    private List<Board> boards = new ArrayList<>();
    private Connect conn;

    public Twitter() {
        conn = new Connect(this.boards); //init the database connection
    }

    /*
    private User publicKeyCheck(PublicKey key) throws IllegalArgumentException {
        User user = publicKeys.get(key);
        if (user == null)
            throw new IllegalArgumentException("No such key");
        return user;
    }
    */

    /**
     * Check if a public key is already registered as a board
     *
     * @param key PublicKey corresponding to the board
     * @return the Board corresponding to the key if found, null otherwise
     */
    private Board findBoard(String key) {
        return this.boards.stream().filter(board -> key.equals(board.getPublicKey())).findAny().orElse(null);
    }

    /**
     * Check if an announcement reading request has a correct number specified
     *
     * @param number int corresponding to the number of announcements to read
     */
    private void readCheck(int number) {
        if (number < 0)
            throw new IllegalArgumentException("Number must be positive");
    }

    @Override
    public void register(String publicKey) throws IllegalArgumentException {
        //publicKeys.put(publicKey, new User(publicKey, "User1")); // TODO: choose the username
        if (findBoard(publicKey) != null)
            throw new IllegalArgumentException("A board with this public key already exists");
        Board b = new Board(publicKey);
        if (conn.insertBoard(b))
            boards.add(b);
    }

    @Override
    public boolean post(String key, String signature, String message, List<Announcement> announcements) throws IllegalArgumentException {
        boolean ret;
        Board b = findBoard(key);
        if (b == null)
            throw new IllegalArgumentException("No such board registered with this key");

        Announcement announcement = new Announcement(key, signature, message, Announcement.announcementsListToIntegers(announcements));
        ret = conn.insertAnnouncement(b, announcement); // insert announcement and update its id
        if (ret)
            b.post(announcement);
        return ret;
    }

    @Override
    public boolean postGeneral(String key, String signature, String message, List<Announcement> announcements) throws IllegalArgumentException {
        boolean ret;
        Board b = boards.get(0); // the general board is the first one
        if (b == null)
            throw new IllegalArgumentException("No general board registered"); // should never happen, in that case a keypair should have been generated earlier

        Announcement announcement = new Announcement(key, signature, message, Announcement.announcementsListToIntegers(announcements));
        ret = conn.insertAnnouncement(b, announcement); // insert announcement and update its id
        if (ret)
            b.post(announcement);
        return ret;
    }

    @Override
    public List<Announcement> read(String key, int number) throws IllegalArgumentException {
        readCheck(number);
        Board b = findBoard(key);
        if (b == null)
            throw new IllegalArgumentException("No such board registered with this key");
        return b.get(number);
    }

    @Override
    public List<Announcement> readGeneral(int number) throws IllegalArgumentException {
        readCheck(number);
        Board b = boards.get(0); // the general board is the first one
        if (b == null)
            throw new IllegalArgumentException("No general board registered"); // should never happen, in that case a keypair should have been generated earlier
        return b.get(number);
    }
}