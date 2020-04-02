package pt.tecnico.client;

import org.beryx.textio.TextIO;
import org.beryx.textio.TextIoFactory;
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
import java.net.Socket;
import java.security.*;
import java.security.spec.InvalidKeySpecException;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Client class used to communicate with a Dependable Public Announcement Server
 */
public class Client {
    private static PublicKey pub;
    private static PrivateKey priv;
    private static PublicKey serverPublicKey;
    private static String serverIp;
    private static int serverPort;
    private final int TIMEOUT = 50;
    TextIO textIO;
    private Socket socket;
    private PrintWriter out;
    private BufferedReader in;
    private String serverNonce;
    private String clientNonce;

    /**
     * Main entrypoint for client module
     *
     * @param args Syntax: client <server-keystore-path> <server-alias> <server-storepass> <server-ip> <server-port> [client-keystore-path] [client-alias] [client-storepass]
     */
    public static void main(String[] args) {
        // We need the the path of the folder where to save the keys
        if (!(args.length == 5 || args.length == 8))
            System.out.println("Syntax: client <server-keystore-path> <server-alias> <server-storepass> <server-ip> <server-port> [client-keystore-path] [client-alias] [client-storepass]");
        else {
            try {
                // We get the client's keys and server's public key
                serverPublicKey = MyCrypto.getPublicKey(args[0], args[1], args[2]);
                // client specific private key
                if (args.length == 8) {
                    priv = MyCrypto.getPrivateKey(args[5], args[6], args[7]);
                    pub = MyCrypto.getPublicKey(args[5], args[6], args[7]);
                    System.out.println("Reusing public key: " + MyCrypto.publicKeyToB64String(pub).substring(0, 60) + "...");
                } else {
                    KeyPair kp = MyCrypto.generateKeyPair();
                    priv = kp.getPrivate();
                    pub = kp.getPublic();
                }
                // we connect to the server
                serverIp = args[3];
                serverPort = Integer.parseInt(args[4]);
                Client client = new Client();
                client.start();
            } catch (Exception e) {
                System.err.println(e.getMessage());
            }
        }
    }

    /**
     * Open a new Socket connection with the server and initializes the buffers
     */
    private void open() {
        try {
            socket = new Socket(serverIp, serverPort);
            socket.setSoTimeout(TIMEOUT * 1000);
            out = new PrintWriter(socket.getOutputStream(), true);
            in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
            System.out.println("New TCP connection with the server opened on port " + socket.getLocalPort());
        } catch (IOException e) {
            System.err.println(e.getMessage());
            System.exit(1);
        }
    }

    /**
     * Close the buffers and the Socket
     *
     * @throws IOException in case an an I/O error occurs
     */
    private void close() throws IOException {
        in.close();
        out.close();
        socket.close();
    }

    /**
     * Start the selection menu (for interactivity) and parse the user choice
     *
     * @throws NoSuchAlgorithmException  in case the specified KEY_ALG does not exist
     * @throws IllegalBlockSizeException in case the length of msg provided to a block cipher is incorrect (e.g., does not match the block size of the cipher)
     * @throws BadPaddingException       in case the message sent to the server is null
     * @throws NoSuchPaddingException    in case transformation contains a padding scheme that is not available
     * @throws InvalidKeyException       in case the public key is invalid (invalid encoding, wrong length, uninitialized, etc)
     * @throws IOException               in case an an I/O error occurs
     * @throws InvalidKeySpecException   in case the given key specification is inappropriate for this key factory to produce a public key
     */
    private void start() throws NoSuchAlgorithmException, IllegalBlockSizeException, BadPaddingException, NoSuchPaddingException, InvalidKeyException, IOException, InvalidKeySpecException {
        textIO = TextIoFactory.getTextIO();
        System.out.println("Hello. This is the client for the for the Highly Dependable Announcement Server project.");
        List<String> enumNames = Stream.of(Options.values())
                .map(Enum::name)
                .collect(Collectors.toList());
        while (true) {
            //open();
            String option = textIO.newStringInputReader()
                    .withNumberedPossibleValues(enumNames)
                    .read("What do you want to do?");

            Runnable method = () -> {
            };
            switch (Options.valueOf(option)) {
                case PRINT_MY_PUBLIC_KEY:
                    System.out.println(pub);
                    System.out.println(MyCrypto.publicKeyToB64String(pub));
                    continue;
                case PRINT_SERVER_PUBLIC_KEY:
                    System.out.println(serverPublicKey);
                    System.out.println(MyCrypto.publicKeyToB64String(serverPublicKey));
                    continue;
                case VERIFY_A_POST_SIGNATURE:
                    System.out.println("Copy and paste the following fields");
                    String pkString = textIO.newStringInputReader().read("The announcement owner's publicKey used to verify the signature");
                    PublicKey pk = MyCrypto.publicKeyFromB64String(pkString);
                    String message = textIO.newStringInputReader().read("The message");
                    JSONArray ann = new JSONArray(textIO.newIntInputReader().withMinVal(0).readList("The list(comma separated) of the announcements it makes reference to"));
                    byte[] signature = MyCrypto.decodeB64(textIO.newStringInputReader().read("The post signature"));
                    String board = textIO.newStringInputReader().withNumberedPossibleValues("Personal", "General").read("The type of board it was published to");
                    JSONObject post = new JSONObject();
                    post.put(Parameters.message.name(), message);
                    post.put(Parameters.announcements.name(), ann);
                    post.put(Parameters.action.name(), board.equals("Personal") ? Action.POST.name() : Action.POSTGENERAL.name());
                    System.out.println("This is the post you are going to verify:");
                    System.out.println(post.toString(2));
                    if (!textIO.newBooleanInputReader().withDefaultValue(true).read("Continue?"))
                        return;
                    if (MyCrypto.verifySignature(signature, post.toString().getBytes(), pk)) {
                        System.out.println("Correct signature! :) ");
                    } else {
                        System.err.println("Wrong signature! :(");
                    }
                    continue;
                case REGISTER:
                    method = this::register;
                    break;
                case READ:
                    method = () -> {
                        String who = textIO.newStringInputReader().read("Who do you want to read from?");
                        Integer num = textIO.newIntInputReader().withMinVal(0).read("How many announcements?");
                        read(who, num);
                    };
                    break;
                case READ_GENERAL:
                    method = () -> {
                        Integer num = textIO.newIntInputReader().withMinVal(0).read("How many announcements?");
                        readGeneral(num);
                    };
                    break;
                case POST:
                    method = () -> {
                        String msg = textIO.newStringInputReader().read("Type the announcement message");
                        System.out.println("Type the announcements's ids you want to make reference separated by commas:");
                        List<Integer> list = textIO.newIntInputReader().withMinVal(0).readList();
                        post(msg, list);
                    };
                    break;
                case POST_GENERAL:
                    method = () -> {
                        String msg = textIO.newStringInputReader().read("Type the announcement message");
                        System.out.println("Type the announcements's ids you want to make reference to separated by commas:");
                        List<Integer> list = textIO.newIntInputReader().withMinVal(0).readList();
                        postGeneral(msg, list);
                    };
                    break;
                case EXIT:
                    return;
            }
            if (initCommunication())
                method.run();
            close();
        }
    }

    /**
     * Initialize the communication with the server by generating a new nonce and sending the first request
     *
     * @return true in case of success, false otherwise
     * @throws IOException in case an an I/O error occurs
     */
    private boolean initCommunication() throws IOException {
        // generate our nonce
        clientNonce = MyCrypto.getRandomNonce();
        // send the initial package
        JSONObject req = new JSONObject();
        req.put(Parameters.client_nonce.name(), clientNonce);
        req.put(Parameters.client_public_key.name(), MyCrypto.publicKeyToB64String(pub));
        digestAndSign(req);
        // prompt user
        System.out.println("Your first request body is:");
        System.out.println(req.toString(2));
        if (!textIO.newBooleanInputReader().withDefaultValue(true).read("Continue?"))
            return false;
        // write
        open();
        out.println(req.toString());
        // wait for answer
        JSONObject resp = new JSONObject(in.readLine()); // todo wait max seconds
        serverNonce = resp.getString(Parameters.server_nonce.name());
        if (!verifySignature(resp))
            return false;
        System.out.println("Server response:");
        System.out.println(resp.toString(2));
        return true;
    }

    /**
     * Checks if the signature of a JSON message is correct
     *
     * @param oResp JSONObject to check
     * @return true if the signature is correct, false otherwise
     */
    @SuppressWarnings("BooleanMethodIsAlwaysInverted")
    private boolean verifySignature(JSONObject oResp) {
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
            if (!clientNonce.equals(respClientNonce) || !serverNonce.equals(respServerNonce))
                throw new IllegalArgumentException("Nonces don't match");
            System.out.println("Server's signature and nonce are correct");
            return true;
        } catch (Exception e) {
            System.err.println(e.getMessage());
            System.err.println("Server response:");
            System.out.println(oResp.toString(2));
            return false;
        }
    }

    /**
     * Build a new JSONObject populated with the client and server nonces, and the client public key
     *
     * @return JSONObject containing an empty request
     */
    private JSONObject newBasicRequest() {
        JSONObject resp = new JSONObject();
        resp.put(Parameters.client_nonce.name(), clientNonce);
        resp.put(Parameters.server_nonce.name(), serverNonce);
        resp.put(Parameters.client_public_key.name(), MyCrypto.publicKeyToB64String(pub));
        return resp;
    }

    /**
     * Compute the digest of a JSONObject message, sign it using the provided private key and encode it to a Base64 String
     *
     * @param jo JSONObject corresponding to the message to compute the digest, sign and Base64 encode
     * @return the signed digest of the message, as a Base64 encoded String
     */
    private String digestAndSign(JSONObject jo) {
        String sig;
        try {
            sig = MyCrypto.digestAndSignToB64(jo.toString().getBytes(), priv);
            jo.put(Parameters.signature.name(), sig);
            return sig;
        } catch (NoSuchAlgorithmException | IllegalBlockSizeException | InvalidKeyException | BadPaddingException | NoSuchPaddingException e) {
            throw new InternalError(e);
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
        System.out.println("Your second request body is:");
        System.out.println(req.toString(2));
        if (!textIO.newBooleanInputReader().withDefaultValue(true).read("Continue?"))
            System.exit(1);
        out.println(req.toString());
        try {
            JSONObject resp = new JSONObject(in.readLine());
            if (!verifySignature(resp))
                System.exit(1);
            System.out.println("Server response:");
            System.out.println(resp.toString(2));
            return resp;
        } catch (Exception e) {
            throw new InternalError(e);
        }
    }

    /**
     * Register the user
     */
    private void register() {
        JSONObject req = newBasicRequest();
        req.put(Parameters.action.name(), Action.REGISTER.name());
        digestAndSign(req);
        secondRequest(req);
    }

    /**
     * Wrapper to post an announcement in the specified board
     *
     * @param message       String corresponding to the message
     * @param announcements List of announcements ids to refer to
     */
    private void post(String message, List<Integer> announcements) {
        genericPost(message, announcements, Action.POST);
    }

    /**
     * Wrapper to post an announcement in the general board
     *
     * @param message       String corresponding to the message
     * @param announcements List of announcements ids to refer to
     */
    private void postGeneral(String message, List<Integer> announcements) {
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
        JSONObject req = newBasicRequest();
        JSONObject postData = new JSONObject();
        JSONArray ann = new JSONArray(announcements);
        // we want to generate the signature of the post first
        postData.put(Parameters.message.name(), message);
        postData.put(Parameters.announcements.name(), ann);
        postData.put(Parameters.action.name(), action);
        String postSig = digestAndSign(postData);
        // now we add the stuff the second package
        req.put(Parameters.message.name(), message);
        req.put(Parameters.announcements.name(), ann);
        req.put(Parameters.action.name(), action);
        // we add the post signature
        req.put(Parameters.post_signature.name(), postSig);
        // we sign and send
        digestAndSign(req);
        secondRequest(req);
    }

    /**
     * Read announcements from the specified board
     *
     * @param key    Base64 encoded String corresponding to the Board public key
     * @param number int corresponding to the number of announcements to read (0 for all announcements)
     */
    private void read(String key, int number) {
        JSONObject req = newBasicRequest();
        req.put(Parameters.action.name(), Action.READ.name());
        req.put(Parameters.board_public_key.name(), key);
        req.put(Parameters.number.name(), number);
        digestAndSign(req);
        secondRequest(req);
    }

    /**
     * Read announcements from the general board
     *
     * @param number int corresponding to the number of announcements to read (0 for all announcements)
     */
    private void readGeneral(int number) {
        JSONObject req = newBasicRequest();
        req.put(Parameters.action.name(), Action.READGENERAL.name());
        req.put(Parameters.number.name(), number);
        digestAndSign(req);
        secondRequest(req);
    }

    /**
     * Different actions available from the user menu
     */
    enum Options {
        PRINT_MY_PUBLIC_KEY,
        PRINT_SERVER_PUBLIC_KEY,
        VERIFY_A_POST_SIGNATURE,
        REGISTER,
        READ,
        READ_GENERAL,
        POST,
        POST_GENERAL,
        EXIT
    }
}

