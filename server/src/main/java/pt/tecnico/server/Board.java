package pt.tecnico.server;


import pt.tecnico.model.Announcement;
import pt.tecnico.model.MyCrypto;

import java.security.NoSuchAlgorithmException;
import java.security.PublicKey;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class Board {
    private String publicKey;
    private List<Announcement> announcements;
    private Integer id = null;

    public Board(String publicKey) {
        this.publicKey = publicKey;
        this.announcements =new ArrayList<>();
    }

    public Board(String publicKey, Integer id, List<Announcement> announcements) {
        this.publicKey = publicKey;
        this.id = id;
        this.announcements = announcements;
    }

    public String getPublicKey() {
        return publicKey;
    }

    public void post(Announcement announcement) {
        announcements.add(announcement);
    }

    public List<Announcement> getAnnouncements() {
        return announcements;
    }

    public List<Announcement> get(int number) {
        Collections.sort(announcements); // should already be sorted by id (so, chronological) but we never now...
        if (number == 0) return announcements;

        int size = announcements.size();
        int from = number <= size ? size - number : 0;
        return announcements.subList(from, size);
    }

    public static Board genGeneralBoard() throws NoSuchAlgorithmException {
        PublicKey board_key = MyCrypto.generateKeyPair().getPublic(); // TODO: generate keypair for the general board
        return new Board(MyCrypto.publicKeyToB64String(board_key));
    }

    public Integer getId() {
        return id;
    }

    public void setId(Integer id) {
        this.id = id;
    }
}
