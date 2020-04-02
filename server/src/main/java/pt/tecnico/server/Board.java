package pt.tecnico.server;

import pt.tecnico.model.Announcement;
import pt.tecnico.model.MyCrypto;

import java.security.NoSuchAlgorithmException;
import java.security.PublicKey;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class Board {
    private final String publicKey;                     // Board public key identifier
    private final List<Announcement> announcements;     // List of announcements the Board contains
    private Integer id = null;                          // Board id, null if it had not been inserted in the database

    /**
     * @param publicKey PublicKey identifying the Board
     */
    public Board(String publicKey) {
        this.publicKey = publicKey;
        this.announcements = new ArrayList<>();
    }

    /**
     * @param publicKey     PublicKey identifying the Board
     * @param id            Integer corresponding to the Board id
     * @param announcements List of announcements the Board contains
     */
    public Board(String publicKey, Integer id, List<Announcement> announcements) {
        this.publicKey = publicKey;
        this.id = id;
        this.announcements = announcements;
    }

    /**
     * Instantiate a new general Board, by generating a new keypair
     *
     * @return a new Board with a generated keypair
     */
    public static Board genGeneralBoard() {
        PublicKey board_key = null;
        try {
            board_key = MyCrypto.generateKeyPair().getPublic();
        } catch (NoSuchAlgorithmException e) {
            e.printStackTrace();
        }
        return new Board(MyCrypto.publicKeyToB64String(board_key));
    }

    public String getPublicKey() {
        return publicKey;
    }

    public void addAnnouncement(Announcement announcement) {
        announcements.add(announcement);
    }

    /**
     * @param number Integer corresponding to the number of most recent announcements to get
     * @return a sublist of announcements containing the number most recent announcements
     */
    public List<Announcement> getAnnouncements(int number) {
        //noinspection unchecked
        Collections.sort(announcements); // should already be sorted by id (so, chronological) but we never now...
        if (number == 0)
            return announcements;

        int size = announcements.size();
        int from = number <= size ? size - number : 0;
        return announcements.subList(from, size);
    }

    public Integer getId() {
        return id;
    }

    public void setId(Integer id) {
        this.id = id;
    }
}
