package pt.tecnico.server;

import pt.tecnico.model.BRBroadcast;
import pt.tecnico.model.MyCrypto;
import pt.tecnico.model.ServerChannel;

import java.io.*;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadPoolExecutor;

// https://dzone.com/articles/simple-http-server-in-java

/**
 * Server class wrapper to setup a new TCP ServerSocket
 */
public class ServerTCP {
    private static final String IP =  "127.0.0.1";
    private final int TIMEOUT = 50;


    /**
     * Main entrypoint for server module
     *
     * @param args Syntax: server <server-keystore-path> <server-alias> <server-storepass> <ip> <port>
     */
    public static void main(String[] args) {
        try {
            // We need the the path of the folder where to save the keys
            if(args.length != 3)
                throw new IllegalArgumentException("Syntax: client <path/to/settings/file> server_number port");
            // We need the the path of the folder where to save the keys
            Map<String, String> opts = parseOptions(args[0]);

            PublicKey pub;
            PrivateKey priv;
            // we set the parameters
            String ports = opts.get("ports");
            String serverkeyStore = opts.get("server_keystore_path");
            String serverKeyPasswd = opts.get("server_store_password");
            String serverNumber = args[1];
            int port = Integer.parseInt(args[2]);
            String serverAlias = "server_"+serverNumber;
            // server private key
            priv = MyCrypto.getPrivateKey(serverkeyStore, serverAlias, serverKeyPasswd);
            pub = MyCrypto.getPublicKey(serverkeyStore, serverAlias, serverKeyPasswd);
            if (priv == null || pub == null) throw new IllegalArgumentException("Private/Public keys are null");

            // We get the servers's public keys a
            // nd we parse the ports
            PublicKey serverPublicKey;
            ports = ports.replace(" ", "");
            ports = ports.substring(1, ports.length()-1);
            String[] list = ports.split(",");
            List<ServerChannel> servers = new ArrayList<>();
            for (int i = 0; i < list.length; i++) {
                serverPublicKey = MyCrypto.getPublicKey(serverkeyStore,"server_"+i, serverKeyPasswd);
                int portAux = Integer.parseInt(list[i]);
                servers.add(new ServerChannel(portAux, serverPublicKey, pub, priv));
            }
            int F = Integer.parseInt(opts.get("f"));

            // We start the server
            Twitter twitter = new Twitter(serverAlias);
            ServerTCP server = new ServerTCP();
            server.start(port, twitter, priv, F ,servers);
        } catch (Exception e) {
            System.err.println("Server exception: " + e.toString());
            e.printStackTrace();
        }
    }

    private void start(int port, Twitter twitter, PrivateKey privateKey, int F, List<ServerChannel> servers) throws IOException {
        ServerSocket serverSocket = new ServerSocket(port, 0, InetAddress.getByName(IP));
        System.out.println("Server up, listening on " + IP + ":" + port + " and waiting for connections");
        ThreadPoolExecutor threadPoolExecutor = (ThreadPoolExecutor) Executors.newFixedThreadPool(10);
        BRBroadcast broadCast = new BRBroadcast(F, servers, port);
        //noinspection InfiniteLoopStatement
        while (true) {
            Socket socket = serverSocket.accept();
            socket.setSoTimeout(TIMEOUT * 1000);
            System.out.printf("Established connection with socket-> %s:%d in a new thread\n", socket.getInetAddress().toString(),socket.getPort());
            BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
            PrintWriter out = new PrintWriter(socket.getOutputStream(), true, StandardCharsets.UTF_8);
            // client-server socket
            threadPoolExecutor.execute(new ServerThread(twitter, privateKey, socket, in, out, F, servers, port, broadCast));
        }
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

}