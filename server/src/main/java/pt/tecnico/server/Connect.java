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
    private void createNewTables() {
        // SQL statement for creating the boards table
        String sql_boards = "CREATE TABLE IF NOT EXISTS boards (\n"
                + "    id integer PRIMARY KEY AUTOINCREMENT,\n"
                + "    public_key varchar(392) UNIQUE NOT NULL" // Board Base64 encoded public key
                + ");";

        // SQL statement for creating the announcements table
        String sql_announcements = "CREATE TABLE IF NOT EXISTS announcements (\n"
                + "    id integer PRIMARY KEY AUTOINCREMENT,\n"
                + "    board_id integer NOT NULL,\n"
                + "    public_key varchar(392) NOT NULL,\n" // Client Base64 encoded public key
                + "    message varchar(255) NOT NULL,\n" // Content of the announcement, max 255 chars
                + "    FOREIGN KEY (board_id) REFERENCES boards(id)\n"
                + ");";

        // SQL statement for creating the rel_announcements_referring table
        String rel_announcements_referring = "CREATE TABLE IF NOT EXISTS rel_announcements_referring (\n"
                + "    announcement_id integer NOT NULL,\n"
                + "    announcement_referring_id integer NOT NULL,\n"
                + "    PRIMARY KEY (announcement_id, announcement_referring_id),\n"
                + "    FOREIGN KEY (announcement_id) REFERENCES announcements(id),\n"
                + "    FOREIGN KEY (announcement_referring_id) REFERENCES announcements(id)\n"
                + ");";

        try (Connection conn = this.connect();
             Statement stmt = conn.createStatement()) {
            // create new tables
            stmt.execute(sql_boards);
            stmt.execute(sql_announcements);
            stmt.execute(rel_announcements_referring);
        } catch (SQLException e) {
            System.out.println(e.getMessage());
        }
    }

    /**
     * Insert a new row into the boards table
     *
     * @param board Board to be inserted
     * @throws IllegalArgumentException
     * @return true if the insert was successful, false otherwise
     */
    public Boolean insertBoard(Board board) throws IllegalArgumentException {
        String sql = "INSERT INTO boards(public_key) VALUES(?)";
        Boolean ret = false;

        try (Connection conn = this.connect();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, MyCrypto.publicKeyToB64String(board.getPublicKey()));
            pstmt.executeUpdate();

            ResultSet rs = pstmt.getGeneratedKeys();
            if (rs.next()) {
                board.setId(rs.getInt(1));
                ret = true;
            } else {
                throw new NullPointerException("Cannot retrieve last inserted announcement");
            }
        } catch (SQLException e) {
            System.out.println(e.getMessage());
        }

        return ret;
    }

    /**
     * Insert a new row into the announcements table
     *
     * @param board
     * @param announcement
     * @return true if the insert was successful, false otherwise
     * @throws IllegalArgumentException
     */
    public boolean insertAnnouncement(Board board, Announcement announcement) throws IllegalArgumentException { //TODO: add referring announcements
        boolean ret = false;
        String sql = "INSERT INTO announcements(board_id, public_key, message) VALUES(?,?,?)";
        String sql_rel = "INSERT INTO rel_announcements_referring(announcement_id, announcement_referring_id) VALUES(?,?)";

        try (Connection conn = this.connect();
             PreparedStatement pstmt = conn.prepareStatement(sql);
             PreparedStatement pstmt_rel = conn.prepareStatement(sql_rel)) {
            pstmt.setInt(1, board.getId());
            pstmt.setString(2, MyCrypto.publicKeyToB64String(announcement.getOwner()));
            pstmt.setString(3, announcement.getMessage());
            ret = pstmt.executeUpdate() == 1; //if the row count for the executed statement is 1, it succeeded

            ResultSet rs = pstmt.getGeneratedKeys();
            if (rs.next()) {
                pstmt_rel.setInt(1, rs.getInt(1));
                for (Announcement relAnnouncement : announcement.getAnnouncements()) {
                    pstmt_rel.setInt(2, relAnnouncement.getId());
                    ret = pstmt_rel.executeUpdate() == 1;
                }
            } else {
                throw new NullPointerException("Cannot retrieve last inserted announcement");
            }
        } catch (SQLException e) {
            System.out.println(e.getMessage());
        }
        return ret;
    }

    /**
     * Select all rows in the announcements table
     */
    public void selectAll() {
        String sql = "SELECT id, board_id, public_key, message FROM announcements";

        try (Connection conn = this.connect();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {

            // loop through the result set
            while (rs.next()) {
                System.out.println(rs.getInt("id") + "\t" +
                        rs.getInt("board_id") + "\t" +
                        rs.getString("public_key") + "\t" +
                        rs.getString("message"));
            }
        } catch (SQLException e) {
            System.out.println(e.getMessage());
        }
    }
}
