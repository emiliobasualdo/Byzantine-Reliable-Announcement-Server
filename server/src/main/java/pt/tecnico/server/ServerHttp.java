package pt.tecnico.server;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;
import pt.tecnico.model.*;

import javax.crypto.BadPaddingException;
import javax.crypto.IllegalBlockSizeException;
import javax.crypto.NoSuchPaddingException;
import java.io.*;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.security.spec.InvalidKeySpecException;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadPoolExecutor;

import static pt.tecnico.model.MyCrypto.SERVER_ALIAS;


// https://dzone.com/articles/simple-http-server-in-java

public class ServerHttp implements HttpHandler {
    private Twitter twitter;
    private PrivateKey privateKey;

    public ServerHttp(Twitter twitter, PrivateKey privateKey) {
        this.twitter = twitter;
        this.privateKey = privateKey;
    }

    @Override
    public void handle(HttpExchange httpExchange) throws IOException {
        String response = null;
        if ("POST".equals(httpExchange.getRequestMethod())) {
            handlePostRequest(httpExchange);
        } else {
            httpExchange.close();
        }
    }

    private void handlePostRequest(HttpExchange httpExchange) throws IOException {
        String[] params = checkAndExtract(httpExchange);
        if(params == null) return;
        try {
            String action = params[Parameters.ACTION.getIndex()];
            if(action == null || action.isEmpty()) throw new IllegalArgumentException("Action can not be null");
            switch(Action.valueOf(action)) {
                case READ:
                    //twitter.read()
                    break;
            }
        } catch (Exception e) {
            handleResponse(httpExchange, 400, e.getMessage());
            System.out.println(e.getMessage());
            return;
        }

        handleResponse(httpExchange, 200, "everything cool");
    }

    private String[] checkAndExtract(HttpExchange httpExchange) throws IOException {
        String[] params;
        try {
            InputStream is = httpExchange.getRequestBody();
            String reqBodyB64 = new String(is.readAllBytes(), StandardCharsets.UTF_8);
            if (reqBodyB64.length() < 256 || !reqBodyB64.contains("\n")) throw new IllegalArgumentException("Message seems too short");
            byte[] sig = MyCrypto.decodeB64(reqBodyB64.substring(0, reqBodyB64.indexOf("\n")));
            // After the signature comes the real clients request, whose params are \n separated
            String clientRequest = reqBodyB64.substring(reqBodyB64.indexOf("\n") + 1);
            params = clientRequest.split("\n");
            PublicKey publicKey = MyCrypto.publicKeyFromB64String(params[Parameters.PUBLICKEY.getIndex()]);
            // We verify that the message was not altered
            if (!MyCrypto.verifySignature(sig, clientRequest.getBytes(), publicKey)) {
                throw new IllegalArgumentException("Signature does not match the body");
            }
        } catch (IllegalArgumentException | BadPaddingException | InvalidKeySpecException e) {
            handleResponse(httpExchange, 400, e.getMessage());
            System.out.println(e.getMessage());
            httpExchange.close();
            return null;
        } catch (Exception e) {
            handleResponse(httpExchange, 500, e.getMessage());
            System.err.println(e.getMessage());
            httpExchange.close();
            return null;
        }
        return params;
    }

    private void handleResponse(HttpExchange httpExchange, int respCode, String response) throws IOException {
        OutputStream outputStream = httpExchange.getResponseBody();
        // this line is a must
        httpExchange.sendResponseHeaders(respCode, response.length());
        outputStream.write(response.getBytes());
        outputStream.flush();
        outputStream.close();
    }

    public static void main(String[] args) {
        try {
            // We need the the path of the folder where to save the keys
            if (args.length == 0) throw new IllegalArgumentException("Specify the path for the keys");
            // We get the server's private key
            PrivateKey privateKey = MyCrypto.getPrivateKey(args[0], SERVER_ALIAS);
            // We start the server
            Twitter twitter = new Twitter();
            HttpServer server = HttpServer.create(new InetSocketAddress("localhost", 8001), 0);
            server.createContext("/twitter", new ServerHttp(twitter, privateKey));
            ThreadPoolExecutor threadPoolExecutor = (ThreadPoolExecutor) Executors.newFixedThreadPool(10);
            server.setExecutor(threadPoolExecutor);
            server.start();
            System.out.println(" Server started on port 8001");
        } catch (Exception e) {
            System.err.println("Server exception: " + e.toString());
            e.printStackTrace();
        }
    }

}