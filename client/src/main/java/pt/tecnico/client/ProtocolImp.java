package pt.tecnico.client;

import org.json.JSONArray;
import org.json.JSONObject;
import pt.tecnico.model.Action;
import pt.tecnico.model.MyCrypto;
import pt.tecnico.model.Parameters;

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

    private static final String SERVER_IP = "127.0.0.1";
    private static final int TIMEOUT = 50 * 1000;

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

    /**
     * Checks if the signature of a JSON message is correct
     *
     * @param serverPublicKey
     * @param oResp JSONObject to check
     * @param clientNonce
     * @param serverNonce
     * @return true if the signature is correct, false otherwise
     */
    @SuppressWarnings("BooleanMethodIsAlwaysInverted")
    private static boolean verifySignature(JSONObject oResp, String clientNonce, String serverNonce, PublicKey serverPublicKey) {
        JSONObject resp = new JSONObject(oResp.toString());
        try {
            if (resp.length() == 0)
                throw new IllegalArgumentException("Empty answer");
            byte[] sig = MyCrypto.decodeB64(resp.getString(Parameters.signature.name()));
            resp.remove(Parameters.signature.name());
            // We verify that the message was not altered
            if (!MyCrypto.verifySignature(sig, resp.toString().getBytes(), serverPublicKey))
                throw new IllegalArgumentException("Signature does not match");
            // If nonces are set then we check their validity
            String respClientNonce = resp.getString(Parameters.client_nonce.name());
            String respServerNonce = resp.getString(Parameters.server_nonce.name());
            // No need to check for null or length as equals does that
            if (!clientNonce.equals(respClientNonce) || (serverNonce != null && !serverNonce.equals(respServerNonce)))
                throw new IllegalArgumentException("Nonces don't match");
            System.out.println("Server's signature and nonce are correct");
            return true;
        } catch (Exception e) {
            System.err.println(e.getMessage());
            System.err.println("Server response:");
            System.err.println(oResp.toString(2));
            return false;
        }
    }

    /**
     * Compute the digest of a JSONObject message, sign it using the provided private key and encode it to a Base64 String
     *
     * @param jo JSONObject corresponding to the message to compute the digest, sign and Base64 encode
     * @param priv
     * @return the signed digest of the message, as a Base64 encoded String
     */
    private static String sign(JSONObject jo, PrivateKey priv) {
        String sig;
        try {
            sig = MyCrypto.digestAndSignToB64(jo.toString().getBytes(), priv);
            jo.put(Parameters.signature.name(), sig);
            return sig;
        } catch (NoSuchAlgorithmException | IllegalBlockSizeException | InvalidKeyException | BadPaddingException | NoSuchPaddingException e) {
            throw new InternalError(e);
        }
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
        String postSig = sign(postData, clientPrivateKey);
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

    static class Server {
        private final PublicKey serverPublicKey;
        private final PublicKey clientPublicKey;
        private final PrivateKey clientPrivateKey;
        int port;
        String clientNonce;
        String serverNonce;
        Socket socket;
        PrintWriter out;
        BufferedReader in;
        boolean isChannelOpen = false;

        JSONObject lastResponse;

        public Server(int port, PublicKey serverPublicKey, PublicKey clientPublicKey, PrivateKey clientPrivateKey) {
            this.port = port;
            this.serverPublicKey = serverPublicKey;
            this.clientPublicKey = clientPublicKey;
            this.clientPrivateKey = clientPrivateKey;
        }

        public void close() {
            try {
                if (isChannelOpen) {
                    in.close();
                    out.close();
                    socket.close();
                }
            } catch (IOException e) {
                throw new RuntimeException(e);
            } finally {
                in = null;
                out = null;
                socket = null;
                clientNonce = null;
                serverNonce = null;
                isChannelOpen = false;
            }
        }

        public void send(JSONObject req) {
                req = new JSONObject(req.toString());
            if (isChannelOpen) {
                // We add the nonce and pub, sign each request and send it
                req.put(Parameters.client_nonce.name(), clientNonce);
                if (serverNonce != null)
                    req.put(Parameters.server_nonce.name(), serverNonce);
                req.put(Parameters.client_public_key.name(), MyCrypto.publicKeyToB64String(clientPublicKey));
                sign(req, clientPrivateKey);
                out.println(req.toString());
            }
        }

        public JSONObject read() throws IOException, BadSignatureException, BadResponseException {
            if (isChannelOpen) {
                lastResponse = new JSONObject(in.readLine());
                if (!verifySignature(lastResponse, clientNonce, serverNonce, serverPublicKey)) {
                    throw new BadSignatureException("Bad signature");
                }
            }
            if (lastResponse == null){
                throw new BadResponseException("Response is null");
            }
            return lastResponse;
        }

        public int open() throws IOException {
            try {
                this.socket = new Socket(SERVER_IP, port);
                this.socket.setSoTimeout(TIMEOUT);
                this.out = new PrintWriter(socket.getOutputStream(), true);
                this.in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
                this.clientNonce = MyCrypto.getRandomNonce();
                this.isChannelOpen = true;
                return socket.getLocalPort();
            } catch (ConnectException e) {
                this.isChannelOpen = false;
                throw e;
            }
        }

        public void setServerNonce() {
            if (isChannelOpen)
                this.serverNonce = this.lastResponse.getString(Parameters.server_nonce.name());
        }
    }

    private static class BadSignatureException extends Exception {
        public BadSignatureException(String message) {
            super(message);
        }
    }

    private static class BadResponseException extends Exception {
        public BadResponseException(String message) {
            super(message);
        }
    }
}
