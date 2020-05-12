package pt.tecnico.model;

import org.json.JSONObject;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Implements a Byzantine Fault Tolerant Reliable Broadcast protocol
 */
public class BRBroadcast {
    private final List<ServerChannel> servers;
    private final int faultyServersCount;
    private final List<JSONObject> deliveredMessages;
    private final Map<JSONObject, ArrayList<ServerChannel>> echoSent;
    private final Map<JSONObject, ArrayList<ServerChannel>> readySent;

    // todo Each instance of this class should broadcast only ONE message or listen to ONE message
    // example 1:
    // brb = new BRBroadcast(faultyServersCount, listOfServers);
    // brb.broadcast(jsonObject)
    //
    // example 2:
    // brb = new BRBroadcast(faultyServersCount, listOfServers);
    // brb.listen()

    /**
     * Constructs an instance of the Byzantine Reliable Broadcast protocol class
     *
     * @param faultyServersCount int corresponding to the maximum number of nodes that may be faulty in the network
     * @param servers            list of servers
     */
    public BRBroadcast(int faultyServersCount, List<ServerChannel> servers) {
        if (servers.size() > 3 * faultyServersCount) {
            this.faultyServersCount = faultyServersCount;
            this.servers = servers;
            this.deliveredMessages = new ArrayList<>();
            this.echoSent = new HashMap<>();
            this.readySent = new HashMap<>();
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
    public void broadcast(String broadcast, JSONObject msg) throws BadResponseException, BadSignatureException, IOException {
        msg.put(Parameters.broadcast.name(), broadcast);
        for (ServerChannel s : servers) {
            s.send(msg);
        }
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
                response = s.read();
                if (response != null) {
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
                                    break;
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
                }
            }
        }

        return response;
    }
}
