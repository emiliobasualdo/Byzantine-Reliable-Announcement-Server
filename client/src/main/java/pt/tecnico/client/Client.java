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
import java.io.*;
import java.net.Socket;
import java.security.*;
import java.security.cert.CertificateException;
import java.security.spec.InvalidKeySpecException;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Client class used to communicate with a Dependable Public Announcement Server
 */
public class Client {

    private ProtocolImp proto;
    TextIO textIO;

    private PublicKey clientPub;

    /**
     * Main entrypoint for client module
     *
     * @param args Syntax: client path/to/settings/file
     */
    public static void main(String[] args) throws IOException, UnrecoverableKeyException, CertificateException, NoSuchAlgorithmException, KeyStoreException, InvalidKeySpecException, InvalidKeyException, BadPaddingException, NoSuchPaddingException, IllegalBlockSizeException {
        if(args.length != 1)
            throw new IllegalArgumentException("Syntax: client <path/to/settings/file>");
        // We need the the path of the folder where to save the keys
        Map<String, String> opts = parseOptions(args[0]);
        if (!(opts.size() == 5 || opts.size() == 8))
            throw new IllegalArgumentException("Some options are missing");

        PublicKey pub;
        PrivateKey priv;
        int N;
        int F;
        Map<PublicKey, ProtocolImp.Server> servers = new HashMap<>();
        // we set the parameters
        F = Integer.parseInt(opts.get("f"));
        String ports = opts.get("ports");
        String serverkeyStore = opts.get("server_keystore_path");
        String serverKeyPasswd = opts.get("server_store_password");
        // client specific private key
        if (opts.size() == 9) {
            String clientKeyStore = opts.get("client_keystore_path");
            String clientAlias = opts.get("client_alias");
            String clientPasswd = opts.get("client_store_pass");
            priv = MyCrypto.getPrivateKey(clientKeyStore, clientAlias, clientPasswd);
            pub = MyCrypto.getPublicKey(clientKeyStore, clientAlias, clientPasswd);
            System.out.println("Reusing public key: " + MyCrypto.publicKeyToB64String(pub).substring(0, 60) + "...");
        } else {
            KeyPair kp = MyCrypto.generateKeyPair();
            priv = kp.getPrivate();
            pub = kp.getPublic();
            System.out.println("Created new public key: " + MyCrypto.publicKeyToB64String(pub).substring(0, 60) + "...");
        }
        // We get the servers's public keys a
        // nd we parse the ports
        PublicKey serverPublicKey;
        ports = ports.replace(" ", "");
        ports = ports.substring(1, ports.length()-1);
        String[] list = ports.split(",");
        N = list.length;
        for (int i = 0; i < N; i++) {
            serverPublicKey = MyCrypto.getPublicKey(serverkeyStore,"server_"+i, serverKeyPasswd);
            servers.put(serverPublicKey, new ProtocolImp.Server(Integer.parseInt(list[i]), serverPublicKey, pub, priv));
        }
        // done
        ProtocolImp p = new ProtocolImp(N, F, servers, priv);
        Client client = new Client(p, pub);
        client.start();
    }

    private static Map<String, String> parseOptions(String file) throws IOException {
        Map<String, String> resp = new HashMap<>();
        // Open the file
        FileInputStream fstream = new FileInputStream(file);
        BufferedReader br = new BufferedReader(new InputStreamReader(fstream));
        String strLine;
        String[] list;
        //Read File Line By Line
        while ((strLine = br.readLine()) != null) {
            if (strLine.startsWith("#")) continue;
            list = strLine.split("=");
            resp.put(list[0], list[1]);
        }
        //Close the input stream
        fstream.close();
        return resp;
    }

    public Client(ProtocolImp p, PublicKey pub) {
        this.proto = p;
        this.clientPub = pub;
    }

    /**
     * Start the selection menu (for interactivity) and parses the user choice
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
            String option = textIO.newStringInputReader()
                    .withNumberedPossibleValues(enumNames)
                    .read("What do you want to do?");

            Runnable method = () -> {
            };
            switch (Options.valueOf(option)) {
                case PRINT_MY_PUBLIC_KEY:
                    System.out.println(MyCrypto.publicKeyToB64String(clientPub));
                    continue;
                case PRINT_SERVERS_PUBLIC_KEY:
                    proto.printServersPublicKey();
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
                    method = () -> proto.register();
                    break;
                case READ:
                    method = () -> {
                        String who = textIO.newStringInputReader().read("Who do you want to read from?");
                        Integer num = textIO.newIntInputReader().withMinVal(0).read("How many announcements?");
                        proto.read(who, num);
                    };
                    break;
                case READ_GENERAL:
                    method = () -> {
                        Integer num = textIO.newIntInputReader().withMinVal(0).read("How many announcements?");
                        proto.readGeneral(num);
                    };
                    break;
                case POST:
                    method = () -> {
                        String msg = textIO.newStringInputReader().read("Type the announcement message");
                        System.out.println("Type the announcements's ids you want to make reference separated by commas:");
                        List<Integer> list = textIO.newIntInputReader().withMinVal(0).readList();
                        proto.post(msg, list);
                    };
                    break;
                case POST_GENERAL:
                    method = () -> {
                        String msg = textIO.newStringInputReader().read("Type the announcement message");
                        System.out.println("Type the announcements's ids you want to make reference to separated by commas:");
                        List<Integer> list = textIO.newIntInputReader().withMinVal(0).readList();
                        proto.postGeneral(msg, list);
                    };
                    break;
                case EXIT:
                    return;
            }
            if (proto.init())
                method.run();
            proto.close();
        }
    }

    /**
     * Different actions available from the user menu
     */
    enum Options {
        PRINT_MY_PUBLIC_KEY,
        PRINT_SERVERS_PUBLIC_KEY,
        VERIFY_A_POST_SIGNATURE,
        REGISTER,
        READ,
        READ_GENERAL,
        POST,
        POST_GENERAL,
        EXIT
    }
}

