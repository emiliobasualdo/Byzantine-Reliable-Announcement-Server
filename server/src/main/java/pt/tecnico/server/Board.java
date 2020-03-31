package pt.tecnico.server;


import pt.tecnico.model.Announcement;
import pt.tecnico.model.MyCrypto;

import java.security.NoSuchAlgorithmException;
import java.security.PublicKey;
import java.util.ArrayList;
import java.util.List;

public class Board {
    private PublicKey publicKey;
    private List<Announcement> announcements = new ArrayList<>();
    private Integer id = null;

    public Board(PublicKey publicKey) {
        this.publicKey = publicKey;
    }

    public PublicKey getPublicKey() {
        return publicKey;
    }

    public void post(Announcement announcement) {
        announcements.add(announcement);
    }

    public List<Announcement> getAnnouncements() {
        return announcements;
    }

    public List<Announcement> get(int number) {
        if (number == 0) return announcements;
        int from = announcements.size() -1;
        int to =  number <= announcements.size()? announcements.size() - number: 0;
        return new ArrayList<>(announcements.subList(to, from));
    }

    public static Board genGeneralBoard() throws NoSuchAlgorithmException {
        PublicKey board_key = MyCrypto.generateKeyPair().getPublic(); // TODO: generate keypair for the general board
        return new Board(board_key);
    }

    public Integer getId() {
        return id;
    }

    public void setId(Integer id) {
        this.id = id;
    }
}
