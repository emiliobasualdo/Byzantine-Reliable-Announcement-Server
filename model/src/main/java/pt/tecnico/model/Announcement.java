package pt.tecnico.model;

import java.io.Serializable;
import java.security.PublicKey;
import java.util.List;

public class Announcement implements Serializable {
    private PublicKey owner;
    private String message;
    private List<Announcement> announcements;
    private Integer id = null;

    public Announcement(PublicKey user, String message, List<Announcement> announcements) {
        postCheck(message, announcements);
        this.message = message;
        this.announcements = announcements;
        this.owner = user;
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

    public PublicKey getOwner() {
        return owner;
    }

    public String getMessage() {
        return message;
    }

    public List<Announcement> getAnnouncements() {
        return announcements;
    }

    public Integer getId() {
        return id;
    }

    public void setId(Integer id) {
        this.id = id;
    }
}
