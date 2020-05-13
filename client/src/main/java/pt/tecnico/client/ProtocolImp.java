package pt.tecnico.client;

import org.json.JSONArray;
import org.json.JSONObject;
import pt.tecnico.model.*;

import javax.crypto.BadPaddingException;
import javax.crypto.IllegalBlockSizeException;
import javax.crypto.NoSuchPaddingException;
import java.io.IOException;
import java.net.ConnectException;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.security.PrivateKey;
import java.util.*;
// todo testar con más de un servidor
public class ProtocolImp {

    private PrivateKey clientPrivateKey;
    private int N;
    private int F;
    private List<ServerChannel> servers;

    public ProtocolImp(int n, int f, List<ServerChannel> servers, PrivateKey clientPrivateKey) {
        this.N = n;
        this.F = f;
        this.servers = servers;
        this.clientPrivateKey = clientPrivateKey;
    }

    private JSONObject broadcast(JSONObject jsonObject, String clientNonce) throws BadResponseException {
        Map<Integer, Integer> bodyCount = new HashMap<>();
        Map<Integer, JSONObject> bodies = new HashMap<>();
        servers.forEach(s -> {
            JSONObject resp;
            try {
                resp = s.send(new JSONObject(jsonObject.toString()), clientNonce);
                if (!resp.getString(Parameters.client_nonce.name()).equals(clientNonce)) {
                    throw new BadResponseException("Nonce don't match");
                }
                s.close();
            } catch (IOException | BadResponseException | BadSignatureException e) {
                System.out.printf("Error sending to server %d %s\n", s.port, e.getMessage());
                return;
            }
            // we add 1 to the count
            resp.remove(Parameters.signature.name());
            int count = 1;
            int hash = resp.toString().hashCode();
            if (bodyCount.containsKey(hash)) {
                count = bodyCount.get(hash) + 1;
            }
            bodyCount.put(hash, count);
            // we save the message
            bodies.put(hash, resp);
        });
        // we find the response with the highest count
        int highestHash = 0;
        int highestCount = 0;
        for (Integer hash : bodyCount.keySet()) {
            int count = bodyCount.get(hash);
            if (count > highestCount) {
                highestCount = count;
                highestHash = hash;
            }
        }
        JSONObject resp = bodies.get(highestHash);
        if ( N-highestCount > F ) {
            throw new BadResponseException("More than F servers differed in their response.");
        }
        System.out.printf("%d servers answered with the same correct message, %d produced an error\n", highestCount, N-highestCount);
        return resp;
    }

    private JSONObject request(JSONObject req) throws BadResponseException {
        // for each open connection we send the message
        String nonce = MyCrypto.getRandomNonce();
        JSONObject respBody = broadcast(req, nonce);
        System.out.printf("Servers answered: %s \n", respBody.toString(2));
        return respBody;
    }

    public void printServersPublicKey() {
        servers.forEach(s -> System.out.println(MyCrypto.publicKeyToB64String(s.serverPublicKey)));
    }

    /**
     * Register the user
     */
    void register() throws BadResponseException {
        JSONObject req = new JSONObject();
        req.put(Parameters.action.name(), Action.REGISTER.name());
        request(req);
    }

    /**
     * Wrapper to post an announcement in the specified board
     *
     * @param message       String corresponding to the message
     * @param announcements List of announcements ids to refer to
     */
    void post(String message, List<Integer> announcements) throws BadResponseException {
        genericPost(message, announcements, Action.POST);
    }

    /**
     * Wrapper to post an announcement in the general board
     *
     * @param message       String corresponding to the message
     * @param announcements List of announcements ids to refer to
     */
    void postGeneral(String message, List<Integer> announcements) throws BadResponseException {
        genericPost(message, announcements, Action.POSTGENERAL);
    }

    /**
     * Code logic to post an announcement in a board, meant to be called by post() or postGeneral() methods
     *
     * @param message       String corresponding to the message
     * @param announcements List of announcements ids to refer to
     * @param action        Action chosen (POST or POST_GENERAL)
     */
    private void genericPost(String message, List<Integer> announcements, Action action) throws BadResponseException {
        JSONObject req = new JSONObject();
        JSONObject postData = new JSONObject();
        JSONArray ann = new JSONArray(announcements);
        // we want to generate the signature of the post first
        postData.put(Parameters.message.name(), message);
        postData.put(Parameters.announcements.name(), ann);
        postData.put(Parameters.action.name(), action);
        String postSig = null;
        try {
            postSig = MyCrypto.digestAndSignToB64(postData.toString().getBytes(), clientPrivateKey);
        } catch (NoSuchAlgorithmException | IllegalBlockSizeException | InvalidKeyException | BadPaddingException | NoSuchPaddingException e) {
            throw new InternalError(e);
        }
        // now we add the stuff the second package
        req.put(Parameters.message.name(), message);
        req.put(Parameters.announcements.name(), ann);
        req.put(Parameters.action.name(), action);
        // we add the post signature
        req.put(Parameters.post_signature.name(), postSig);
        // we sign and send
        request(req);
    }

    /**
     * Read announcements from the specified board
     *
     * @param key    Base64 encoded String corresponding to the Board public key
     * @param number int corresponding to the number of announcements to read (0 for all announcements)
     */
    void read(String key, int number) throws BadResponseException {
        JSONObject req = new JSONObject();
        req.put(Parameters.action.name(), Action.READ.name());
        req.put(Parameters.board_public_key.name(), key);
        req.put(Parameters.number.name(), number);
        request(req);
    }

    /**
     * Read announcements from the general board
     *
     * @param number int corresponding to the number of announcements to read (0 for all announcements)
     */
    void readGeneral(int number) throws BadResponseException {
        JSONObject req = new JSONObject();
        req.put(Parameters.action.name(), Action.READGENERAL.name());
        req.put(Parameters.number.name(), number);
        request(req);
    }
}
