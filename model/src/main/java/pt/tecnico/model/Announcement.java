package pt.tecnico.model;

import java.io.Serializable;
import java.util.List;
import java.util.stream.Collectors;

public class Announcement implements Serializable, Comparable {
    private String owner;
    private String message;
    private List<Integer> announcements;
    private Integer id = null;
    private String signature;

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
        Integer compareId = ((Announcement)o).getId();
        return this.id-compareId;
    }

    /**
     * Check if an announcement posting request has a correct message length and a refers correct announcement count
     * specified
     *
     * @param message
     * @param announcements
     * @throws IllegalArgumentException
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

    public static List<Integer> announcementsListToIntegers(List<Announcement> announcements) {
        return announcements.stream().map(Announcement::getId).collect(Collectors.toList());
    }
}
