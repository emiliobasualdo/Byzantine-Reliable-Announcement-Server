package pt.tecnico.server;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import pt.tecnico.model.*;

import javax.crypto.BadPaddingException;
import java.io.*;
import java.lang.reflect.Array;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.security.spec.InvalidKeySpecException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
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
        JSONObject params = checkAndExtract(httpExchange);
        if(params == null) return;
        try {
            String action = params.getString(Parameters.action.name());
            if(action == null || action.isEmpty()) throw new IllegalArgumentException("Action can not be null");
            int number;
            PublicKey publicKey;
            String msg;
            List<Integer> ann;
            switch(Action.valueOf(action)) {
                case REGISTER:
                    publicKey = MyCrypto.publicKeyFromB64String(params.getString(Parameters.board_public_key.name()));
                    twitter.register(publicKey);
                    break;
                case READ:
                    publicKey = MyCrypto.publicKeyFromB64String(params.getString(Parameters.board_public_key.name()));
                    number = params.getInt(Parameters.number.name());
                    twitter.read(publicKey, number);
                    break;
                case READGENERAL:
                    number = params.getInt(Parameters.number.name());
                    twitter.readGeneral(number);
                    break;
                case POST:
                    publicKey = MyCrypto.publicKeyFromB64String(params.getString(Parameters.client_public_key.name()));
                    msg = params.getString(Parameters.message.name());
                    ann = (List<Integer>) jsonArrayToList(params.getJSONArray(Parameters.announcements.name()));
                    twitter.post(publicKey, msg, ann);
                    break;
                case POSTGENERAL:
                    publicKey = MyCrypto.publicKeyFromB64String(params.getString(Parameters.client_public_key.name()));
                    msg = params.getString(Parameters.message.name());
                    ann = (List<Integer>) jsonArrayToList(params.getJSONArray(Parameters.announcements.name()));
                    twitter.postGeneral(publicKey, msg, ann);
                    break;
                default:
                    throw new IllegalStateException("Unexpected value: " + Action.valueOf(action));
            }
        } catch (Exception e) {
            handleResponse(httpExchange, 400, e.getMessage());
            System.out.println(e.getMessage());
            return;
        }

        handleResponse(httpExchange, 200, "everything cool");
    }

    private List jsonArrayToList(JSONArray jsonArray) {
        List resp = new ArrayList();
        jsonArray.forEach(resp::add);
        return resp;
    }

    private JSONObject checkAndExtract(HttpExchange httpExchange) throws IOException {
        JSONObject jo;
        try {
            InputStream is = httpExchange.getRequestBody();
            jo = new JSONObject(new String(is.readAllBytes(), StandardCharsets.UTF_8));
            if (jo.length() == 0) throw new IllegalArgumentException("Message seems too short");
            byte[] sig = MyCrypto.decodeB64(jo.getString(Parameters.signature.name()));
            jo.remove(Parameters.signature.name());
            // After the signature comes the real clients request, whose params are \n separated
            PublicKey publicKey = MyCrypto.publicKeyFromB64String(jo.getString(Parameters.client_public_key.name()));
            // We verify that the message was not altered
            if (!MyCrypto.verifySignature(sig, jo.toString().getBytes(), publicKey)) {
                throw new IllegalArgumentException("Signature does not match the body");
            }
        } catch (IllegalArgumentException | BadPaddingException | InvalidKeySpecException | JSONException e) {
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
        return jo;
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