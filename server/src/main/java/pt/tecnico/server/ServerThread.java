package pt.tecnico.server;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import pt.tecnico.model.*;

import javax.crypto.BadPaddingException;
import javax.crypto.IllegalBlockSizeException;
import javax.crypto.NoSuchPaddingException;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.PrintWriter;
import java.net.Socket;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.spec.InvalidKeySpecException;
import java.util.ArrayList;
import java.util.List;

public class ServerThread implements Runnable {

    private ServerInt twitter;
    private PrivateKey privateKey;

    private Socket clientSocket;
    private PrintWriter out;
    private BufferedReader in;

    private String serverNonce;
    private String clientNonce;


    public ServerThread(Twitter twitter, PrivateKey privateKey, Socket clientSocket, BufferedReader in, PrintWriter out) {
        this.twitter = twitter;
        this.privateKey = privateKey;
        this.clientSocket = clientSocket;
        this.in = in;
        this.out = out;
    }

    private JSONObject handleRequest(JSONObject joMap) {
        try {
            String action = joMap.getString(Parameters.action.name());
            if (action == null || action.isEmpty()) throw new IllegalArgumentException("Action can not be null");
            int number;
            PublicKey publicKey;
            String msg;
            List<Integer> ann;
            switch (Action.valueOf(action)) {
                case REGISTER:
                    publicKey = MyCrypto.publicKeyFromB64String(joMap.getString(Parameters.board_public_key.name()));
                    twitter.register(publicKey);
                    break;
                case READ:
                    publicKey = MyCrypto.publicKeyFromB64String(joMap.getString(Parameters.board_public_key.name()));
                    number = joMap.getInt(Parameters.number.name());
                    twitter.read(publicKey, number);
                    break;
                case READGENERAL:
                    number = joMap.getInt(Parameters.number.name());
                    twitter.readGeneral(number);
                    break;
                case POST:
                    publicKey = MyCrypto.publicKeyFromB64String(joMap.getString(Parameters.client_public_key.name()));
                    msg = joMap.getString(Parameters.message.name());
                    ann = (List<Integer>) jsonArrayToList(joMap.getJSONArray(Parameters.announcements.name()));
                    twitter.post(publicKey, msg, ann);
                    break;
                case POSTGENERAL:
                    publicKey = MyCrypto.publicKeyFromB64String(joMap.getString(Parameters.client_public_key.name()));
                    msg = joMap.getString(Parameters.message.name());
                    ann = (List<Integer>) jsonArrayToList(joMap.getJSONArray(Parameters.announcements.name()));
                    twitter.postGeneral(publicKey, msg, ann);
                    break;
                default:
                    throw new IllegalArgumentException("Unexpected value: " + Action.valueOf(action).name() + " for action param.");
            }
        } catch (Exception e) {
            System.out.println(e.getMessage());
            throw new IllegalArgumentException(e.getMessage());
        }
        return new JSONObject("{\"data\": \"This is a correct output\"}");
    }

    private JSONObject checkAndExtract(String msg) throws IllegalArgumentException, InternalError {
        JSONObject jo;
        try {
            if (msg == null || msg.length() == 0) throw new IllegalArgumentException("Message seems too short");
            jo = new JSONObject(msg);
            if (jo.length() == 0) throw new IllegalArgumentException("Message seems too short");
            byte[] sig = MyCrypto.decodeB64(jo.getString(Parameters.signature.name()));
            jo.remove(Parameters.signature.name());
            // After the signature comes the real clients request, whose params are \n separated
            PublicKey publicKey = MyCrypto.publicKeyFromB64String(jo.getString(Parameters.client_public_key.name()));
            // We verify that the message was not altered
            if (!MyCrypto.verifySignature(sig, jo.toString().getBytes(), publicKey)) {
                throw new IllegalArgumentException("Signature does not match the body");
            }
            // If nonces are set then we check their validity
            if (clientNonce != null) {
                String reqClientNonce = jo.getString(Parameters.client_nonce.name());
                String reqServerNonce = jo.getString(Parameters.server_nonce.name());
                // No need to check for null or length as equals does that
                if (!clientNonce.equals(reqClientNonce) || !serverNonce.equals(reqServerNonce)) {
                    throw new IllegalArgumentException("Illegal nonce");
                }
            }
        } catch (IllegalArgumentException | BadPaddingException | InvalidKeySpecException | JSONException e) {
            throw new IllegalArgumentException(e.getMessage());
        } catch (Exception e) {
            System.err.println(e.getMessage());
            throw new InternalError(e.getMessage());
        }
        return jo;
    }

    private void handleResponse(JSONObject resp, boolean close) throws InternalError, InvalidKeyException, BadPaddingException, NoSuchAlgorithmException, IllegalBlockSizeException, NoSuchPaddingException {
        resp.put(Parameters.client_nonce.name(), clientNonce);
        resp.put(Parameters.server_nonce.name(), serverNonce);
        String sig = MyCrypto.digestAndSignToB64(resp.toString().getBytes(), privateKey);
        resp.put(Parameters.signature.name(), sig);
        out.println(resp.toString());
        if (close) {
            try {
                closeClientConn();
            } catch (IOException e) {
                throw new InternalError(e);
            }
        }
    }

    @Override
    public void run() {
        System.out.println("Thread up and running");
        try {
            receive(in.readLine());
        } catch (IOException | IllegalBlockSizeException | NoSuchPaddingException | BadPaddingException | NoSuchAlgorithmException | InvalidKeyException e) {
            throw new InternalError(e);
        }
    }

    JSONObject receive(String msg) throws IllegalBlockSizeException, NoSuchPaddingException, BadPaddingException, NoSuchAlgorithmException, InvalidKeyException {
        JSONObject resp;
        boolean close;
        try {
            // extract the client's nonce and public key and check signature
            JSONObject packet = checkAndExtract(msg);
            if(clientNonce == null) { // first packet of the protocol
                // we set the client's nonec and create the server's (our) nonce
                setNonecs(packet.getString(Parameters.client_nonce.name()));
                // answer server's nonce using client's nonce
                resp = new JSONObject();
                close = false;
            } else { // second packet of the protocol
                resp = handleRequest(packet);
                close = true;
            }
            resp.put(Parameters.status.name(), Status.OK.name());
            handleResponse(resp, close);
        } catch (IllegalArgumentException | JSONException e) {
            resp = new JSONObject();
            resp.put(Parameters.err_msg.name(), e.getMessage());
            resp.put(Parameters.status.name(), Status.CLIENT_ERROR.name());
            handleResponse(resp, true);
            System.out.println("Client error: " + e.getMessage());
        } catch (InternalError e) {
            resp = new JSONObject();
            resp.put(Parameters.err_msg.name(), "Ups.... we had an internal error");
            resp.put(Parameters.status.name(), Status.SERVER_ERROR.name());
            handleResponse(resp, true);
            System.err.println(e.getMessage());
        }
        return resp;
    }

    private void setNonecs(String nonce) throws IllegalArgumentException {
        setClientNonec(nonce);
        setServerNonec();
    }

    private void setServerNonec() {
        serverNonce = MyCrypto.getRandomNonce();
    }

    private void setClientNonec(String nonec) throws IllegalArgumentException {
        clientNonce = nonec;
        if (clientNonce == null || clientNonce.length() < MyCrypto.NONCE_LENGTH)
            throw new IllegalArgumentException("Illegal nonce");
    }

    private void closeClientConn() throws IOException {
        in.close();
        out.close();
        clientSocket.close();
    }

    private List jsonArrayToList(JSONArray jsonArray) {
        List resp = new ArrayList();
        jsonArray.forEach(resp::add);
        return resp;
    }
}
