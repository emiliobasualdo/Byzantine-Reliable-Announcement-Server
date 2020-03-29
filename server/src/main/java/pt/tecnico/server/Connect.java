package pt.tecnico.server;

import pt.tecnico.model.MyCrypto;

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
     * @param generalBoard
     */
    public Connect(Board generalBoard) {
        createNewTables();
        //insertBoard(generalBoard.getPublicKey()); //TODO: uncomment after the general board keypair is generated
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
                + "    public_key varchar UNIQUE NOT NULL"
                + ");";

        // SQL statement for creating the announcements table
        String sql_announcements = "CREATE TABLE IF NOT EXISTS announcements (\n"
                + "    id integer PRIMARY KEY AUTOINCREMENT,\n"
                + "    board_id integer NOT NULL,\n"
                + "    public_key varchar NOT NULL,\n"
                + "    message varchar(255) NOT NULL,\n"
                + "    FOREIGN KEY (board_id) REFERENCES boards(id)\n"
                + ");";

        // SQL statement for creating the rel_announcements_referring table
        String rel_announcements_referring = "CREATE TABLE IF NOT EXISTS rel_announcements_referring (\n"
                + "    announcement_id integer NOT NULL,\n"
                + "    announcement_referring_id integer NOT NULL,\n"
                + "    PRIMARY KEY (announcement_id, announcement_referring_id)\n"
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
     * Find the board id corresponding to the specified public key
     *
     * @param public_key
     * @return board id corresponding to the key
     */
    public Integer findIdByKey(PublicKey public_key) {
        Integer id = null;
        String sql = "SELECT id, public_key FROM boards WHERE public_key = ?";

        try (Connection conn = this.connect();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, MyCrypto.publicKeyToB64String(public_key));
            ResultSet rs = pstmt.executeQuery();
            if (rs.next()) {
                id = rs.getInt("id");
            }
            rs.close();
        } catch (SQLException e) {
            System.out.println(e.getMessage());
        }

        return id;
    }

    /**
     * Find the announcements corresponding to the specified public key (board)
     *
     * @param public_key
     * @return announcement id correspondig to the key
     * @throws IllegalArgumentException
     */
    public Integer findAnnouncementsByKey(PublicKey public_key) throws IllegalArgumentException { //TODO: update according to new database scheme
        Integer boardId = findIdByKey(public_key);
        if (boardId == null) throw new IllegalArgumentException("No such key");

        Integer id = null;
        String sql = "SELECT id, public_key FROM boards WHERE public_key = ?";

        try (Connection conn = this.connect();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, MyCrypto.publicKeyToB64String(public_key));
            ResultSet rs = pstmt.executeQuery();
            while (rs.next()) {
                id = rs.getInt("id");
            }
            rs.close();
        } catch (SQLException e) {
            System.out.println(e.getMessage());
        }

        return id;
    }

    /**
     * Insert a new row into the boards table
     *
     * @param board_key
     * @throws IllegalArgumentException
     */
    public void insertBoard(PublicKey board_key) throws IllegalArgumentException {
        Integer exists = findIdByKey(board_key);
        if (exists != null) throw new IllegalArgumentException("Public key already registered");

        String sql = "INSERT INTO boards(public_key) VALUES(?)";

        try (Connection conn = this.connect();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, MyCrypto.publicKeyToB64String(board_key));
            pstmt.executeUpdate();
        } catch (SQLException e) {
            System.out.println(e.getMessage());
        }
    }

    /**
     * Insert a new row into the announcements table
     *
     * @param board_key
     * @param client_key
     * @param message
     * @return true if the insert was successful, false otherwise
     * @throws IllegalArgumentException
     */
    public boolean insertAnnouncement(PublicKey board_key, PublicKey client_key, String message) throws IllegalArgumentException {
        Integer boardId = findIdByKey(board_key);
        if (boardId == null) throw new IllegalArgumentException("No such key");

        boolean ret = false;
        String sql = "INSERT INTO announcements(board_id, public_key, message) VALUES(?,?,?)";

        try (Connection conn = this.connect();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setInt(1, boardId);
            pstmt.setString(2, MyCrypto.publicKeyToB64String(client_key));
            pstmt.setString(3, message);
            ret = pstmt.executeUpdate() == 1; //if the row count for the executed statement is 1
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
