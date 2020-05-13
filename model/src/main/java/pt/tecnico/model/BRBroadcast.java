package pt.tecnico.model;

import org.json.JSONObject;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Implements a Byzantine Fault Tolerant Reliable Broadcast protocol
 */
public class BRBroadcast {
    private final List<ServerChannel> servers;
    private final int faultyServersCount;
    private final List<JSONObject> deliveredMessages;
    private final Map<JSONObject, ArrayList<ServerChannel>> echoSent;
    private final Map<JSONObject, ArrayList<ServerChannel>> readySent;
    private final int port;

    /**
     * Constructs an instance of the Byzantine Reliable Broadcast protocol class
     *
     * @param faultyServersCount int corresponding to the maximum number of nodes that may be faulty in the network
     * @param servers            list of servers
     */
    public BRBroadcast(int faultyServersCount, List<ServerChannel> servers, int port) {
        if (servers.size() > 3 * faultyServersCount) {
            this.faultyServersCount = faultyServersCount;
            this.servers = servers;
            this.deliveredMessages = new CopyOnWriteArrayList<>();
            this.echoSent = new ConcurrentHashMap<>();
            this.readySent = new ConcurrentHashMap<>();
            this.port = port;
        } else {
            throw new IllegalArgumentException("Number of servers doesn't satisfy N>3f assumption");
        }
    }

    /**
     * Broadcast method that sends a message to each server of the BRBroadcast object
     *  @param broadcast String corresponding to the "broadcast" JSON key. Can be "SEND", "ECHO" or "READY"
     * @param msg       JSONObject corresponding to the message to broadcast
     */
    public JSONObject broadcast(String broadcast, JSONObject msg) throws BadResponseException, BadSignatureException, IOException {
        JSONObject outOfBodyParam = new JSONObject();
        outOfBodyParam.put(Parameters.broadcast.name(), broadcast);
        int errors = 0;
        for (ServerChannel s : servers) {
            if (s.port == port) continue;
            try {
                s.send(msg, outOfBodyParam);
            } catch (BadResponseException | IOException | BadSignatureException e) {
                errors++;
                System.out.printf("Server: %d produced error: %s\n", s.port, e.getMessage());
            }
        }
        if (errors > faultyServersCount) {
            throw new IllegalStateException(String.format("More than F(%d) servers responded with an error\n", faultyServersCount));
        }
        return listen();
    }

    public JSONObject broadcast(JSONObject msg) throws BadResponseException, BadSignatureException, IOException {
        return broadcast("SEND", msg);
    }
    /**
     * Listen method to wait for any server to receive a new message, then apply the
     * Byzantine Fault Tolerant Reliable Broadcast protocol
     *
     * @return JSONObject in case a message that was not yet delivered was received
     */
    public JSONObject listen() throws BadResponseException, BadSignatureException, IOException {
        JSONObject response = null;

        while (response == null) {
            for (ServerChannel s : servers) {
                if (s.port == port) continue;
                response = listenOnServer(s);
            }
        }

        return response;
    }

    public JSONObject listenOnServer(ServerChannel s) throws BadResponseException, BadSignatureException, IOException {
        JSONObject response = s.listen();
        if (response == null) {
            return null;
        }
        // Check that the message is not already delivered
        if (!deliveredMessages.contains(response)) {
            String broadcast = response.getString(Parameters.broadcast.name());

            if (broadcast.equals("SEND") && !echoSent.containsKey(response)) {
                // Add the message to the echoSent map
                echoSent.put(response, new ArrayList<>());
                // Broadcast the ECHO message
                broadcast("ECHO", response);
            } else if (broadcast.equals("ECHO")) {
                if (!echoSent.containsKey(response)) {
                    // Add the message to the echoSent map
                    echoSent.put(response, new ArrayList<>());
                    // Add the server to the corresponding message in the echoSent map
                    echoSent.get(response).add(s);
                } else {
                    // Add the server to the corresponding message in the echoSent map
                    echoSent.get(response).add(s);
                    // If more than (N+f)/2 ECHO messages received, send READY message
                    if ((echoSent.get(response).size() > (servers.size() + faultyServersCount) / 2)
                            && !readySent.containsKey(response)) {
                        // Add the message to the readySent map
                        readySent.put(response, new ArrayList<>());
                        // Broadcast the READY message
                        broadcast("READY", response);
                    }
                }
            } else if (broadcast.equals("READY")) {
                // If a SEND and ECHO messages were not received, but a READY message was received
                if (!readySent.containsKey(response)) {
                    // Add the message to the readySent map
                    readySent.put(response, new ArrayList<>());
                    // Add the server to the corresponding message in the readySent map
                    readySent.get(response).add(s);
                } else {
                    // Add the server to the corresponding message in the readySent map
                    readySent.get(response).add(s);
                    // If 2*f READY messages have been delivered, DELIVER the message
                    if (readySent.get(response).size() > 2 * faultyServersCount) {
                        // Add the message to the deliveredMessages array
                        deliveredMessages.add(response);
                        // Deliver the message
                        return response;
                    } else if (!echoSent.containsKey(response) && readySent.get(response).size() > faultyServersCount) {  // If a SEND and ECHO messages were not received, but f READY messages were received, send a READY message
                        broadcast("READY", response);
                    }
                }
            } else {
                // In any other broadcast value (should not happen), reset the response to stay in the while loop
                response = null;
            }
        } else {
            // In case the message is already delivered, reset the response to stay in the while loop
            response = null;
        }
        return response;
    }
}
