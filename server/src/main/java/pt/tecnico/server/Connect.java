package pt.tecnico.server;

import pt.tecnico.model.Announcement;
import pt.tecnico.model.MyCrypto;

import java.security.NoSuchAlgorithmException;
import java.security.PublicKey;
import java.sql.*;

/**
 * Connect class to bind the DPAS to a local or remote DBMS
 */
public class Connect {
    private static final String DB_DRIVER = "jdbc:sqlite";
    private static final String DB_FILENAME = "dpas.db";

    /**
     * Constructor creating the database, tables and adding the general board if needed
     *
     * @param twitter Twitter calling object, to populate fields on db connection
     */
    public Connect(Twitter twitter) {
        try (Connection conn = this.connect();
             ResultSet tableBoards = conn.getMetaData()
                     .getTables(null, null, "boards", null);
             ResultSet tableAnnouncements = conn.getMetaData()
                     .getTables(null, null, "announcements", null);
             ResultSet tableRelAnnouncementsReferring = conn.getMetaData()
                     .getTables(null, null, "rel_announcements_referring", null)) {
            if (tableBoards.next() && tableAnnouncements.next() && tableRelAnnouncementsReferring.next()) {
                // Tables exist
                // TODO: add db loading logic
            } else {
                // Tables do not exist
                createNewTables();
                Board generalBoard = Board.genGeneralBoard();
                insertBoard(generalBoard);
            }
        } catch (SQLException | NoSuchAlgorithmException e) {
            System.out.println(e.getMessage());
        }
    }

    /**
     * Connect to the database
     *
     * @return Connection
     */
    private Connection connect() {
        Connection conn = null;
        try {
            // create a connection to the database
            conn = DriverManager.getConnection(DB_DRIVER + ":" + DB_FILENAME);
        } catch (SQLException e) {
            System.out.println(e.getMessage());
        }
        return conn;
    }

    /**
     * Create new tables in the database
     */
    private void createNewTables() {}

    /**
     * Insert a new row into the boards table
     *
     * @param board Board to be inserted
     * @throws IllegalArgumentException
     * @return true if the insert was successful, false otherwise
     */
    public Boolean insertBoard(Board board) throws IllegalArgumentException {return true;}

    /**
     * Insert a new row into the announcements table
     *
     * @param board
     * @param announcement
     * @return true if the insert was successful, false otherwise
     * @throws IllegalArgumentException
     */
    public boolean insertAnnouncement(Board board, Announcement announcement) throws IllegalArgumentException {return true;}

    /**
     * Select all rows in the announcements table
     */
    public void selectAll() { }
}
