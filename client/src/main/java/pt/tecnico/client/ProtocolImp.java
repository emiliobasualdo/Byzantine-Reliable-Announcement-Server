package pt.tecnico.client;

import org.json.JSONArray;
import org.json.JSONObject;
import pt.tecnico.model.*;

import javax.crypto.BadPaddingException;
import javax.crypto.IllegalBlockSizeException;
import javax.crypto.NoSuchPaddingException;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.ConnectException;
import java.net.Socket;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.util.*;
// todo testar con más de un servidor
public class ProtocolImp {

    private PrivateKey clientPrivateKey;
    private int N;
    private int F;
    private Map<PublicKey, Server> servers;

    public ProtocolImp(int n, int f, Map<PublicKey, Server> servers, PrivateKey clientPrivateKey) {
        this.N = n;
        this.F = f;
        this.servers = servers;
        this.clientPrivateKey = clientPrivateKey;
    }

    /**
     * Open a new Socket connection with the server and initializes the buffers
     */
    private void open() {
        List<Integer> ports = new ArrayList<>();
        for (Server s : servers.values()) {
            try {
                ports.add(s.open());
            } catch (IOException e) {
                System.out.println(e.getMessage());
            }
        }
        if (ports.size() < N-F){
            System.err.println("Less than N-F servers are available");
            close();
            System.exit(1);
        }
        System.out.println("New TCP connections with all servers were opened on client ports: " + ports.toString());

    }

    /**
     * Close the buffers and the Socket
     *
     */
    public void close() {
        System.out.println("Closing all connections");
        servers.values().forEach(Server::close);
    }

    /**
     * Initialize the communication with each server by generating a new nonce and sending the first request
     *
     * @return true in case of success, false otherwise
     */
    public boolean init() {
        // we open the sockets for each server
        open();
        // we send the firs empty object to ehach server
        serversSend(new JSONObject());
        // wait for answers
        try {
            readResponses();
        } catch (BadResponseException e) {
            System.out.println(e.getMessage());
            close();
            return false;
        }
        // if signatures pass we set the nonces
        servers.values().forEach(Server::setServerNonce);
        return true;
    }

    private void serversSend(JSONObject jsonObject) {
        servers.values().forEach(s -> s.send(jsonObject));
    }

    /**
     * Requests each Server object to read from its server and verifies the signatures
     * @throws IllegalArgumentException if the amount of illegal signatures is greater than F
     */
    private void readResponses() throws BadResponseException {
        int badResponses = 0;
        int badSignatures = 0;
        JSONObject resp;
        for (Server s : servers.values()) {
            try {
                resp = s.read();
                System.out.printf("Server %d response: %s\n", s.port, resp.toString(2));
            } catch (IOException | BadResponseException e) {
                badResponses++;
                System.err.printf("Server %d produced exception: %s\n", s.port, e.getMessage());
                s.close();
            } catch (BadSignatureException e) {
                badSignatures++;
                System.err.printf("Server %d produced exception: %s\n", s.port, e.getMessage());
                s.close();
            }
        }
        if (badResponses > 0)
            System.out.printf("%d out of %d servers answered with bad response\n", badResponses, N);
        if (badSignatures > 0)
            System.out.printf("%d out of %d servers answered with bad signature\n", badSignatures, N);
        if (badResponses + badSignatures > F) {
            throw new BadResponseException(String.format("The amount of illegal responses(%d) is bigger than F(%d)\n", badResponses +badSignatures, F));
        }
    }

    /**
     * Send the second request
     *
     * @param req JSONObject corresponding to the message to send to the server
     * @return a JSONObject containing the server response
     */
    @SuppressWarnings("UnusedReturnValue")
    private JSONObject secondRequest(JSONObject req) {
        // for each open connection we send the message
        serversSend(req);
        // we wait for the response
        try {
            readResponses();
        } catch (BadResponseException e) {
            //todo que hacemos acá loco
            System.err.println("Bad responses: "+ e.getMessage());
            System.err.println("We will cancel an close all connections");
            close();
            System.exit(1);
        }
        // we must verify the responses
        JSONObject resp = null;
        try {
            resp = verifyResponses();
        } catch (BadResponseException e) {
            //todo qué hacemos acá loco
            System.err.println("Bad responses: "+ e.getMessage());
            System.err.println("We will cancel an close all connections");
            close();
            System.exit(1);
        }
        return resp;
    }

    private JSONObject verifyResponses() throws BadResponseException{
        return null;
    }

    public void printServersPublicKey() {
        servers.keySet().forEach(s -> System.out.println(MyCrypto.publicKeyToB64String(s)));
    }

    /**
     * Register the user
     */
    void register() {
        JSONObject req = new JSONObject();
        req.put(Parameters.action.name(), Action.REGISTER.name());
        secondRequest(req);
    }

    /**
     * Wrapper to post an announcement in the specified board
     *
     * @param message       String corresponding to the message
     * @param announcements List of announcements ids to refer to
     */
    void post(String message, List<Integer> announcements) {
        genericPost(message, announcements, Action.POST);
    }

    /**
     * Wrapper to post an announcement in the general board
     *
     * @param message       String corresponding to the message
     * @param announcements List of announcements ids to refer to
     */
    void postGeneral(String message, List<Integer> announcements) {
        genericPost(message, announcements, Action.POSTGENERAL);
    }

    /**
     * Code logic to post an announcement in a board, meant to be called by post() or postGeneral() methods
     *
     * @param message       String corresponding to the message
     * @param announcements List of announcements ids to refer to
     * @param action        Action chosen (POST or POST_GENERAL)
     */
    private void genericPost(String message, List<Integer> announcements, Action action) {
        JSONObject req = new JSONObject();
        JSONObject postData = new JSONObject();
        JSONArray ann = new JSONArray(announcements);
        // we want to generate the signature of the post first
        postData.put(Parameters.message.name(), message);
        postData.put(Parameters.announcements.name(), ann);
        postData.put(Parameters.action.name(), action);
        String postSig = null;
        try {
            postSig = MyCrypto.digestAndSignToB64(postData.toString().getBytes(), clientPrivateKey);
        } catch (NoSuchAlgorithmException | IllegalBlockSizeException | InvalidKeyException | BadPaddingException | NoSuchPaddingException e) {
            throw new InternalError(e);
        }
        // now we add the stuff the second package
        req.put(Parameters.message.name(), message);
        req.put(Parameters.announcements.name(), ann);
        req.put(Parameters.action.name(), action);
        // we add the post signature
        req.put(Parameters.post_signature.name(), postSig);
        // we sign and send
        secondRequest(req);
    }

    /**
     * Read announcements from the specified board
     *
     * @param key    Base64 encoded String corresponding to the Board public key
     * @param number int corresponding to the number of announcements to read (0 for all announcements)
     */
    void read(String key, int number) {
        JSONObject req = new JSONObject();
        req.put(Parameters.action.name(), Action.READ.name());
        req.put(Parameters.board_public_key.name(), key);
        req.put(Parameters.number.name(), number);
        secondRequest(req);
    }

    /**
     * Read announcements from the general board
     *
     * @param number int corresponding to the number of announcements to read (0 for all announcements)
     */
    void readGeneral(int number) {
        JSONObject req = new JSONObject();
        req.put(Parameters.action.name(), Action.READGENERAL.name());
        req.put(Parameters.number.name(), number);
        secondRequest(req);
    }
}
