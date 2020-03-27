package pt.tecnico.server;

import com.sun.net.httpserver.HttpExchange;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import pt.tecnico.model.Action;
import pt.tecnico.model.MyCrypto;

import javax.crypto.BadPaddingException;
import javax.crypto.IllegalBlockSizeException;
import javax.crypto.NoSuchPaddingException;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.lang.reflect.Array;
import java.security.*;
import java.util.Arrays;
import java.util.Base64;

import static org.mockito.Mockito.*;

public class ServerHttpTest {

    private static PrivateKey serverPrivateKey;
    private static PublicKey serverPublicKey;

    private static PrivateKey clientPrivateKey;
    private static PublicKey clientPublicKey;

    private static ServerHttp server;
    private static HttpExchange httpExchange;

    private static final String randString = "NLFAG6qzEafasdfasfdasdfRoCiN0\nRdRswckfkcgSZClNamchh7nsMo3Zl6dhKIJyF39Sg2iLyVovJcwTouelyEw0AXyVXjyGZ5ygcqi9n4ZqI0KM7x81Wlqf3PAz2K3ODnU\nnfqMWBQSHuABdMx4yFl3jKSjQ3IECuNadPsof6Irj3Vrd7ePWM2uJLvig8r71MjYhl7geI6RDqO7qXm7JyIOGzRpq9d5yt6JrtVMXcJ0wnIQdUEB8dQQbW3afVufR8STTTaBg4";

    @BeforeAll
    static void beforeAll() throws NoSuchAlgorithmException {
        KeyPair kp = MyCrypto.generateKeyPair();
        serverPrivateKey = kp.getPrivate();
        serverPublicKey = kp.getPublic();
        kp = MyCrypto.generateKeyPair();
        clientPrivateKey = kp.getPrivate();
        clientPublicKey = kp.getPublic();
    };

    @BeforeEach
    void beforeEach() {
        server = new ServerHttp(mock(Twitter.class), serverPrivateKey);
        httpExchange = mock(HttpExchange.class);
        when(httpExchange.getRequestMethod()).thenReturn("POST");
        OutputStream out = mock(OutputStream.class);
        when(httpExchange.getResponseBody()).thenReturn(out);
    };

    private void setRequestBody(String requestBody) {
        InputStream is = new ByteArrayInputStream(requestBody.getBytes());
        when(httpExchange.getRequestBody()).thenReturn(is);
    }

    @Test
    void test_correct_example_returns_200() throws IOException, InvalidKeyException, BadPaddingException, NoSuchAlgorithmException, IllegalBlockSizeException, NoSuchPaddingException {
        // SETUP
        StringBuilder sb = new StringBuilder();
        sb.append(MyCrypto.publicKeyToB64String(clientPublicKey)).append("\n");
        sb.append(Action.READ.name()).append("\n");
        System.out.println(sb.toString());
        String sig = MyCrypto.digestAndSignToB64(sb.toString().getBytes(), clientPrivateKey);
        String body = sig + "\n" + sb.toString();
        setRequestBody(body);
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
}