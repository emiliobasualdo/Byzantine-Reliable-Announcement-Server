package pt.tecnico.client;

import org.beryx.textio.TextIO;
import org.beryx.textio.TextIoFactory;
import org.json.JSONObject;
import pt.tecnico.model.Announcement;
import pt.tecnico.model.MyCrypto;
import pt.tecnico.model.Parameters;
import pt.tecnico.model.ServerInt;

import javax.crypto.BadPaddingException;
import javax.crypto.IllegalBlockSizeException;
import javax.crypto.NoSuchPaddingException;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.Socket;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.spec.InvalidKeySpecException;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class Client implements ServerInt {

    private static PublicKey pub;
    private static PrivateKey priv;
    private static PublicKey serverPublicKey;

    private Socket socket;
    private PrintWriter out;
    private BufferedReader in;

    private String serverNonce;
    private String clientNonce;

    private Client(Socket socket, PrintWriter out, BufferedReader in) {
        this.socket = socket;
        this.out = out;
        this.in = in;
    }

    public static void main(String[] args) {
        // We need the the path of the folder where to save the keys
        if(args.length == 0) throw new IllegalArgumentException("Specify the path for the keys");
        try {
            // We get the client's keys and server's public key
            priv = MyCrypto.getPrivateKey(args[0], MyCrypto.CLIENT_ALIAS);
            pub = MyCrypto.getPublicKey(args[0], MyCrypto.CLIENT_ALIAS);
            serverPublicKey = MyCrypto.getPublicKey(args[0], MyCrypto.SERVER_ALIAS);
            // we connect to the server
            Socket socket = new Socket("127.0.0.1", 8001);
            PrintWriter out = new PrintWriter(socket.getOutputStream(), true);
            BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
            Client client = new Client(socket, out, in);
            System.out.println("Connected to server");
            client.start();
        } catch (Exception e) {
            System.err.println(e.getMessage());
        }
    }

    private void start() throws NoSuchAlgorithmException, IllegalBlockSizeException, BadPaddingException, NoSuchPaddingException, InvalidKeyException, IOException {
        TextIO textIO = TextIoFactory.getTextIO();
        System.out.println("Hello. This is the client for the for the Highly Dependable Announcement Server.");
        List<String> enumNames = Stream.of(Options.values())
                .map(Enum::name)
                .collect(Collectors.toList());
        while (true) {
            String option = textIO.newStringInputReader()
                    .withNumberedPossibleValues(enumNames)
                    .read("What do you want to do?");
            System.out.println(option);

            Runnable method = () -> {};
            switch (Options.valueOf(option)){
                case PRINT_MY_PUBLIC_KEY:
                    System.out.println(pub);
                    break;
                case PRINT_SERVER_PUBLIC_KEY:
                    System.out.println(serverPublicKey);
                    break;
                case REGISTER:
                    method = () -> register(pub);
                    break;
                case READ:
                    method = () -> {
                        String who = textIO.newStringInputReader().read("From who do you want to read from?");
                        Integer num = textIO.newIntInputReader().withMinVal(0).read("How many announcements?");
                        try {
                            read(MyCrypto.publicKeyFromB64String(who), num);
                        } catch (NoSuchAlgorithmException | InvalidKeySpecException e) {
                            System.err.println("The public key does not seem valid");
                        }
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
                        post(pub, msg, list);
                    };
                    break;
                case POST_GENERAL:
                    method = () -> {
                        String msg = textIO.newStringInputReader().read("Type the announcement message");
                        System.out.println("Type the announcements's ids you want to make reference to separated by commas");
                        List<Integer> list = textIO.newIntInputReader().withMinVal(0).readList();
                        postGeneral(pub, msg, list);
                    };
                    break;
                case EXIT:
                    return;
            }
            if(initCommunication()) {
                method.run();
            }
        }
    }

    private boolean initCommunication() throws InvalidKeyException, BadPaddingException, NoSuchAlgorithmException, IllegalBlockSizeException, NoSuchPaddingException, IOException {
        // generate our nonce
        clientNonce = MyCrypto.getRandomNonce();
        // send the initial package
        JSONObject req = new JSONObject();
        req.put(Parameters.client_nonce.name(), clientNonce);
        req.put(Parameters.client_public_key.name(), MyCrypto.publicKeyToB64String(pub));
        req.put(Parameters.signature.name(), MyCrypto.digestAndSignToB64(req.toString().getBytes(), priv));
        out.write(req.toString());
        // wait for answer
        JSONObject resp = new JSONObject(in.readLine()); // todo wait max seconds
        System.out.println("Server response:");
        System.out.println(resp.toString(2));
        if (verifySignature(resp)) {
            serverNonce = resp.getString(Parameters.server_nonce.name());
            serverNonce = resp.getString(Parameters.server_nonce.name());
            return true;
        } else {
            System.out.println("Servers signature or nonce does not match.");
            return false;
        }
    }

    private boolean verifySignature(JSONObject resp) throws IllegalBlockSizeException, InvalidKeyException, BadPaddingException, NoSuchAlgorithmException, NoSuchPaddingException {
        if (resp.length() == 0) return false;
        byte[] sig = MyCrypto.decodeB64(resp.getString(Parameters.signature.name()));
        resp.remove(Parameters.signature.name());
        // We verify that the message was not altered
        if (!MyCrypto.verifySignature(sig, resp.toString().getBytes(), serverPublicKey)) return false;
        // If nonces are set then we check their validity
        String respClientNonce = resp.getString(Parameters.client_nonce.name());
        String respServerNonce = resp.getString(Parameters.server_nonce.name());
        // No need to check for null or length as equals does that
        return clientNonce.equals(respClientNonce) && serverNonce.equals(respServerNonce);
    }

    @Override
    public void register(PublicKey publicKey) throws IllegalArgumentException {
        JSONObject req = new JSONObject();
    }

    @Override
    public boolean post(PublicKey key, String message, List<Integer> announcements) throws IllegalArgumentException {
        System.out.println(Arrays.toString(announcements.toArray()));
        return false;
    }

    @Override
    public boolean postGeneral(PublicKey key, String message, List<Integer> announcements) throws IllegalArgumentException {
        return false;
    }

    @Override
    public List<Announcement> read(PublicKey key, int number) throws IllegalArgumentException {
        return null;
    }

    @Override
    public List<Announcement> readGeneral(int number) throws IllegalArgumentException {
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

