package pt.tecnico.server;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import pt.tecnico.model.*;

import javax.crypto.BadPaddingException;
import java.io.*;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.spec.InvalidKeySpecException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadPoolExecutor;

import static pt.tecnico.model.MyCrypto.SERVER_ALIAS;


// https://dzone.com/articles/simple-http-server-in-java

public class ServerTCP {
    private Twitter twitter;
    private PrivateKey privateKey;

    private ServerSocket serverSocket;

    private ServerTCP(Twitter twitter, PrivateKey privateKey) {
        this.twitter = twitter;
        this.privateKey = privateKey;
    }

    private void start(int port) throws IOException {
        serverSocket = new ServerSocket(port);
        System.out.println("Server up on port: " + port + " and waiting for connections");
        ThreadPoolExecutor threadPoolExecutor = (ThreadPoolExecutor) Executors.newFixedThreadPool(10);
        while (true) {
            Socket clientSocket = serverSocket.accept();
            BufferedReader in = new BufferedReader(new InputStreamReader(clientSocket.getInputStream()));
            PrintWriter out = new PrintWriter(clientSocket.getOutputStream(), true, StandardCharsets.UTF_8);
            threadPoolExecutor.execute(new ServerThread(twitter, privateKey, clientSocket, in, out));
        }
    }

    public static void main(String[] args) {
        try {
            // We need the the path of the folder where to save the keys
            if (args.length == 0) throw new IllegalArgumentException("Specify the path for the keys");
            // We get the server's private key
            PrivateKey privateKey = MyCrypto.getPrivateKey(args[0], SERVER_ALIAS);
            // We start the server
            Twitter twitter = new Twitter();
            ServerTCP server = new ServerTCP(twitter, privateKey);
            server.start(8001);
        } catch (Exception e) {
            System.err.println("Server exception: " + e.toString());
            e.printStackTrace();
        }
    }

}