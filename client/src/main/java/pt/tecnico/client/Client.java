package pt.tecnico.client;

import pt.tecnico.model.Announcement;
import pt.tecnico.model.MyCrypto;
import pt.tecnico.model.ServerInt;
import javax.crypto.BadPaddingException;
import javax.crypto.IllegalBlockSizeException;
import javax.crypto.NoSuchPaddingException;
import java.io.*;
import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;
import java.security.*;
import java.util.ArrayList;
import java.util.List;

public class Client {

    private static ServerInt server;
    private static PublicKey pub;
    private static PrivateKey priv;
    private static PublicKey serverPublicKey;

    private Client() {}

    public static void main(String[] args) {

        // We need the the path of the folder where to save the keys
        if(args.length == 0) throw new IllegalArgumentException("Specify the path for the keys");
        try {
            // We get the client's private key
            priv = MyCrypto.getPrivateKey(args[0], MyCrypto.CLIENT_ALIAS);
            pub = MyCrypto.getPublicKey(args[0], MyCrypto.CLIENT_ALIAS);
            // we find the server
            Registry registry = LocateRegistry.getRegistry();
            server = (ServerInt) registry.lookup("Server");
            // we see if we can perform a communication
            System.out.println("Connected to server");
            serverPublicKey = MyCrypto.getPublicKey(args[0], MyCrypto.SERVER_ALIAS);
            register("pilo");

        } catch (Exception e) {
            System.err.println(e.getMessage());
        }
    }

    private static void register(String name) throws InvalidKeyException, BadPaddingException, NoSuchAlgorithmException, IllegalBlockSizeException, NoSuchPaddingException {
        //server.register(pub, name, MyCrypto.digestAndSign(name.getBytes(), priv));
    }

    private static List<Announcement> read(PublicKey pk, Integer number) throws InvalidKeyException, BadPaddingException, NoSuchAlgorithmException, IllegalBlockSizeException, NoSuchPaddingException {
        byte[] numberInBytes = new byte[]{number.byteValue()};
        //HttpPost(pk, number, MyCrypto.digestAndSign(numberInBytes, priv));
        return new ArrayList<Announcement>();
    }

    private static void post(String msg, List<Announcement> announcements) throws IOException, NoSuchAlgorithmException, IllegalBlockSizeException, InvalidKeyException, BadPaddingException, NoSuchPaddingException {

    }

}
