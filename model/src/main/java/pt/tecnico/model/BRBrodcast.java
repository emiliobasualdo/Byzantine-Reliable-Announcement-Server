package pt.tecnico.model;

import org.json.JSONObject;

import java.util.List;

public class BRBrodcast {
    private final List<Server> servers;
    // todo Each instance of this class should broadcast only ONE message or listen to ONE message
    // example 1:
    // brb = new BRBrodcast(listOfServers);
    // brb.broadcast(jsonObject)
    //
    // example 2:
    // brb = new BRBrodcast(listOfServers);
    // brb.listen()

    public BRBrodcast(List<Server> servers) {
        this.servers = servers;
    }

    public void brodcast(JSONObject msg) {

    }

    public JSONObject listen() {
        return new JSONObject();
    }
}
