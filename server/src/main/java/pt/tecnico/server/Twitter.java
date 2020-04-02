package pt.tecnico.server;

import pt.tecnico.model.Announcement;
import pt.tecnico.model.ServerInt;

import java.util.ArrayList;
import java.util.List;


/**
 * Twitter base class to abstract the DBMS layer
 */
public class Twitter implements ServerInt {
    private final List<Board> boards = new ArrayList<>();
    private final List<Integer> announcements = new ArrayList<>();
    private final Connect conn;

    public Twitter() {
        conn = new Connect(this.boards, this.announcements); //init the database connection
    }

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
     * Check if an announcement with the specified id had been posted
     *
     * @param announcementsIds List of announcements ids to refer to
     * @return true if found, false otherwise
     */
    private boolean announcementExists(List<Integer> announcementsIds) {
        return this.announcements.containsAll(announcementsIds);
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
        if (findBoard(publicKey) != null)
            throw new IllegalArgumentException("A board with this public key already exists");
        Board b = new Board(publicKey);
        if (conn.insertBoard(b))
            boards.add(b);
    }

    /**
     * Subroutine to post an announcement, meant to be called from post() and postGeneral methods
     *
     * @param key           Base64 encoded String corresponding to the Client public key
     * @param signature     Base64 encoded String corresponding to the announcement signature
     * @param message       String corresponding to the message
     * @param announcements List of announcements ids to refer to
     * @param board         Board to post to
     * @return true if the insert was successful, false otherwise
     */
    private boolean genericPost(String key, String signature, String message, List<Integer> announcements, Board board) {
        boolean ret;
        if (!announcementExists(announcements))
            throw new IllegalArgumentException("One or more referring announcements specified do not exist");

        Announcement announcement = new Announcement(key, signature, message, announcements);
        ret = conn.insertAnnouncement(board, announcement); // insert announcement and update its id
        if (ret) {
            board.post(announcement);
            this.announcements.add(announcement.getId());
        }
        return ret;
    }

    @Override
    public boolean post(String key, String signature, String message, List<Integer> announcements) throws IllegalArgumentException {
        Board b = findBoard(key);
        if (b == null)
            throw new IllegalArgumentException("No such board registered with this key");
        return genericPost(key, signature, message, announcements, b);
    }

    @Override
    public boolean postGeneral(String key, String signature, String message, List<Integer> announcements) throws IllegalArgumentException {
        Board b = boards.get(0); // the general board is the first one
        if (b == null)
            throw new IllegalArgumentException("No general board registered"); // should never happen, in that case a keypair should have been generated earlier
        return genericPost(key, signature, message, announcements, b);
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