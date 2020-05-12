package pt.tecnico.server;

import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.jupiter.api.*;
import pt.tecnico.model.Action;
import pt.tecnico.model.MyCrypto;
import pt.tecnico.model.Parameters;
import pt.tecnico.model.Status;

import javax.crypto.BadPaddingException;
import javax.crypto.IllegalBlockSizeException;
import javax.crypto.NoSuchPaddingException;
import java.io.BufferedReader;
import java.io.PrintWriter;
import java.net.Socket;
import java.security.*;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;

class ServerThreadTest {
    public static final String ANSI_BLUE = "\u001B[34m";
    public static final String ANSI_RESET = "\u001B[0m";
    private static PrivateKey serverPrivateKey;
    private static PublicKey serverPublicKey;
    private static PrivateKey clientPrivateKey;
    private static PublicKey clientPublicKey;
    private static ServerThread serverThread;
    private static Socket clientSocket;
    private static String client_nonce;
    private static String server_nonce;

    @BeforeAll
    static void beforeAll() throws NoSuchAlgorithmException {
        KeyPair kp = MyCrypto.generateKeyPair();
        serverPrivateKey = kp.getPrivate();
        serverPublicKey = kp.getPublic();
        kp = MyCrypto.generateKeyPair();
        clientPrivateKey = kp.getPrivate();
        clientPublicKey = kp.getPublic();
        clientSocket = mock(Socket.class);
    }

    @BeforeEach
    void beforeEach(TestInfo testInfo) {
        System.out.println(ANSI_BLUE + "Test: " + testInfo.getDisplayName() + " start" + ANSI_RESET);
        PrintWriter out = mock(PrintWriter.class);
        BufferedReader in = mock(BufferedReader.class);
        clientSocket = mock(Socket.class);
        serverThread = new ServerThread(mock(Twitter.class), serverPrivateKey, clientSocket, in, out);
    }

    @AfterEach
    void afterEach() {
        System.out.println(ANSI_BLUE + "______________End______________\n\n\n" + ANSI_RESET);
    }

    private void digestAndSign(JSONObject jo) throws InvalidKeyException, BadPaddingException, NoSuchAlgorithmException, IllegalBlockSizeException, NoSuchPaddingException {
        String sig = MyCrypto.digestAndSignToB64(jo.toString().getBytes(), clientPrivateKey);
        jo.put(Parameters.signature.name(), sig);
    }

    private JSONObject sendAndSet(JSONObject jo) throws IllegalBlockSizeException, NoSuchPaddingException, BadPaddingException, NoSuchAlgorithmException, InvalidKeyException {
        JSONObject resp = send(jo);
        client_nonce = jo.getString(Parameters.client_nonce.name());
        server_nonce = resp.getString(Parameters.server_nonce.name());
        return resp;
    }

    private JSONObject send(JSONObject req) throws NoSuchAlgorithmException, InvalidKeyException, BadPaddingException, NoSuchPaddingException, IllegalBlockSizeException {
        return serverThread.receive(req.toString());
    }

    private void setNonces(JSONObject req) {
        req.put(Parameters.client_nonce.name(), client_nonce);
        req.put(Parameters.server_nonce.name(), server_nonce);
    }

    private JSONObject makeInitialPackage() throws IllegalBlockSizeException, NoSuchPaddingException, BadPaddingException, NoSuchAlgorithmException, InvalidKeyException {
        JSONObject jo = new JSONObject();
        jo.put(Parameters.client_public_key.name(), MyCrypto.publicKeyToB64String(clientPublicKey));
        jo.put(Parameters.client_nonce.name(), MyCrypto.getRandomNonce());
        digestAndSign(jo);
        return jo;
    }

    private JSONObject makeSecondPackage() throws IllegalBlockSizeException, NoSuchPaddingException, BadPaddingException, NoSuchAlgorithmException, InvalidKeyException {
        JSONObject resp = new JSONObject();
        resp.put(Parameters.action.name(), Action.POSTGENERAL.name());
        resp.put(Parameters.message.name(), "This is a message");
        resp.put(Parameters.announcements.name(), new JSONArray(List.of(1, 2, 3)));
        String sig = MyCrypto.digestAndSignToB64(resp.toString().getBytes(), clientPrivateKey);
        resp.put(Parameters.post_signature.name(), sig);
        resp.put(Parameters.client_public_key.name(), MyCrypto.publicKeyToB64String(clientPublicKey));
        resp.put(Parameters.client_nonce.name(), client_nonce);
        resp.put(Parameters.server_nonce.name(), server_nonce);
        setNonces(resp);
        digestAndSign(resp);
        return resp;
    }

    @Test
    void test_correct_first_step_returns_both_nonces() throws InvalidKeyException, BadPaddingException, NoSuchAlgorithmException, IllegalBlockSizeException, NoSuchPaddingException {
        System.out.println("After first packet Server should return both nonces");
        // SETUP
        JSONObject initJo = makeInitialPackage();
        System.out.println("Initial package send by the client, with random nonce: ");
        System.out.println(initJo.toString(2));
        // EXERCISE
        JSONObject resp = sendAndSet(initJo);
        System.out.println("Server answers with the client's nonce and new server nonce: ");
        System.out.println(initJo.toString(2));
        // ASSERT
        assertEquals(resp.getString(Parameters.client_nonce.name()), initJo.getString(Parameters.client_nonce.name()));
        assertEquals(resp.getString(Parameters.server_nonce.name()).length(), MyCrypto.NONCE_LENGTH);
    }

    @Test
    void test_correct_second_step_returns_both_nonces() throws InvalidKeyException, BadPaddingException, NoSuchAlgorithmException, IllegalBlockSizeException, NoSuchPaddingException {
        System.out.println("After second packet, the server should return both nonces and there should be no problem with signatures");
        // SETUP
        JSONObject firstreq = makeInitialPackage();
        JSONObject firstResp = sendAndSet(firstreq);
        JSONObject secondReq = makeSecondPackage();
        System.out.println("Client's second packet with example action but with both nonce");
        System.out.println(secondReq.toString(2));
        // EXERCISE
        JSONObject secondResp = send(secondReq);
        System.out.println("Server returns both nonces in second package to confirm that we are still in the same communication");
        System.out.println(secondReq.toString(2));
        // ASSERT
        assertEquals(firstResp.getString(Parameters.client_nonce.name()), client_nonce);
        assertEquals(firstResp.getString(Parameters.server_nonce.name()), server_nonce);

        assertEquals(secondReq.getString(Parameters.client_nonce.name()), firstreq.getString(Parameters.client_nonce.name()));
        assertEquals(secondReq.getString(Parameters.server_nonce.name()), server_nonce);

        assertEquals(secondResp.getString(Parameters.client_nonce.name()), client_nonce);
        assertEquals(secondResp.getString(Parameters.server_nonce.name()), server_nonce);
    }

    @Test
    void test_illegal_first_nonce_returns_error() throws InvalidKeyException, BadPaddingException, NoSuchAlgorithmException, IllegalBlockSizeException, NoSuchPaddingException {
        System.out.println("If client sends an illegal nonce, ex: tampered message, server answers with an error");
        // SETUP
        JSONObject req = new JSONObject();
        req.put(Parameters.client_public_key.name(), MyCrypto.publicKeyToB64String(clientPublicKey));
        req.put(Parameters.client_nonce.name(), "aaaaa");
        digestAndSign(req);
        System.out.println("Client's packet:");
        System.out.println(req.toString(2));
        // EXERCISE
        JSONObject resp = send(req);
        System.out.println("Server's answer");
        System.out.println(resp.toString(2));
        // ASSERT
        assertEquals(resp.getString(Parameters.status.name()), Status.CLIENT_ERROR.name());
        assertEquals(resp.getString(Parameters.err_msg.name()), "Illegal nonce");
    }

    @Test
    void test_illegal_client_second_nonce_returns_error() throws InvalidKeyException, BadPaddingException, NoSuchAlgorithmException, IllegalBlockSizeException, NoSuchPaddingException {
        System.out.println("If client sends an illegal client nonce in the second package, ex: an old one, server answers with an error");
        JSONObject req = makeInitialPackage();
        JSONObject resp = sendAndSet(req);
        assertEquals(resp.getString(Parameters.status.name()), Status.OK.name());
        JSONObject secondReq = makeSecondPackage();
        // edit the nonce
        secondReq.put(Parameters.client_nonce.name(), MyCrypto.getRandomNonce());
        secondReq.remove(Parameters.signature.name());
        digestAndSign(secondReq);
        System.out.println("Client's nonce should be: " + client_nonce + " but we change it:");
        System.out.println(secondReq.toString(2));
        // EXERCISE
        resp = send(secondReq);
        System.out.println("Server's answer:");
        System.out.println(resp.toString(2));
        // ASSERT
        assertEquals(resp.getString(Parameters.status.name()), Status.CLIENT_ERROR.name());
        assertEquals(resp.getString(Parameters.err_msg.name()), "Illegal nonce");
    }

    @Test
    void test_illegal_server_second_nonce_returns_error() throws InvalidKeyException, BadPaddingException, NoSuchAlgorithmException, IllegalBlockSizeException, NoSuchPaddingException {
        System.out.println("If client sends an illegal server nonce in the second package, ex: an old one, server answers with an error");
        JSONObject req = makeInitialPackage();
        JSONObject resp = sendAndSet(req);
        assertEquals(resp.getString(Parameters.status.name()), Status.OK.name());
        JSONObject secondReq = makeSecondPackage();
        // edit the nonce
        System.out.println("Server's's nonce should be: " + server_nonce + " but we change it:");
        secondReq.put(Parameters.server_nonce.name(), MyCrypto.getRandomNonce());
        secondReq.remove(Parameters.signature.name());
        digestAndSign(secondReq);
        System.out.println(secondReq.toString(2));
        // EXERCISE
        resp = send(secondReq);
        System.out.println("Server's answer:");
        System.out.println(resp.toString(2));
        // ASSERT
        assertEquals(resp.getString(Parameters.status.name()), Status.CLIENT_ERROR.name());
        assertEquals(resp.getString(Parameters.err_msg.name()), "Illegal nonce");
    }

    @Test
    void test_illegal_first_signature_returns_error() throws InvalidKeyException, BadPaddingException, NoSuchAlgorithmException, IllegalBlockSizeException, NoSuchPaddingException {
        System.out.println("If client sends an illegal signature in the first packet the server realises");
        JSONObject req = makeInitialPackage();
        System.out.println("Signature should be: " + req.getString(Parameters.signature.name()).substring(0, 20) + "... but we change it.");
        req.put(Parameters.signature.name(), MyCrypto.digestAndSignToB64(new byte[]{1, 2, 3, 4}, clientPrivateKey));
        System.out.println(req.toString(2));
        // EXERCISE
        JSONObject resp = send(req);
        System.out.println("Server's answer:");
        System.out.println(resp.toString(2));
        // ASSERT
        assertEquals(resp.getString(Parameters.status.name()), Status.CLIENT_ERROR.name());
        assertEquals(resp.getString(Parameters.err_msg.name()), "Signature does not match the body");
    }

    @Test
    void test_illegal_second_signature_returns_error() throws InvalidKeyException, BadPaddingException, NoSuchAlgorithmException, IllegalBlockSizeException, NoSuchPaddingException {
        System.out.println("If client sends an illegal signature in the second packet the server realises");
        JSONObject req = makeInitialPackage();
        sendAndSet(req);
        req = makeSecondPackage();
        System.out.println("Signature should be: " + req.getString(Parameters.signature.name()).substring(0, 20) + "... but we change it.");
        req.put(Parameters.signature.name(), MyCrypto.digestAndSignToB64(new byte[]{1, 2, 3, 4}, clientPrivateKey));
        System.out.println(req.toString(2));
        // EXERCISE
        JSONObject resp = send(req);
        System.out.println("Server's answer:");
        System.out.println(resp.toString(2));
        // ASSERT
        assertEquals(resp.getString(Parameters.status.name()), Status.CLIENT_ERROR.name());
        assertEquals(resp.getString(Parameters.err_msg.name()), "Signature does not match the body");
    }

    @Test
    void test_illegal_post_signature_returns_error() throws InvalidKeyException, BadPaddingException, NoSuchAlgorithmException, IllegalBlockSizeException, NoSuchPaddingException {
        System.out.println("If client sends an illegal post_signature, even if the global signature is correct, the server return an error");
        JSONObject req = makeInitialPackage();
        sendAndSet(req);
        req = makeSecondPackage();
        System.out.println("The post_signature should be: " + req.getString(Parameters.post_signature.name()).substring(0, 20) + "... but we change it.");
        req.put(Parameters.post_signature.name(), MyCrypto.digestAndSignToB64(new byte[]{1, 2, 3, 4}, clientPrivateKey));
        req.remove(Parameters.signature.name());
        digestAndSign(req);
        System.out.println(req.toString(2));
        // EXERCISE
        JSONObject resp = send(req);
        System.out.println("Server's answer:");
        System.out.println(resp.toString(2));
        // ASSERT
        assertEquals( Status.CLIENT_ERROR.name(), resp.getString(Parameters.status.name()));
        assertEquals("Post signature does not match the post body", resp.getString(Parameters.err_msg.name()));
    }

    @Test
    void test_duplicate_first_package_returns_error() throws InvalidKeyException, BadPaddingException, NoSuchAlgorithmException, IllegalBlockSizeException, NoSuchPaddingException {
        System.out.println("If client sends the first package twice, ex: duplicate attack, the server return an error");
        JSONObject req = makeInitialPackage();
        System.out.println("Client package:");
        System.out.println(req.toString(2));
        System.out.println("Sending once...");
        sendAndSet(req);
        // EXERCISE
        System.out.println("Sending twice...");
        JSONObject resp = send(req);
        System.out.println("Server's answer:");
        System.out.println(resp.toString(2));
        // ASSERT
        assertEquals(resp.getString(Parameters.status.name()), Status.CLIENT_ERROR.name());
        assertEquals(resp.getString(Parameters.err_msg.name()), "JSONObject[\"server_nonce\"] not found.");
    }
}