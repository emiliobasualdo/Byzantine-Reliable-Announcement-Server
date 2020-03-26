package pt.tecnico.server;

import com.sun.net.httpserver.HttpExchange;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import pt.tecnico.model.MyCrypto;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.security.KeyPair;
import java.security.NoSuchAlgorithmException;
import java.security.PrivateKey;
import java.security.PublicKey;

import static org.mockito.Mockito.*;

public class ServerHttpTest {

    private static PrivateKey privateKey;
    private static PublicKey publicKey;

    private static ServerHttp server;
    private static HttpExchange httpExchange;

    private static final String randString = "NLFAG6qzERoCiN0jYRdRswckfkcgSZClNamchh7nsMo3Zl6dhKIJyF39Sg2iLyVovJcwTouelyEw0AXyVXjyGZ5ygcqi9n4ZqI0KM7x81Wlqf3PAz2K3ODnUNZnfqMWBQSHuABdMx4yFl3jKSjQ3IECuNadPsof6Irj3Vrd7ePWM2uJLvig8r71MjYhl7geI6RDqO7qXm7JyIOGzRpq9d5yt6JrtVMXcJ0wnIQdUEB8dQQbW3afVufR8STTTaBg4";

    @BeforeAll
    static void beforeAll() throws NoSuchAlgorithmException {
        KeyPair kp = MyCrypto.generateKeyPair();
        privateKey = kp.getPrivate();
        publicKey = kp.getPublic();
    };

    @BeforeEach
    void beforeEach() {
        server = new ServerHttp(mock(Twitter.class), privateKey);
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
    void test_empty_body_returns_400() throws IOException {
        // SETUP
        setRequestBody("");
        // EXERCISE
        server.handle(httpExchange);
        // ASSERT
        verify(httpExchange).sendResponseHeaders(eq(400), anyLong());
    }

    @Test
    void test_non_encrypted_data_returns_400() throws IOException {
        // SETUP
        setRequestBody(randString);
        // EXERCISE
        server.handle(httpExchange);
        // ASSERT
        verify(httpExchange).sendResponseHeaders(eq(400), anyLong());
    }
}