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
    private final BRBroadcast broadcast;
    private int F;
    private ServerInt server;
    private PrivateKey privateKey;

    private Socket clientSocket;
    private PrintWriter out;
    private BufferedReader in;
    private List<ServerChannel> servers;
    private int port;

    private String clientPublicKey;

    private String clientNonce;

    /**
     * @param server       ServerInt object that will handle register/post/read logic
     * @param privateKey   PrivateKey to sign messages with
     * @param clientSocket Socket opened with the client
     * @param in           BufferedReader to read messages from the client
     * @param out          PrintWriter to write messages to
     * @param servers
     * @param broadcast
     */
    public ServerThread(ServerInt server, PrivateKey privateKey, Socket clientSocket, BufferedReader in, PrintWriter out, int F, List<ServerChannel> servers, int port, BRBroadcast broadcast) {
        this.server = server;
        this.privateKey = privateKey;
        this.clientSocket = clientSocket;
        this.in = in;
        this.out = out;
        this.F = F;
        this.servers = servers;
        this.port = port;
        this.broadcast = broadcast;
    }


    @Override
    public void run() {
        try {
            String firstLine = in.readLine();
            if(isClient(firstLine)) {
                clientReceive(firstLine);
            } else {
                JSONObject packet = new JSONObject(firstLine);
                PublicKey publicKey = MyCrypto.publicKeyFromB64String(packet.getString(Parameters.client_public_key.name()));
                ServerChannel sc = null;
                for (ServerChannel s: servers) {
                    if (s.serverPublicKey.equals(publicKey)) {
                        sc = s;
                        break;
                    }
                }
                System.out.printf("Received message from server:%d %s\n",sc.port, firstLine);
                JSONObject req = broadcast.listen(packet, sc);
                if (clientPublicKey == null || clientPublicKey.isEmpty()){
                    clientPublicKey = req.getString(Parameters.client_public_key.name());
                }
                handleRequest(req);
            }
        } catch (SocketTimeoutException e) {
            System.err.println("Timeout reached");
        } catch (IllegalBlockSizeException | NoSuchPaddingException | BadPaddingException | NoSuchAlgorithmException | InvalidKeyException e) {
            throw new InternalError(e);
        } catch (IOException e) {
            System.err.println(e.getMessage());
        } catch (BadResponseException | BadSignatureException | InvalidKeySpecException e) {
            e.printStackTrace();
        }
    }

    private boolean isClient(String msg) {
        JSONObject packet = new JSONObject(msg);
        return !packet.has(Parameters.broadcast.name());
    }


    JSONObject clientReceive(String msg) throws IllegalBlockSizeException, NoSuchPaddingException, BadPaddingException, NoSuchAlgorithmException, InvalidKeyException {
        JSONObject resp = null;
        try {
            // extract the client's nonce and public key and check signature
            JSONObject packet = check(msg);
            System.out.println("Client message:" + packet.toString(2));
            // we set the client's nonce
            setClientNonce(packet.getString(Parameters.client_nonce.name()));
            broadcast.broadcast(packet);
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
            String action = joMap.getString(Parameters.action.name());
            if (action == null || action.isEmpty())
                throw new IllegalArgumentException("Action can not be null");
            int number;
            String msg, signature, boardPublicKey;
            List<Integer> ann;
            List<Announcement> list;
            switch (Action.valueOf(action)) {
                case READ:
                    boardPublicKey = joMap.getString(Parameters.board_public_key.name());
                    number = joMap.getInt(Parameters.number.name());
                    list = server.read(boardPublicKey, number);
                    resp.put(Parameters.data.name(), new JSONArray(list));
                    break;
                case READGENERAL:
                    number = joMap.getInt(Parameters.number.name());
                    list = server.readGeneral(number);
                    resp.put(Parameters.data.name(), new JSONArray(list));
                    break;
                case REGISTER:
                    server.register(clientPublicKey);
                    resp.put(Parameters.data.name(), "Successfully registered");
                    break;
                case POST:
                    signature = checkPostSignature(joMap, MyCrypto.publicKeyFromB64String(clientPublicKey));
                    msg = joMap.getString(Parameters.message.name());
                    ann = (List<Integer>) jsonArrayToList(joMap.getJSONArray(Parameters.announcements.name()));
                    server.post(clientPublicKey, signature, msg, ann);
                    resp.put(Parameters.data.name(), "Posted successfully!");
                    break;
                case POSTGENERAL:
                    signature = checkPostSignature(joMap, MyCrypto.publicKeyFromB64String(clientPublicKey));
                    msg = joMap.getString(Parameters.message.name());
                    ann = (List<Integer>) jsonArrayToList(joMap.getJSONArray(Parameters.announcements.name()));
                    server.postGeneral(clientPublicKey, signature, msg, ann);
                    resp.put(Parameters.data.name(), "Posted successfully!");
                    break;
                default:
                    throw new IllegalArgumentException("Unexpected value: " + Action.valueOf(action).name() + " for action param.");
            }
            System.out.printf("%s executed successfully\n", action);
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
            clientPublicKey = jo.getString(Parameters.client_public_key.name());
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

    private void handleResponse(JSONObject response) throws InternalError, InvalidKeyException, BadPaddingException, NoSuchAlgorithmException, IllegalBlockSizeException, NoSuchPaddingException {
        response = new JSONObject(response.toString());
        response.put(Parameters.client_nonce.name(), clientNonce);
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
