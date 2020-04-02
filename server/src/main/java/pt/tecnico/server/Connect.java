package pt.tecnico.server;

import pt.tecnico.model.Announcement;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;

/**
 * Connect class to bind the DPAS to a local or remote DBMS
 */
public class Connect {
    private static final String DB_DRIVER = "jdbc:sqlite:dpas.db";

    /**
     * Constructor creating the database, tables, adding the general board if needed, or populating the boards
     */
    public Connect(List<Board> boards, List<Integer> announcements) {
        try (Connection conn = this.connect();
             ResultSet tableBoards = conn.getMetaData()
                     .getTables(null, null, "boards", null);
             ResultSet tableAnnouncements = conn.getMetaData()
                     .getTables(null, null, "announcements", null);
             ResultSet tableRelAnnouncementsReferring = conn.getMetaData()
                     .getTables(null, null, "rel_announcements_referring", null)) {
            if (tableBoards.next() && tableAnnouncements.next() && tableRelAnnouncementsReferring.next()) {
                // Tables exist
                populateBoards(boards, announcements);
            } else {
                // Tables do not exist
                createNewTables();
                insertBoard(Board.genGeneralBoard());
            }
        } catch (SQLException e) {
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
            conn = DriverManager.getConnection(DB_DRIVER);
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
                + "    public_key varchar(392) UNIQUE NOT NULL"         // Base64 encoded board public key
                + ");";

        // SQL statement for creating the announcements table
        String sql_announcements = "CREATE TABLE IF NOT EXISTS announcements (\n"
                + "    id integer PRIMARY KEY AUTOINCREMENT,\n"
                + "    board_id integer NOT NULL,\n"
                + "    public_key varchar(392) NOT NULL,\n"             // Base64 encoded client public key
                + "    signature varchar(344) NOT NULL,\n"              // Base64 encoded announcement signature
                + "    message varchar(255) NOT NULL,\n"                // Content of the announcement, max 255 chars
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
     * @return true if the insert was successful, false otherwise
     */
    protected boolean insertBoard(Board board) {
        String sql = "INSERT INTO boards(public_key) VALUES(?)";
        boolean ret;

        try (Connection conn = this.connect();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, board.getPublicKey());
            ret = (pstmt.executeUpdate() == 1); //if the row count for the executed statement is 1, it succeeded

            ResultSet rs = pstmt.getGeneratedKeys();
            if (ret && rs.next()) {
                board.setId(rs.getInt(1));
            } else {
                throw new NullPointerException("Cannot retrieve last inserted board");
            }
        } catch (SQLException e) {
            System.out.println(e.getMessage());
            ret = false;
        }

        return ret;
    }

    /**
     * Insert a new row into the announcements table
     *
     * @param board        Board to post to
     * @param announcement Announcement to be inserted/posted
     * @return true if the insert was successful, false otherwise
     */
    protected boolean insertAnnouncement(Board board, Announcement announcement) {
        boolean ret;
        boolean flagUpdatedRetToFalse = false;
        String sql = "INSERT INTO announcements(board_id, public_key, signature, message) VALUES(?,?,?,?)";
        String sql_rel = "INSERT INTO rel_announcements_referring(announcement_id, announcement_referring_id) VALUES(?,?)";

        try (Connection conn = this.connect();
             PreparedStatement pstmt = conn.prepareStatement(sql);
             PreparedStatement pstmt_rel = conn.prepareStatement(sql_rel)) {
            pstmt.setInt(1, board.getId());
            pstmt.setString(2, announcement.getOwner());
            pstmt.setString(3, announcement.getSignature());
            pstmt.setString(4, announcement.getMessage());
            ret = (pstmt.executeUpdate() == 1); //if the row count for the executed statement is 1, it succeeded

            ResultSet rs = pstmt.getGeneratedKeys();
            if (ret && rs.next()) {
                announcement.setId(rs.getInt(1));
                pstmt_rel.setInt(1, rs.getInt(1));
                for (Integer relAnnouncement : announcement.getAnnouncements()) {
                    pstmt_rel.setInt(2, relAnnouncement);
                    ret = (!flagUpdatedRetToFalse && (pstmt_rel.executeUpdate() == 1)); // (flagUpdatedRetToFalse ? false : (pstmt_rel.executeUpdate() == 1))
                    if (!flagUpdatedRetToFalse && !ret) flagUpdatedRetToFalse = true;
                }
            } else {
                throw new NullPointerException("Cannot retrieve last inserted announcement");
            }
        } catch (SQLException e) {
            System.out.println(e.getMessage());
            ret = false;
        }
        return ret;
    }

    /**
     * Populate the model from the database
     *
     * @param boards          List of Board that will be populated from the database
     * @param announcementIds List of Announcement ids that will be populated from the database
     * @return true if everything was successful, false otherwise
     */
    @SuppressWarnings("UnusedReturnValue")
    private boolean populateBoards(List<Board> boards, List<Integer> announcementIds) {
        boolean ret = false;
        List<Announcement> announcements;
        String sql_boards = "SELECT id, public_key FROM boards";
        String sql_announcement = "SELECT id, board_id, public_key, signature, message FROM announcements WHERE board_id = ?";
        String sql_rel = "SELECT announcement_id, announcement_referring_id FROM rel_announcements_referring WHERE announcement_id = ?";

        try (Connection conn = this.connect();
             Statement stmt = conn.createStatement();
             ResultSet rs_boards = stmt.executeQuery(sql_boards);
             PreparedStatement pstmt_announcement = conn.prepareStatement(sql_announcement);
             PreparedStatement pstmt_rel = conn.prepareStatement(sql_rel)) {
            ResultSet rs_announcement;
            ResultSet rs_rel;

            // loop through the result set of boards
            while (rs_boards.next()) {
                announcements = new ArrayList<>();
                pstmt_announcement.setInt(1, rs_boards.getInt("id"));
                rs_announcement = pstmt_announcement.executeQuery();
                List<Integer> announcements_rel;

                // loop through the result set of announcements
                while (rs_announcement.next()) {
                    announcements_rel = new ArrayList<>();
                    pstmt_rel.setInt(1, rs_announcement.getInt("id"));
                    rs_rel = pstmt_rel.executeQuery();

                    // loop through the result set of announcements_rel
                    while (rs_rel.next()) {
                        announcements_rel.add(rs_rel.getInt("announcement_referring_id"));
                    }
                    rs_rel.close();

                    announcements.add(new Announcement(rs_announcement.getString("public_key"),
                            rs_announcement.getString("signature"),
                            rs_announcement.getString("message"),
                            announcements_rel,
                            rs_announcement.getInt("id")));
                    announcementIds.add(rs_announcement.getInt("id"));
                }

                rs_announcement.close();
                boards.add(new Board(rs_boards.getString("public_key"), rs_boards.getInt("id"), announcements));
                ret = true;
            }
        } catch (SQLException e) {
            System.out.println(e.getMessage());
            ret = false;
        }
        return ret;
    }
}
