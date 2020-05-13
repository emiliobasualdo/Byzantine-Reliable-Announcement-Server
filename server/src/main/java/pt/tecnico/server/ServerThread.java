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
    private int F;
    private ServerInt server;
    private PrivateKey privateKey;

    private Socket clientSocket;
    private PrintWriter out;
    private BufferedReader in;
    private List<ServerChannel> servers;
    private int port;
    private boolean clientChannel;
    private ServerChannel serverChannel;

    private String clientPublicKey;

    private String clientNonce;

    /**
     * @param server       ServerInt object that will handle register/post/read logic
     * @param privateKey   PrivateKey to sign messages with
     * @param clientSocket Socket opened with the client
     * @param in           BufferedReader to read messages from the client
     * @param out          PrintWriter to write messages to
     * @param servers
     * @param clientSocket Socket opened with the client
     */
    public ServerThread(ServerInt server, PrivateKey privateKey, Socket clientSocket, BufferedReader in, PrintWriter out, int F, List<ServerChannel> servers, int port, boolean clientChannel) {
        this.server = server;
        this.privateKey = privateKey;
        this.clientSocket = clientSocket;
        this.in = in;
        this.out = out;
        this.F = F;
        this.servers = servers;
        this.port = port;
        this.clientChannel = clientChannel;
    }

    public ServerThread(ServerInt server, PrivateKey privateKey, ServerChannel serverChannel, boolean clientChannel) {
        this.server = server;
        this.privateKey = privateKey;
        this.serverChannel = serverChannel;
        this.clientChannel = clientChannel;
    }


    @Override
    public void run() {
        try {
            if (clientChannel) {
                // client-server socket
                clientReceive(in.readLine());
            } else {
                // server-server socket
                new BRBroadcast(F, servers, port).listenOnServer(serverChannel);
            }
        } catch (SocketTimeoutException e) {
            System.err.println("Timeout reached");
        } catch (IllegalBlockSizeException | NoSuchPaddingException | BadPaddingException | NoSuchAlgorithmException | InvalidKeyException e) {
            throw new InternalError(e);
        } catch (IOException e) {
            System.err.println(e.getMessage());
        } catch (BadResponseException | BadSignatureException e) {
            e.printStackTrace();
        }
    }


    JSONObject clientReceive(String msg) throws IllegalBlockSizeException, NoSuchPaddingException, BadPaddingException, NoSuchAlgorithmException, InvalidKeyException {
        JSONObject resp = null;
        try {
            // extract the client's nonce and public key and check signature
            JSONObject packet = check(msg);
            System.out.println("Client message:" + packet.toString(2));
            // we set the client's nonce
            JSONObject body = packet.getJSONObject(Parameters.body.name());
            setClientNonce(body.getString(Parameters.client_nonce.name()));
            new BRBroadcast(F, servers, port).broadcast(body);
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
        } catch (BadResponseException | BadSignatureException | IOException e) {
            e.printStackTrace();
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
            String msg, signature, boardPublicKey;
            List<Integer> ann;
            List<Announcement> list;
            switch (Action.valueOf(action)) {
                case READ:
                    boardPublicKey = body.getString(Parameters.board_public_key.name());
                    number = body.getInt(Parameters.number.name());
                    list = server.read(boardPublicKey, number);
                    resp.put(Parameters.data.name(), new JSONArray(list));
                    break;
                case READGENERAL:
                    number = body.getInt(Parameters.number.name());
                    list = server.readGeneral(number);
                    resp.put(Parameters.data.name(), new JSONArray(list));
                    break;
                case REGISTER:
                    server.register(clientPublicKey);
                    resp.put(Parameters.data.name(), "Successfully registered");
                    break;
                case POST:
                    signature = checkPostSignature(body, MyCrypto.publicKeyFromB64String(clientPublicKey));
                    msg = body.getString(Parameters.message.name());
                    ann = (List<Integer>) jsonArrayToList(body.getJSONArray(Parameters.announcements.name()));
                    server.post(clientPublicKey, signature, msg, ann);
                    resp.put(Parameters.data.name(), "Posted successfully!");
                    break;
                case POSTGENERAL:
                    signature = checkPostSignature(body, MyCrypto.publicKeyFromB64String(clientPublicKey));
                    msg = body.getString(Parameters.message.name());
                    ann = (List<Integer>) jsonArrayToList(body.getJSONArray(Parameters.announcements.name()));
                    server.postGeneral(clientPublicKey, signature, msg, ann);
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
            clientPublicKey = jo.getJSONObject(Parameters.body.name()).getString(Parameters.client_public_key.name());
            PublicKey clientPublicKeyAux = MyCrypto.publicKeyFromB64String(clientPublicKey);
            // We verify that the message was not altered
            if (!MyCrypto.verifySignature(sig, jo.toString().getBytes(), clientPublicKeyAux)) {
                throw new IllegalArgumentException("Signature does not match the body");
            }
        } catch (IllegalArgumentException | BadPaddingException | InvalidKeySpecException | JSONException e) {
            throw new IllegalArgumentException(e.getMessage());
        } catch (Exception e) {
            System.err.println(e.getMessage());
            throw new InternalError(e.getMessage());
        }
        return new JSONObject(msg);
    }

    private void handleResponse(JSONObject body) throws InternalError, InvalidKeyException, BadPaddingException, NoSuchAlgorithmException, IllegalBlockSizeException, NoSuchPaddingException {
        body = new JSONObject(body.toString());
        JSONObject response = new JSONObject();
        body.put(Parameters.client_nonce.name(), clientNonce);
        response.put(Parameters.body.name(), body);
        String sig = MyCrypto.digestAndSignToB64(response.toString().getBytes(), privateKey);
        response.put(Parameters.signature.name(), sig);
        out.println(response.toString());
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
