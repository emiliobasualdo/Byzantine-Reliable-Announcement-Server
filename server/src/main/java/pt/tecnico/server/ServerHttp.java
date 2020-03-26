package pt.tecnico.server;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;
import pt.tecnico.model.Announcement;
import pt.tecnico.model.MyCrypto;
import pt.tecnico.model.Parameters;
import pt.tecnico.model.ServerInt;

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
        String response=null;
        if("POST".equals(httpExchange.getRequestMethod())) {
            handlePostRequest(httpExchange);
        } else {
            httpExchange.close();
        }
    }

    private void handlePostRequest(HttpExchange httpExchange) throws IOException {
        InputStream is =  httpExchange.getRequestBody();
        String decryptedBody;
        PublicKey publicKey;
        try {
            // We get the request body and decrypt it with the server's privateKey
            String requestBody = new String(is.readAllBytes(), StandardCharsets.UTF_8);
            // TODO break blocks into smaller ones
            if (requestBody.length() < 256) throw new IllegalArgumentException("Message seems too short");
            decryptedBody = new String(MyCrypto.decrypt(requestBody.getBytes(), privateKey));
            // We extract the parameters that are separated by \n
            // The first parameter is the signature of the client's request
            byte[] signature = decryptedBody.substring(0, decryptedBody.indexOf("\n")).getBytes();
            // After the signature comes the real clients request, whose params are \n separated
            String clientRequest = decryptedBody.substring(decryptedBody.indexOf("\n"));
            String[] params = clientRequest.split("\n");
            publicKey = MyCrypto.publicKeyFromB64String(params[Parameters.PUBLICKEY.getIndex()]);
            // We verify that the message was not altered
            if(!MyCrypto.verifySignature(signature, clientRequest.getBytes(), publicKey)) {
                throw  new IllegalArgumentException("Signature does not match the body");
            }
        } catch (IOException | NoSuchAlgorithmException | InvalidKeyException | NoSuchPaddingException | IllegalBlockSizeException | InvalidKeySpecException e) {
            handleResponse(httpExchange, 500, e.getMessage());
            System.err.println(e.getMessage());
            httpExchange.close();
            return;
        } catch (IllegalArgumentException | BadPaddingException e ) {
            handleResponse(httpExchange, 400, e.getMessage());
            System.out.println(e.getMessage());
            httpExchange.close();
            return;
        }
        handleResponse(httpExchange, 200, "everything cool");
    }

    private void handleResponse(HttpExchange httpExchange, int respCode, String response)  throws  IOException {
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
            if(args.length == 0) throw new IllegalArgumentException("Specify the path for the keys");
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