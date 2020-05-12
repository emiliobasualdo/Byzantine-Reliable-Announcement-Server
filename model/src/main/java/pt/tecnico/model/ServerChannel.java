package pt.tecnico.model;

import org.json.JSONObject;

import javax.crypto.BadPaddingException;
import javax.crypto.IllegalBlockSizeException;
import javax.crypto.NoSuchPaddingException;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.ConnectException;
import java.net.Socket;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.security.PrivateKey;
import java.security.PublicKey;

public class ServerChannel {
    public final PublicKey serverPublicKey;
    private final PublicKey clientPublicKey;
    private final PrivateKey clientPrivateKey;
    public int port;
    String clientNonce;
    Socket socket;
    PrintWriter out;
    BufferedReader in;

    private static final String SERVER_IP = "127.0.0.1";
    private static final int TIMEOUT = 50 * 1000;

    public ServerChannel(int port, PublicKey serverPublicKey, PublicKey clientPublicKey, PrivateKey clientPrivateKey) {
        this.port = port;
        this.serverPublicKey = serverPublicKey;
        this.clientPublicKey = clientPublicKey;
        this.clientPrivateKey = clientPrivateKey;
    }

    private static void sign(JSONObject jo, PrivateKey priv) {
        String sig;
        try {
            sig = MyCrypto.digestAndSignToB64(jo.toString().getBytes(), priv);
            jo.put(Parameters.signature.name(), sig);
        } catch (NoSuchAlgorithmException | IllegalBlockSizeException | InvalidKeyException | BadPaddingException | NoSuchPaddingException e) {
            throw new InternalError(e);
        }
    }

    private static boolean verifySignature(JSONObject oResp, String clientNonce, PublicKey serverPublicKey) {
        JSONObject resp = new JSONObject(oResp.toString());
        try {
            if (resp.length() == 0)
                throw new IllegalArgumentException("Empty answer");
            byte[] sig = MyCrypto.decodeB64(resp.getString(Parameters.signature.name()));
            resp.remove(Parameters.signature.name());
            // We verify that the message was not altered
            if (!MyCrypto.verifySignature(sig, resp.toString().getBytes(), serverPublicKey))
                throw new IllegalArgumentException("Signature does not match");
            // If nonces are set then we check their validity
            String respClientNonce = resp.getString(Parameters.client_nonce.name());
            String respServerNonce = resp.getString(Parameters.server_nonce.name());
            // No need to check for null or length as equals does that
            if (!clientNonce.equals(respClientNonce))
                throw new IllegalArgumentException("Nonces don't match");
            System.out.println("Server's signature and nonce are correct");
            return true;
        } catch (Exception e) {
            System.err.println(e.getMessage());
            System.err.println("Server response:");
            System.err.println(oResp.toString(2));
            return false;
        }
    }

    private void close() {
        try {
            in.close();
            out.close();
            socket.close();
        } catch (IOException e) {
            throw new RuntimeException(e);
        } finally {
            in = null;
            out = null;
            socket = null;
            clientNonce = null;
        }
    }

    public JSONObject send(JSONObject body) throws IOException, BadResponseException, BadSignatureException {
        // first we open the socket
        open();
        // we prepare to send the message
        body = new JSONObject(body.toString());
        JSONObject req = new JSONObject();
        req.put(Parameters.body.name(), body);
        // We add the nonce and pub, sign each request and send it
        req.put(Parameters.client_nonce.name(), clientNonce);
        req.put(Parameters.client_public_key.name(), MyCrypto.publicKeyToB64String(clientPublicKey));
        sign(req, clientPrivateKey);
        out.println(req.toString());
        // once sent we wait for an answer from the server
        JSONObject resp = read();
        close();
        return resp;
    }

    public JSONObject read() throws IOException, BadSignatureException, BadResponseException {
        JSONObject resp = new JSONObject(in.readLine());
        if (resp.length() == 0){
            throw new BadResponseException("Response is null");
        }
        if (!verifySignature(resp, clientNonce, serverPublicKey)) {
            throw new BadSignatureException("Bad signature");
        }
        return resp;
    }

    private void open() throws IOException {
        this.socket = new Socket(SERVER_IP, port);
        this.socket.setSoTimeout(TIMEOUT);
        this.out = new PrintWriter(socket.getOutputStream(), true);
        this.in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
        this.clientNonce = MyCrypto.getRandomNonce();
        socket.getLocalPort();
    }
}
