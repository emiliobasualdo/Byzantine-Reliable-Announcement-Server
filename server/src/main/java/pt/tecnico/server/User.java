package pt.tecnico.server;

import pt.tecnico.model.Announcement;
import pt.tecnico.model.MyCrypto;

import java.io.Serializable;
import java.security.NoSuchAlgorithmException;
import java.security.spec.InvalidKeySpecException;
import java.util.List;

public class User implements Serializable {
    private String pb;
    private String name;
    private Board board;

    public User(String pb, String name) throws InvalidKeySpecException, NoSuchAlgorithmException {
        this.name = name;
        this.pb = pb;
        board = new Board(pb);
    }

    public String getName() {
        return name;
    }

    public String getPb() {
        return pb;
    }

    public void post(String message, String signature, List<Announcement> announcements) {
        board.post(new Announcement(pb, signature, message, Announcement.announcementsListToIntegers(announcements)));
    }

    public List<Announcement> read(int number) {
        return board.get(number);
    }
}
