package pt.tecnico.client;

import org.beryx.textio.TextIO;
import org.beryx.textio.TextIoFactory;
import org.json.JSONObject;
import pt.tecnico.model.*;

import javax.crypto.BadPaddingException;
import javax.crypto.IllegalBlockSizeException;
import javax.crypto.NoSuchPaddingException;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.Socket;
import java.security.*;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class Client {

    private static PublicKey pub;
    private static PrivateKey priv;
    private static PublicKey serverPublicKey;

    private Socket socket;
    private PrintWriter out;
    private BufferedReader in;

    private String serverNonce;
    private String clientNonce;

    TextIO textIO;


    public static void main(String[] args) {
        // We need the the path of the folder where to save the keys
        if(args.length < 3) throw new IllegalArgumentException("Specify key-store-path, alias, password");
        try {
            // We get the client's keys and server's public key
            KeyPair kp = MyCrypto.generateKeyPair();
            priv = kp.getPrivate();
            pub = kp.getPublic();
            serverPublicKey = MyCrypto.getPublicKey(args[0], args[1], args[2]);
            // we connect to the server
            Client client = new Client();
            System.out.println("Connected to server");
            client.start();
        } catch (Exception e) {
            System.err.println(e.getMessage());
        }
    }

    private void open() {
        try {
            socket = new Socket("127.0.0.1", 8001);
            out = new PrintWriter(socket.getOutputStream(), true);
            in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
        } catch (IOException e) {
            System.err.println(e.getMessage());
            System.exit(1);
        }
    };

    private void close() throws IOException {
        in.close();
        out.close();
        socket.close();
    }
    private void start() throws NoSuchAlgorithmException, IllegalBlockSizeException, BadPaddingException, NoSuchPaddingException, InvalidKeyException, IOException {
        textIO = TextIoFactory.getTextIO();
        System.out.println("Hello. This is the client for the for the Highly Dependable Announcement Server.");
        List<String> enumNames = Stream.of(Options.values())
                .map(Enum::name)
                .collect(Collectors.toList());
        while (true) {
            open();
            String option = textIO.newStringInputReader()
                    .withNumberedPossibleValues(enumNames)
                    .read("What do you want to do?");

            Runnable method = () -> {};
            switch (Options.valueOf(option)){
                case PRINT_MY_PUBLIC_KEY:
                    System.out.println(pub);
                    System.out.println(MyCrypto.publicKeyToB64String(pub));
                    continue;
                case PRINT_SERVER_PUBLIC_KEY:
                    System.out.println(serverPublicKey);
                    System.out.println(MyCrypto.publicKeyToB64String(serverPublicKey));
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
                        System.out.println("Type the announcements's ids you want to make reference to separated by commas");
                        List<Integer> list = textIO.newIntInputReader().withMinVal(0).readList();
                        post(msg, list);
                    };
                    break;
                case POST_GENERAL:
                    method = () -> {
                        String msg = textIO.newStringInputReader().read("Type the announcement message");
                        System.out.println("Type the announcements's ids you want to make reference to separated by commas");
                        List<Integer> list = textIO.newIntInputReader().withMinVal(0).readList();
                        postGeneral(msg, list);
                    };
                    break;
                case EXIT:
                    return;
            }
            if(initCommunication()) {
                method.run();
            }
            close();
        }
    }

    private boolean initCommunication() throws InvalidKeyException, BadPaddingException, NoSuchAlgorithmException, IllegalBlockSizeException, NoSuchPaddingException, IOException {
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
        if(!textIO.newBooleanInputReader().read("Continue?")) return false;
        // write
        out.println(req.toString());
        // wait for answer
        JSONObject resp = new JSONObject(in.readLine()); // todo wait max seconds
        serverNonce = resp.getString(Parameters.server_nonce.name());
        if (!verifySignature(resp)) return false;
        System.out.println("Server response:");
        System.out.println(resp.toString(2));
        return true;
    }

    private boolean verifySignature(JSONObject oResp) throws IllegalBlockSizeException, InvalidKeyException, BadPaddingException, NoSuchAlgorithmException, NoSuchPaddingException {
        JSONObject resp = new JSONObject(oResp.toString());
        try {
            if (resp.length() == 0) throw new IllegalArgumentException("Empty answer");
            byte[] sig = MyCrypto.decodeB64(resp.getString(Parameters.signature.name()));
            resp.remove(Parameters.signature.name());
            // We verify that the message was not altered
            if (!MyCrypto.verifySignature(sig, resp.toString().getBytes(), serverPublicKey)) throw new IllegalArgumentException("Signature does not match");
            // If nonces are set then we check their validity
            String respClientNonce = resp.getString(Parameters.client_nonce.name());
            String respServerNonce = resp.getString(Parameters.server_nonce.name());
            // No need to check for null or length as equals does that
            if(!clientNonce.equals(respClientNonce) || !serverNonce.equals(respServerNonce)) throw new IllegalArgumentException("Nonces don't match");
            System.out.println("Server's signature and nonce are correct");
            return true;
        } catch (Exception e) {
            System.out.println(e.getMessage());
            System.err.println("Server response:");
            System.out.println(oResp.toString(2));
            return false;
        }
    }

    private JSONObject newBasicRequest() {
        JSONObject resp = new JSONObject();
        resp.put(Parameters.client_nonce.name(), clientNonce);
        resp.put(Parameters.server_nonce.name(), serverNonce);
        resp.put(Parameters.client_public_key.name(), MyCrypto.publicKeyToB64String(pub));
        return resp;
    }

    private void digestAndSign(JSONObject jo) {
        String sig;
        try {
            sig = MyCrypto.digestAndSignToB64(jo.toString().getBytes(), priv);
            jo.put(Parameters.signature.name(), sig);
        } catch (NoSuchAlgorithmException | IllegalBlockSizeException | InvalidKeyException | BadPaddingException | NoSuchPaddingException e) {
            throw new InternalError(e);
        }
    }

    private JSONObject secondRequest(JSONObject req) {
        System.out.println("Your second request body is:");
        System.out.println(req.toString(2));
        if(!textIO.newBooleanInputReader().read("Continue?")) System.exit(1);
        out.println(req.toString());
        try {
            JSONObject resp = new JSONObject(in.readLine());
            if (!verifySignature(resp)) System.exit(1);
            System.out.println("Server response:");
            System.out.println(resp.toString(2));
            return resp;
        } catch (Exception e) {
            throw new InternalError(e);
        }
    }

    private void register() throws IllegalArgumentException {
        JSONObject req = newBasicRequest();
        req.put(Parameters.action.name(), Action.REGISTER.name());
        digestAndSign(req);
        secondRequest(req);
    }

    private boolean post(String message, List<Integer> announcements) throws IllegalArgumentException {
        JSONObject req = newBasicRequest();
        return false;
    }

    private boolean postGeneral(String message, List<Integer> announcements) throws IllegalArgumentException {
        return false;
    }

    private void read(String key, int number) throws IllegalArgumentException {
        JSONObject req = newBasicRequest();
        req.put(Parameters.action.name(), Action.READ.name());
        req.put(Parameters.board_public_key.name(), key);
        req.put(Parameters.number.name(), number);
        digestAndSign(req);
        secondRequest(req);
    }

    private List<Announcement> readGeneral(int number) throws IllegalArgumentException {
        return null;
    }

    enum Options {
        PRINT_MY_PUBLIC_KEY,
        PRINT_SERVER_PUBLIC_KEY,
        REGISTER,
        READ,
        READ_GENERAL,
        POST,
        POST_GENERAL,
        EXIT;
    }
}

