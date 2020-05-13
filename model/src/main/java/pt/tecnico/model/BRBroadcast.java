package pt.tecnico.model;

import org.json.JSONObject;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.concurrent.*;

/**
 * Implements a Byzantine Fault Tolerant Reliable Broadcast protocol
 */
public class BRBroadcast {
    private final List<ServerChannel> servers;
    private final int faultyServersCount;
    private final List<JSONObject> deliveredMessages;
    private final Map<String, List<ServerChannel>> echoSent;
    private final Map<String, List<ServerChannel>> readySent;
    private final int port;
    private final ExecutorService executorService;

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
            this.executorService = Executors.newFixedThreadPool(servers.size() * 4);
        } else {
            throw new IllegalArgumentException("Number of servers doesn't satisfy N>3f assumption");
        }
    }

    /**
     * Broadcast method that sends a message to each server of the BRBroadcast object
     *
     * @param broadcast String corresponding to the "broadcast" JSON key. Can be "SEND", "ECHO" or "READY"
     * @param msg       JSONObject corresponding to the message to broadcast
     */
    public void broadcast(String broadcast, JSONObject msg) {
        msg = new JSONObject(msg.toString());
        msg.put(Parameters.broadcast.name(), broadcast);
        int errors = 0;
        for (ServerChannel s : servers) {
            if (s.port == port) continue;
            final JSONObject finalMsg = new JSONObject(msg.toString());
            executorService.submit(() -> s.send(finalMsg).toString());
        }
        if (errors > faultyServersCount) {
            throw new IllegalStateException(String.format("More than F(%d) servers responded with an error\n", faultyServersCount));
        }
    }

    /**
     * Broadcast method that sends a SEND message to each server of the BRBroadcast object
     *
     * @param msg JSONObject corresponding to the message to broadcast
     */
    public void broadcast(JSONObject msg) {
        broadcast("SEND", msg);
    }

    public JSONObject listen() throws BadResponseException, BadSignatureException, IOException {
        return listen(null, null);
    }

    /**
     * Listen method to wait for any server to receive a new message, then apply the
     * Byzantine Fault Tolerant Reliable Broadcast protocol
     *
     * @param firstMessage Initial JSONObject message sent by the server upon start
     * @param firstSc      ServerChannel whose thread runs this method
     * @return JSONObject in case a message that was not yet delivered was received
     */
    public JSONObject listen(JSONObject firstMessage, ServerChannel firstSc) throws BadResponseException, BadSignatureException, IOException {
        JSONObject response = null;
        while (response == null) {
            if (firstSc != null) {
                response = new JSONObject(firstMessage.toString());
                response = dealWithResponse(response, firstSc);
            } else {
                for (ServerChannel s : servers) {
                    try {
                        response = executorService.submit(() -> dealWithResponse(s.read(), s)).get();
                    } catch (InterruptedException | ExecutionException e) {
                        e.printStackTrace();
                    }
                }
            }
        }

        return response;
    }

    /**
     * Byzantine Fault Tolerant Reliable Broadcast protocol
     *
     * @param response JSONObject message received
     * @param s        ServerChannel who received the message
     * @return JSONObject in case a message that was not yet delivered was received
     */
    private JSONObject dealWithResponse(JSONObject response, ServerChannel s) throws BadResponseException, BadSignatureException, IOException {
        if (response != null) {
            String clientNonce = response.getString(Parameters.client_nonce.name());
            // Check that the message is not already delivered
            if (!deliveredMessages.contains(response)) {
                String broadcast = response.getString(Parameters.broadcast.name());
                if (broadcast.equals("SEND") && !echoSent.containsKey(clientNonce)) {
                    // Add the message to the echoSent map
                    echoSent.put(clientNonce, new CopyOnWriteArrayList());
                    // Broadcast the ECHO message
                    broadcast("ECHO", response);
                } else if (broadcast.equals("ECHO")) {
                    if (!echoSent.containsKey(clientNonce)) {
                        // Add the message to the echoSent map
                        echoSent.put(clientNonce, new CopyOnWriteArrayList<>());
                        // Add the server to the corresponding message in the echoSent map
                        echoSent.get(clientNonce).add(s);
                    } else {
                        // Add the server to the corresponding message in the echoSent map
                        echoSent.get(clientNonce).add(s);
                        // If more than (N+f)/2 ECHO messages received, send READY message
                        if ((echoSent.get(clientNonce).size() > (servers.size() + faultyServersCount) / 2)
                                && !readySent.containsKey(clientNonce)) {
                            // Add the message to the readySent map
                            readySent.put(clientNonce, new CopyOnWriteArrayList<>());
                            // Broadcast the READY message
                            broadcast("READY", response);
                        }
                    }
                } else if (broadcast.equals("READY")) {
                    // If a SEND and ECHO messages were not received, but a READY message was received
                    if (!readySent.containsKey(clientNonce)) {
                        // Add the message to the readySent map
                        readySent.put(clientNonce, new CopyOnWriteArrayList<>());
                        // Add the server to the corresponding message in the readySent map
                        readySent.get(clientNonce).add(s);
                    } else {
                        // Add the server to the corresponding message in the readySent map
                        readySent.get(clientNonce).add(s);
                        // If 2*f READY messages have been delivered, DELIVER the message
                        if (readySent.get(clientNonce).size() > 2 * faultyServersCount) {
                            // Add the message to the deliveredMessages array
                            deliveredMessages.add(response);
                            // Deliver the message
                            return response;
                        } else if (!echoSent.containsKey(clientNonce) && readySent.get(clientNonce).size() > faultyServersCount) {  // If a SEND and ECHO messages were not received, but f READY messages were received, send a READY message
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
        }
        return response;
    }
}
