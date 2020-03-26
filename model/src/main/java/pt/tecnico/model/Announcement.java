package pt.tecnico.model;

import java.io.Serializable;
import java.security.PublicKey;
import java.util.List;

public class Announcement implements Serializable {
    private PublicKey owner; // if user is null we don't mind knowing;
    private String message;
    private List<Announcement> announcements;

    public Announcement(PublicKey user, String message, List<Announcement> announcements) {
        this.message = message;
        this.announcements = announcements;
        this.owner = user;
    }

    public Announcement(String message, List<Announcement> announcements) {
        this(null, message, announcements);
    }

    public PublicKey getOwner() {
        return owner;
    }

    public String getMessage() {
        return message;
    }

    public List<Announcement> getAnnouncements() {
        return announcements;
    }
}
