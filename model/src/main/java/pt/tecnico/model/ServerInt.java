package pt.tecnico.model;

import java.util.List;

/**
 * Server interface, to implement required DPAS methods on the server side
 */
public interface ServerInt {
    /**
     * Register a user by adding its own board
     *
     * @param publicKey Base64 encoded String corresponding to the Client public key
     * @throws IllegalArgumentException in case the client already registered his board
     */
    void register(String publicKey) throws IllegalArgumentException;

    /**
     * Post an announcement in the specified board, if it exists
     *
     * @param key           Base64 encoded String corresponding to the Client public key
     * @param signature     Base64 encoded String corresponding to the announcement signature
     * @param message       String corresponding to the message
     * @param announcements List of announcements ids to refer to
     * @return true if the insert was successful, false otherwise
     * @throws IllegalArgumentException in case we can't find the board (most likely because the provided key is not registered)
     */
    @SuppressWarnings("UnusedReturnValue")
    boolean post(String key, String signature, String message, List<Integer> announcements) throws IllegalArgumentException;

    /**
     * Post an announcement in the general board, if it exists
     *
     * @param key           Base64 encoded String corresponding to the Client public key
     * @param signature     Base64 encoded String corresponding to the announcement signature
     * @param message       String corresponding to the message
     * @param announcements List of announcements ids to refer to
     * @return true if the insert was successful, false otherwise
     * @throws IllegalArgumentException in case we can't find the board
     */
    @SuppressWarnings("UnusedReturnValue")
    boolean postGeneral(String key, String signature, String message, List<Integer> announcements) throws IllegalArgumentException;

    /**
     * Read announcements from the specified board, if it exists and contains announcements
     *
     * @param key    Base64 encoded String corresponding to the Board public key
     * @param number int corresponding to the number of announcements to read (0 for all announcements)
     * @return a List of the number last posted announcements, or all announcements in case there are less than number posted.
     * @throws IllegalArgumentException in case we can't find the board (most likely because the provided key is not registered)
     */
    List<Announcement> read(String key, int number) throws IllegalArgumentException;

    /**
     * Read announcements from the general board, if it exists and contains announcements
     *
     * @param number int corresponding to the number of announcements to read (0 for all announcements)
     * @return a List of the number last posted announcements, or all announcements in case there are less than number posted.
     * @throws IllegalArgumentException in case we can't find the board
     */
    List<Announcement> readGeneral(int number) throws IllegalArgumentException;
}