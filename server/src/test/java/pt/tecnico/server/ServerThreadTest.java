package pt.tecnico.server;

import org.json.JSONObject;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import pt.tecnico.model.Action;
import pt.tecnico.model.MyCrypto;
import pt.tecnico.model.Parameters;
import pt.tecnico.model.Status;

import javax.crypto.BadPaddingException;
import javax.crypto.IllegalBlockSizeException;
import javax.crypto.NoSuchPaddingException;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.PrintWriter;
import java.net.Socket;
import java.security.*;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;

class ServerThreadTest {

    private static PrivateKey serverPrivateKey;
    private static PublicKey serverPublicKey;

    private static PrivateKey clientPrivateKey;
    private static PublicKey clientPublicKey;

    private static ServerThread serverThread;
    private static Socket clientSocket;

    private static String client_nonce;
    private static String server_nonce;

    private static final String randString = "NLFAG6qzEafasdfasfdasdfRoCiN0\nRdRswckfkcgSZClNamchh7nsMo3Zl6dhKIJyF39Sg2iLyVovJcwTouelyEw0AXyVXjyGZ5ygcqi9n4ZqI0KM7x81Wlqf3PAz2K3ODnU\nnfqMWBQSHuABdMx4yFl3jKSjQ3IECuNadPsof6Irj3Vrd7ePWM2uJLvig8r71MjYhl7geI6RDqO7qXm7JyIOGzRpq9d5yt6JrtVMXcJ0wnIQdUEB8dQQbW3afVufR8STTTaBg4";

    @BeforeAll
    static void beforeAll() throws NoSuchAlgorithmException {
        KeyPair kp = MyCrypto.generateKeyPair();
        serverPrivateKey = kp.getPrivate();
        serverPublicKey = kp.getPublic();
        kp = MyCrypto.generateKeyPair();
        clientPrivateKey = kp.getPrivate();
        clientPublicKey = kp.getPublic();
        clientSocket = mock(Socket.class);
    };

    @BeforeEach
    void beforeEach() {
        PrintWriter out = mock(PrintWriter.class);
        BufferedReader in = mock(BufferedReader.class);
        clientSocket = mock(Socket.class);
        serverThread = new ServerThread(mock(Twitter.class), serverPrivateKey, clientSocket, in, out);
    };

    private void digestAndSign(JSONObject jo) throws InvalidKeyException, BadPaddingException, NoSuchAlgorithmException, IllegalBlockSizeException, NoSuchPaddingException {
        String sig = MyCrypto.digestAndSignToB64(jo.toString().getBytes(), clientPrivateKey);
        jo.put(Parameters.signature.name(), sig);
    }

    private JSONObject sendAndSet(JSONObject jo) throws IllegalBlockSizeException, NoSuchPaddingException, BadPaddingException, NoSuchAlgorithmException, InvalidKeyException {
        System.out.println(jo.toString());
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

    private JSONObject makeInitialPackage() throws IllegalBlockSizeException, NoSuchPaddingException, BadPaddingException, NoSuchAlgorithmException, InvalidKeyException, IOException {
        JSONObject jo = new JSONObject();
        jo.put(Parameters.client_public_key.name(), MyCrypto.publicKeyToB64String(clientPublicKey));
        jo.put(Parameters.client_nonce.name(), MyCrypto.getRandomNonce());
        digestAndSign(jo);
        return jo;
    }

    private JSONObject makeSecondPackage() throws IllegalBlockSizeException, NoSuchPaddingException, BadPaddingException, NoSuchAlgorithmException, InvalidKeyException {
        JSONObject resp = new JSONObject();
        resp.put(Parameters.client_public_key.name(), MyCrypto.publicKeyToB64String(clientPublicKey));
        resp.put(Parameters.board_public_key.name(), MyCrypto.publicKeyToB64String(clientPublicKey));
        resp.put(Parameters.number.name(), 3);
        resp.put(Parameters.action.name(), Action.READ.name());
        resp.put(Parameters.client_nonce.name(), client_nonce);
        resp.put(Parameters.server_nonce.name(), server_nonce);
        setNonces(resp);
        digestAndSign(resp);
        return resp;
    }

    @Test
    void test_correct_first_step_returns_both_nonces() throws InvalidKeyException, BadPaddingException, NoSuchAlgorithmException, IllegalBlockSizeException, NoSuchPaddingException, IOException {
        // SETUP
        JSONObject initJo = makeInitialPackage();
        // EXERCISE
        JSONObject resp = sendAndSet(initJo);
        // ASSERT
        assertEquals(resp.getString(Parameters.client_nonce.name()), initJo.getString(Parameters.client_nonce.name()));
        assertEquals(resp.getString(Parameters.server_nonce.name()).length(), MyCrypto.NONCE_LENGTH);
    }

    @Test
    void test_correct_second_step_returns_both_nonces() throws IOException, InvalidKeyException, BadPaddingException, NoSuchAlgorithmException, IllegalBlockSizeException, NoSuchPaddingException {
        // SETUP
        JSONObject firstreq = makeInitialPackage();
        JSONObject firstResp = sendAndSet(firstreq);
        JSONObject secondReq = makeSecondPackage();
        // EXERCISE
        JSONObject secondResp = send(secondReq);
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
        // SETUP
        JSONObject req = new JSONObject();
        req.put(Parameters.client_public_key.name(), MyCrypto.publicKeyToB64String(clientPublicKey));
        req.put(Parameters.client_nonce.name(), "");
        digestAndSign(req);
        // EXERCISE
        JSONObject resp = send(req);
        // ASSERT
        assertEquals(resp.getString(Parameters.status.name()), Status.CLIENT_ERROR.name());
        assertEquals(resp.getString(Parameters.err_msg.name()), "Illegal nonce");
    }

    @Test
    void test_illegal_client_second_nonce_returns_error() throws IOException, InvalidKeyException, BadPaddingException, NoSuchAlgorithmException, IllegalBlockSizeException, NoSuchPaddingException {
        JSONObject req = makeInitialPackage();
        JSONObject resp = sendAndSet(req);
        assertEquals(resp.getString(Parameters.status.name()), Status.OK.name());
        JSONObject secondReq = makeSecondPackage();
        // edit the nonce
        secondReq.put(Parameters.client_nonce.name(), MyCrypto.getRandomNonce());
        secondReq.remove(Parameters.signature.name());
        digestAndSign(secondReq);
        // EXERCISE
        resp = send(secondReq);
        // ASSERT
        assertEquals(resp.getString(Parameters.status.name()), Status.CLIENT_ERROR.name());
        assertEquals(resp.getString(Parameters.err_msg.name()), "Illegal nonce");
    }

    @Test
    void test_illegal_server_second_nonce_returns_error() throws IOException, InvalidKeyException, BadPaddingException, NoSuchAlgorithmException, IllegalBlockSizeException, NoSuchPaddingException {
        JSONObject req = makeInitialPackage();
        JSONObject resp = sendAndSet(req);
        assertEquals(resp.getString(Parameters.status.name()), Status.OK.name());
        JSONObject secondReq = makeSecondPackage();
        // edit the nonce
        secondReq.put(Parameters.server_nonce.name(), MyCrypto.getRandomNonce());
        secondReq.remove(Parameters.signature.name());
        digestAndSign(secondReq);
        // EXERCISE
        resp = send(secondReq);
        // ASSERT
        assertEquals(resp.getString(Parameters.status.name()), Status.CLIENT_ERROR.name());
        assertEquals(resp.getString(Parameters.err_msg.name()), "Illegal nonce");
    }

    /*@Test
    void test_correct_read_general_returns_200() throws IOException, InvalidKeyException, BadPaddingException, NoSuchAlgorithmException, IllegalBlockSizeException, NoSuchPaddingException {
        // SETUP
        JSONObject jo = new JSONObject();
        jo.put(Parameters.client_public_key.name(), MyCrypto.publicKeyToB64String(clientPublicKey));
        jo.put(Parameters.number.name(), 3);
        jo.put(Parameters.action.name(), Action.READGENERAL.name());
        String sig = MyCrypto.digestAndSignToB64(jo.toString().getBytes(), clientPrivateKey);
        jo.put(Parameters.signature.name(), sig);
        setRequestBody(jo.toString());
        // EXERCISE
        server.handle(httpExchange);
        // ASSERT
        verify(httpExchange).sendResponseHeaders(eq(200), anyLong());
    }

    @Test
    void test_correct_post_returns_200() throws IOException, InvalidKeyException, BadPaddingException, NoSuchAlgorithmException, IllegalBlockSizeException, NoSuchPaddingException {
        // SETUP
        JSONObject jo = new JSONObject();
        jo.put(Parameters.client_public_key.name(), MyCrypto.publicKeyToB64String(clientPublicKey));
        jo.put(Parameters.action.name(), Action.POST.name());
        jo.put(Parameters.message.name(), "Cobyd-briant -2019");
        jo.put(Parameters.announcements.name(), new JSONArray(List.of(3,2,1)));
        String sig = MyCrypto.digestAndSignToB64(jo.toString().getBytes(), clientPrivateKey);
        jo.put(Parameters.signature.name(), sig);
        setRequestBody(jo.toString());
        // EXERCISE
        server.handle(httpExchange);
        // ASSERT
        verify(httpExchange).sendResponseHeaders(eq(200), anyLong());
    }

    @Test
    void test_correct_post_general_returns_200() throws IOException, InvalidKeyException, BadPaddingException, NoSuchAlgorithmException, IllegalBlockSizeException, NoSuchPaddingException {
        // SETUP
        JSONObject jo = new JSONObject();
        jo.put(Parameters.client_public_key.name(), MyCrypto.publicKeyToB64String(clientPublicKey));
        jo.put(Parameters.action.name(), Action.POSTGENERAL.name());
        jo.put(Parameters.message.name(), "Hola soy pilo");
        jo.put(Parameters.announcements.name(), new JSONArray(List.of(3,2,1)));
        String sig = MyCrypto.digestAndSignToB64(jo.toString().getBytes(), clientPrivateKey);
        jo.put(Parameters.signature.name(), sig);
        setRequestBody(jo.toString());
        // EXERCISE
        server.handle(httpExchange);
        // ASSERT
        verify(httpExchange).sendResponseHeaders(eq(200), anyLong());
    }

    @Test
    void test_empty_body_returns_400() throws IOException {
        // SETUP
        setRequestBody("");
        // EXERCISE
        server.handle(httpExchange);
        // ASSERT
        verify(httpExchange).sendResponseHeaders(eq(400), anyLong());
    }

    @Test
    void test_random_data_returns_400() throws IOException {
        // SETUP
        setRequestBody(randString);
        // EXERCISE
        server.handle(httpExchange);
        // ASSERT
        verify(httpExchange).sendResponseHeaders(eq(400), anyLong());
    }

    @Test
    void test_non_server_private_key_encryption_returns_400() throws IOException {
        // SETUP
        setRequestBody(randString);
        // EXERCISE
        server.handle(httpExchange);
        // ASSERT
        verify(httpExchange).sendResponseHeaders(eq(400), anyLong());
    }

    @Test
    void test_altered_signature_returns_400() throws IOException, InvalidKeyException, BadPaddingException, NoSuchAlgorithmException, IllegalBlockSizeException, NoSuchPaddingException {
        // SETUP
        JSONObject jo = new JSONObject();
        jo.put(Parameters.client_public_key.name(), MyCrypto.publicKeyToB64String(clientPublicKey));
        jo.put(Parameters.action.name(), Action.POST.name());
        jo.put(Parameters.message.name(), "Cobyd-briant -2019");
        jo.put(Parameters.announcements.name(), new JSONArray(List.of(3,2,1)));
        String sig = MyCrypto.digestAndSignToB64(jo.toString().getBytes(), clientPrivateKey);
        jo.put(Parameters.signature.name(), "aa" + sig.substring(2));
        setRequestBody(jo.toString());
        // EXERCISE
        server.handle(httpExchange);
        // ASSERT
        verify(httpExchange).sendResponseHeaders(eq(400), anyLong());
    }

    @Test
    void test_altered_message_returns_400() throws IOException, InvalidKeyException, BadPaddingException, NoSuchAlgorithmException, IllegalBlockSizeException, NoSuchPaddingException {
        // SETUP
        JSONObject jo = new JSONObject();
        jo.put(Parameters.client_public_key.name(), MyCrypto.publicKeyToB64String(clientPublicKey));
        jo.put(Parameters.action.name(), Action.POST.name());
        jo.put(Parameters.message.name(), "Cobyd-briant -2019");
        jo.put(Parameters.announcements.name(), new JSONArray(List.of(3,2,1)));
        String sig = MyCrypto.digestAndSignToB64(jo.toString().getBytes(), clientPrivateKey);
        jo.put(Parameters.signature.name(), sig);
        // we alter the message
        jo.remove(Parameters.message.name());
        jo.put(Parameters.message.name(), "aCobyd-briant -2019");
        setRequestBody(jo.toString());
        // EXERCISE
        server.handle(httpExchange);
        // ASSERT
        verify(httpExchange).sendResponseHeaders(eq(400), anyLong());
    }

    @Test
    void test_illegal_action_returns_400() throws IOException, InvalidKeyException, BadPaddingException, NoSuchAlgorithmException, IllegalBlockSizeException, NoSuchPaddingException {
        // SETUP
        JSONObject jo = new JSONObject();
        jo.put(Parameters.client_public_key.name(), MyCrypto.publicKeyToB64String(clientPublicKey));
        jo.put(Parameters.action.name(), "Action");
        jo.put(Parameters.message.name(), "Cobyd-briant -2019");
        jo.put(Parameters.announcements.name(), new JSONArray(List.of(3,2,1)));
        String sig = MyCrypto.digestAndSignToB64(jo.toString().getBytes(), clientPrivateKey);
        jo.put(Parameters.signature.name(), sig);
        setRequestBody(jo.toString());
        // EXERCISE
        server.handle(httpExchange);
        // ASSERT
        verify(httpExchange).sendResponseHeaders(eq(400), anyLong());
    }

    @Test
    void test_big_body_returns_200() throws IOException, InvalidKeyException, BadPaddingException, NoSuchAlgorithmException, IllegalBlockSizeException, NoSuchPaddingException {
        // SETUP
        JSONObject jo = new JSONObject();
        jo.put(Parameters.client_public_key.name(), MyCrypto.publicKeyToB64String(clientPublicKey));
        jo.put(Parameters.action.name(), Action.POSTGENERAL.name());
        jo.put(Parameters.message.name(), randString);
        jo.put(Parameters.announcements.name(), new JSONArray(List.of(3,2,1,1,1,1,2,3,3,4,5,4,3,3,3,2,3,2,1,1,1,1,2,3,3,4,5,4,3,3,3,2,3,2,1,1,1,1,2,3,3,4,5,4,3,3,3,2,3,2,1,1,1,1,2,3,3,4,5,4,3,3,3,2,3,2,1,1,1,1,2,3,3,4,5,4,3,3,3,2,3,2,1,1,1,1,2,3,3,4,5,4,3,3,3,2)));
        String sig = MyCrypto.digestAndSignToB64(jo.toString().getBytes(), clientPrivateKey);
        jo.put(Parameters.signature.name(), sig);
        setRequestBody(jo.toString());
        // EXERCISE
        server.handle(httpExchange);
        // ASSERT
        verify(httpExchange).sendResponseHeaders(eq(200), anyLong());
    }*/
}