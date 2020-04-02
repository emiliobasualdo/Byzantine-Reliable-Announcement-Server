package pt.tecnico.model;

import java.util.List;

/**
 * Announcement class to gather the sender, the message, the list of announcements it refers to, its database id and its signature
 */
public class Announcement implements Comparable {
    private final String owner;                     // Base64 encoded client public key
    private final String message;                   // Content of the announcement, max 255 chars
    private final List<Integer> announcements;      // List of announcements ids to refer to
    private final String signature;                 // Base64 encoded announcement signature
    private Integer id = null;                      // Announcement id if it had been inserted in the db

    public Announcement(String user, String signature, String message, List<Integer> announcements) {
        postCheck(message, announcements);
        this.message = message;
        this.announcements = announcements;
        this.owner = user;
        this.signature = signature;
    }

    public Announcement(String user, String signature, String message, List<Integer> announcements, Integer id) {
        this(user, signature, message, announcements);
        this.setId(id);
    }

    @Override
    public int compareTo(Object o) {
        Integer compareId = ((Announcement) o).getId();
        return this.id - compareId;
    }

    /**
     * Check if an announcement posting request has a correct message length and a refers correct announcement count specified
     *
     * @param message       String corresponding to the announcements message to post
     * @param announcements List of announcements ids to refer to
     * @throws IllegalArgumentException in case the specified message and list of announcements do not respect the criteria
     */
    private void postCheck(String message, List<Integer> announcements) throws IllegalArgumentException {
        if (message == null || message.length() == 0 || message.length() == 255)
            throw new IllegalArgumentException("Message length must be between 0 and 255 characters");
        if (announcements != null && announcements.size() > 1000)
            throw new IllegalArgumentException("Announcements list can not have more than 1000 items");
    }

    public String getOwner() {
        return owner;
    }

    public String getMessage() {
        return message;
    }

    public List<Integer> getAnnouncements() {
        return announcements;
    }

    public Integer getId() {
        return id;
    }

    public void setId(Integer id) {
        this.id = id;
    }

    public String getSignature() {
        return signature;
    }
}
