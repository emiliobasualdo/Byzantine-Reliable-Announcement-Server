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
import java.net.SocketTimeoutException;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.spec.InvalidKeySpecException;
import java.util.ArrayList;
import java.util.List;

/**
 * Server class to handle client requests
 */
public class ServerThread implements Runnable {
    private final ServerInt server;
    private final PrivateKey privateKey;

    private final Socket clientSocket;
    private final PrintWriter out;
    private final BufferedReader in;

    private String clientNonce;

    /**
     * @param server       ServerInt object that will handle register/post/read logic
     * @param privateKey   PrivateKey to sign messages with
     * @param clientSocket Socket opened with the client
     * @param in           BufferedReader to read messages from the client
     * @param out          PrintWriter to write messages to
     */
    public ServerThread(ServerInt server, PrivateKey privateKey, Socket clientSocket, BufferedReader in, PrintWriter out) {
        this.server = server;
        this.privateKey = privateKey;
        this.clientSocket = clientSocket;
        this.in = in;
        this.out = out;
    }

    @Override
    public void run() {
        try {
            receive(in.readLine());
        } catch (SocketTimeoutException e) {
            System.err.println("Timeout reached");
        } catch (IllegalBlockSizeException | NoSuchPaddingException | BadPaddingException | NoSuchAlgorithmException | InvalidKeyException e) {
            throw new InternalError(e);
        } catch (IOException e) {
            System.err.println(e.getMessage());
        }
    }

    JSONObject receive(String msg) throws IllegalBlockSizeException, NoSuchPaddingException, BadPaddingException, NoSuchAlgorithmException, InvalidKeyException {
        JSONObject resp;
        try {
            // extract the client's nonce and public key and check signature
            JSONObject packet = check(msg);
            System.out.println("Client message:" + packet.toString(2));
            // we set the client's nonce
            setClientNonce(packet.getString(Parameters.client_nonce.name()));
            resp = handleRequest(packet);
            resp.put(Parameters.status.name(), Status.OK.name());
            handleResponse(resp);
        } catch (IllegalArgumentException | JSONException e) {
            resp = new JSONObject();
            resp.put(Parameters.err_msg.name(), e.getMessage());
            resp.put(Parameters.status.name(), Status.CLIENT_ERROR.name());
            handleResponse(resp);
            System.out.println("Client error: " + e.getMessage());
        } catch (InternalError e) {
            resp = new JSONObject();
            resp.put(Parameters.err_msg.name(), "Ups.... we had an internal error");
            resp.put(Parameters.status.name(), Status.SERVER_ERROR.name());
            handleResponse(resp);
            System.err.println(e.getMessage());
        }
        return resp;
    }

    @SuppressWarnings("unchecked")
    private JSONObject handleRequest(JSONObject joMap) {
        JSONObject resp = new JSONObject();
        try {
            JSONObject body = joMap.getJSONObject(Parameters.body.name());
            String action = body.getString(Parameters.action.name());
            if (action == null || action.isEmpty())
                throw new IllegalArgumentException("Action can not be null");
            int number;
            String msg, signature, publicKey;
            List<Integer> ann;
            List<Announcement> list;
            switch (Action.valueOf(action)) {
                case READ:
                    publicKey = body.getString(Parameters.board_public_key.name());
                    number = body.getInt(Parameters.number.name());
                    list = server.read(publicKey, number);
                    resp.put(Parameters.data.name(), new JSONArray(list));
                    break;
                case READGENERAL:
                    number = body.getInt(Parameters.number.name());
                    list = server.readGeneral(number);
                    resp.put(Parameters.data.name(), new JSONArray(list));
                    break;
                case REGISTER:
                    publicKey = joMap.getString(Parameters.client_public_key.name());
                    server.register(publicKey);
                    resp.put(Parameters.data.name(), "Successfully registered");
                    break;
                case POST:
                    publicKey = joMap.getString(Parameters.client_public_key.name());
                    signature = checkPostSignature(body, MyCrypto.publicKeyFromB64String(publicKey));
                    msg = body.getString(Parameters.message.name());
                    ann = (List<Integer>) jsonArrayToList(body.getJSONArray(Parameters.announcements.name()));
                    server.post(publicKey, signature, msg, ann);
                    resp.put(Parameters.data.name(), "Posted successfully!");
                    break;
                case POSTGENERAL:
                    publicKey = joMap.getString(Parameters.client_public_key.name());
                    signature = checkPostSignature(body, MyCrypto.publicKeyFromB64String(publicKey));
                    msg = body.getString(Parameters.message.name());
                    ann = (List<Integer>) jsonArrayToList(body.getJSONArray(Parameters.announcements.name()));
                    server.postGeneral(publicKey, signature, msg, ann);
                    resp.put(Parameters.data.name(), "Posted successfully!");
                    break;
                default:
                    throw new IllegalArgumentException("Unexpected value: " + Action.valueOf(action).name() + " for action param.");
            }
        } catch (Exception e) {
            throw new IllegalArgumentException(e.getMessage());
        }
        return resp;
    }

    private String checkPostSignature(JSONObject body, PublicKey publicKey) throws NoSuchAlgorithmException, IllegalBlockSizeException, InvalidKeyException, BadPaddingException, NoSuchPaddingException {
        JSONObject postData = new JSONObject();
        postData.put(Parameters.message.name(), body.getString(Parameters.message.name()));
        postData.put(Parameters.announcements.name(), body.getJSONArray(Parameters.announcements.name()));
        postData.put(Parameters.action.name(), body.getString(Parameters.action.name()));
        String stringSig = body.getString(Parameters.post_signature.name());
        byte[] sig = MyCrypto.decodeB64(stringSig);
        if (!MyCrypto.verifySignature(sig, postData.toString().getBytes(), publicKey))
            throw new IllegalArgumentException("Post signature does not match the post body");
        return stringSig;
    }

    private JSONObject check(String msg) throws IllegalArgumentException, InternalError {
        JSONObject jo;
        try {
            if (msg == null || msg.length() == 0)
                throw new IllegalArgumentException("Message seems too short");
            jo = new JSONObject(msg);
            if (jo.length() == 0)
                throw new IllegalArgumentException("Message seems too short");
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
                if (!clientNonce.equals(reqClientNonce) )
                    throw new IllegalArgumentException("Illegal nonce");
            }
        } catch (IllegalArgumentException | BadPaddingException | InvalidKeySpecException | JSONException e) {
            throw new IllegalArgumentException(e.getMessage());
        } catch (Exception e) {
            System.err.println(e.getMessage());
            throw new InternalError(e.getMessage());
        }
        return new JSONObject(msg);
    }

    private void handleResponse(JSONObject resp) throws InternalError, InvalidKeyException, BadPaddingException, NoSuchAlgorithmException, IllegalBlockSizeException, NoSuchPaddingException {
        resp.put(Parameters.client_nonce.name(), clientNonce);
        String sig = MyCrypto.digestAndSignToB64(resp.toString().getBytes(), privateKey);
        resp.put(Parameters.signature.name(), sig);
        out.println(resp.toString());
        try {
            closeClientConn();
        } catch (IOException e) {
            throw new InternalError(e);
        }
    }

    private void setClientNonce(String nonce) throws IllegalArgumentException {
        clientNonce = nonce;
        if (clientNonce == null || clientNonce.length() < MyCrypto.NONCE_LENGTH)
            throw new IllegalArgumentException("Illegal nonce");
    }

    private void closeClientConn() throws IOException {
        in.close();
        out.close();
        clientSocket.close();
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private List jsonArrayToList(JSONArray jsonArray) {
        List resp = new ArrayList();
        jsonArray.forEach(resp::add);
        return resp;
    }
}
