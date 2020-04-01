package pt.tecnico.server;

import pt.tecnico.model.Announcement;

import java.io.Serializable;
import java.security.PublicKey;
import java.util.List;

public class User implements Serializable {
    private PublicKey pb;
    private String name;
    private Board board;

    public User(PublicKey pb, String name) {
        this.name = name;
        this.pb = pb;
        board = new Board(pb);
    }

    public String getName() {
        return name;
    }

    public PublicKey getPb() {
        return pb;
    }

    public void post(String message, List<Integer> announcements, String signature) {
        board.post(new Announcement(pb, message, announcements, signature));
    }

    public List<Announcement> read(int number) {
        return board.get(number);
    }
}
