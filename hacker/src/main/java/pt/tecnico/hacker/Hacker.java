package pt.tecnico.hacker;

import org.beryx.textio.TextIO;
import org.beryx.textio.TextIoFactory;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Hacker class used as a proxy server (between a server and a client) with the Dependable Public Announcement Server
 */
public class Hacker {
    private static final int INIT = 0;
    private static final int SECOND = 1;
    private static final int THIRD = 2;
    private static final int LAST = 3;
    private ServerSocket ourSocket;
    private Socket serverSocket;
    private PrintWriter serverOut;
    private BufferedReader serverIn;
    private Socket clientSocket;
    private PrintWriter clientOut;
    private BufferedReader clientIn;
    private TextIO textIO;

    /**
     * Main entrypoint for hacker module
     *
     * @param args Syntax: Syntax: hacker <hacker-port> <server-ip> <server-port>
     */
    public static void main(String[] args) {
        // We need the the path of the folder where to save the keys
        if (args.length != 3)
            System.out.println("Syntax: hacker <hacker-port> <server-ip> <server-port>");
        else {
            try {
                // we connect to the server
                Hacker hacker = new Hacker();
                hacker.start(Integer.parseInt(args[0]), args[1], Integer.parseInt(args[2]));
            } catch (Exception e) {
                System.err.println(e.getMessage());
            }
        }
    }

    /**
     * Open a new Socket listening on the specified port
     *
     * @param myPort int corresponding to the hacker port to listen to
     * @throws IOException in case an an I/O error occurs
     */
    private void openMe(int myPort) throws IOException {
        // creating our port
        ourSocket = new ServerSocket(myPort);
        System.out.println("Hacker up on port: " + myPort);
    }

    /**
     * Wait for a client to connect, then connect to the specified server
     *
     * @param serverIp   String corresponding to the ip address to run the server on
     * @param serverPort int corresponding to the port to listen to
     * @throws IOException in case an an I/O error occurs
     */
    private void openServerAndClient(String serverIp, int serverPort) throws IOException {
        // wait for client
        System.out.println("Waiting for a client to connect");
        clientSocket = ourSocket.accept();
        clientIn = new BufferedReader(new InputStreamReader(clientSocket.getInputStream()));
        clientOut = new PrintWriter(clientSocket.getOutputStream(), true, StandardCharsets.UTF_8);
        System.out.println("New TCP connection with the client opened on port " + clientSocket.getLocalPort());
        // connecting to server
        System.out.println("Connecting to server");
        serverSocket = new Socket(serverIp, serverPort);
        serverOut = new PrintWriter(serverSocket.getOutputStream(), true);
        serverIn = new BufferedReader(new InputStreamReader(serverSocket.getInputStream()));
        System.out.println("New TCP connection with the server opened on port " + serverSocket.getLocalPort());
    }

    /**
     * Get a message from a client or the server, and print it in the corresponding color
     *
     * @param in BufferedReader corresponding to the buffer to read
     * @return JSONObject corresponding to the read object
     * @throws IOException in case an an I/O error occurs
     */
    private JSONObject read(BufferedReader in) throws IOException {
        System.out.println("Waiting for a message to arrive");
        JSONObject jo = new JSONObject(in.readLine());
        if (in.equals(clientIn)) {
            System.out.println("New message from client arrived:");
            print(Color.CLIENT, jo.toString(2));
        } else {
            System.out.println("New message from server arrived:");
            print(Color.SERVER, jo.toString(2));
        }
        return jo;
    }

    /**
     * Start the selection menu (for interactivity) and parse the user choice
     *
     * @param myPort     int corresponding to the hacker port to listen to
     * @param serverIp   String corresponding to the ip address to run the server on
     * @param serverPort int corresponding to the port to listen to
     * @throws IOException in case an an I/O error occurs
     */
    private void start(int myPort, String serverIp, int serverPort) throws IOException {
        textIO = TextIoFactory.getTextIO();
        System.out.println("Hello. This is the hacker proxy for the for the Highly Dependable Announcement Server project.");
        openMe(myPort);
        List<String> enumNames = Stream.of(Options.values()).map(Enum::name).collect(Collectors.toList());
        boolean end = false;
        int state = INIT;
        BufferedReader in;
        PrintWriter out;
        //noinspection ConstantConditions
        while (!end) {
            switch (state) {
                case INIT:
                    openServerAndClient(serverIp, serverPort);
                case THIRD:
                    in = clientIn;
                    out = serverOut;
                    break;
                case SECOND:
                case LAST:
                    in = serverIn;
                    out = clientOut;
                    break;
                default:
                    throw new IllegalStateException("Unexpected value: " + state);
            }
            state = (state + 1) % 4;
            JSONObject message = read(in);
            String option = textIO.newStringInputReader()
                    .withNumberedPossibleValues(enumNames)
                    .read("What do you want to do with it?");
            switch (Options.valueOf(option)) {
                case PASS:
                    out.println(message.toString());
                    break;
                case EDIT:
                    boolean done = false;
                    while (!done) {
                        String field = textIO.newStringInputReader().read("Type the field to edit:");
                        String value = textIO.newStringInputReader().read("Type the STRING value to insert");
                        message.put(field, value);
                        done = !textIO.newBooleanInputReader().withDefaultValue(false).read("Edit more fields?");
                    }
                    System.out.println("This message is going to be sent:");
                    System.out.println(message.toString(2));
                    if (!textIO.newBooleanInputReader().withDefaultValue(true).read("Continue?"))
                        continue;
                    out.println(message.toString());
                    break;
                case DROP:
                    state = INIT;
                    break;
                case DUPLICATE:
                    System.out.println("This message is going to be sent twice:");
                    System.out.println(message.toString(2));
                    if (!textIO.newBooleanInputReader().withDefaultValue(true).read("Continue?"))
                        continue;
                    out.println(message.toString());
                    out.println(message.toString());
                    break;
                case EXIT:
                    end = true;
                    return;
            }
        }
    }

    /**
     * Print a message in the specified color
     *
     * @param color Color corresponding to the ANSI color code
     * @param s     String corresponding to the message to print
     */
    private void print(Color color, String s) {
        System.out.println(color + s + Color.RESET);
    }

    /**
     * Different actions available from the user menu
     */
    enum Options {
        PASS,
        EDIT,
        DROP,
        DUPLICATE,
        EXIT
    }

    /**
     * Different ANSI color codes used to differentiate the client and server in the console
     */
    enum Color {
        CLIENT("\u001B[33m"),      // Yellow ANSI color code
        SERVER("\u001B[34m"),      // Blue ANSI color code
        RESET("\u001B[0m");        // Reset ANSI color code

        private String colorCode;

        Color(String colorCode) {
            this.colorCode = colorCode;
        }

        public String toString() {
            return colorCode;
        }
    }
}

