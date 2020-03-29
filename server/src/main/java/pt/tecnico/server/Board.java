package pt.tecnico.server;


import pt.tecnico.model.Announcement;

import java.security.PublicKey;
import java.util.ArrayList;
import java.util.List;

public class Board {
    private PublicKey publicKey;
    private List<Announcement> announcements = new ArrayList<>();;

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
}
