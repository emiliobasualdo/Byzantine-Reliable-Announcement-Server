package pt.tecnico.server;

import pt.tecnico.model.MyCrypto;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.security.PrivateKey;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadPoolExecutor;

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
            System.out.println("Established connection with client-> "+ clientSocket.getInetAddress().toString() +":"+clientSocket.getPort());
            System.out.println("Creating new thread");
            BufferedReader in = new BufferedReader(new InputStreamReader(clientSocket.getInputStream()));
            PrintWriter out = new PrintWriter(clientSocket.getOutputStream(), true, StandardCharsets.UTF_8);
            threadPoolExecutor.execute(new ServerThread(twitter, privateKey, clientSocket, in, out));
        }
    }

    public static void main(String[] args) {
        try {
            // We need the the path of the folder where to save the keys
            if(args.length < 3) throw new IllegalArgumentException("Specify key-store-path, alias, password");
            // We get the server's private key
            PrivateKey privateKey = MyCrypto.getPrivateKey(args[0], args[1], args[2]);
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