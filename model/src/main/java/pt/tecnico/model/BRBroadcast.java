package pt.tecnico.model;

import org.json.JSONObject;

import java.util.List;

public class BRBroadcast {
    private final List<ServerChannel> servers;
    // todo Each instance of this class should broadcast only ONE message or listen to ONE message
    // example 1:
    // brb = new BRBrodcast(listOfServers);
    // brb.broadcast(jsonObject)
    //
    // example 2:
    // brb = new BRBrodcast(listOfServers);
    // brb.listen()

    public BRBroadcast(List<ServerChannel> servers) {
        this.servers = servers;
    }

    public JSONObject broadcast(JSONObject msg) {
        return msg;
    }

    public JSONObject listen() {
        return new JSONObject();
    }
}
