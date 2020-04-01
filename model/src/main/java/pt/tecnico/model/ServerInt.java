package pt.tecnico.model;

import java.security.InvalidKeyException;
import java.security.PublicKey;
import java.util.List;

public interface ServerInt {

    public void register(PublicKey publicKey) throws IllegalArgumentException;
    public boolean post(PublicKey key, String message, List<Integer> announcements, String signature) throws IllegalArgumentException;
    public boolean postGeneral(PublicKey key, String message, List<Integer> announcements, String signature) throws IllegalArgumentException;
    public List<Announcement> read(PublicKey key, int number) throws IllegalArgumentException, InvalidKeyException;
    public List<Announcement> readGeneral(int number) throws IllegalArgumentException;
}