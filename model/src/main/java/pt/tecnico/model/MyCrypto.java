package pt.tecnico.model;

import javax.crypto.*;
import java.io.FileInputStream;
import java.io.IOException;
import java.security.*;
import java.security.cert.Certificate;
import java.security.cert.CertificateException;
import java.security.spec.InvalidKeySpecException;
import java.security.spec.X509EncodedKeySpec;
import java.util.*;

// https://www.baeldung.com/java-digital-signature
public class MyCrypto {
    private static final String DIGEST_ALG = "SHA-512";
    private static final String KEY_ALG = "RSA";
    private static final int KEY_SIZE = 2048;
    private static final String KEY_STORE = "PKCS12";
    public static final int NONCE_LENGTH = UUID.randomUUID().toString().length();

    public static PrivateKey getPrivateKey(String path, String alias, String password) throws KeyStoreException, UnrecoverableKeyException, NoSuchAlgorithmException, IOException, CertificateException {
        KeyStore keyStore = KeyStore.getInstance(KEY_STORE);
        keyStore.load(new FileInputStream(path), password.toCharArray());
        return (PrivateKey) keyStore.getKey(alias, password.toCharArray());
    }

    public static PublicKey getPublicKey(String path, String alias, String password) throws KeyStoreException, NoSuchAlgorithmException, IOException, CertificateException {
        Certificate certificate;
        KeyStore keyStore;
        keyStore = KeyStore.getInstance(KEY_STORE);
        keyStore.load(new FileInputStream(path), password.toCharArray());
        certificate = keyStore.getCertificate(alias);
        return certificate.getPublicKey();
    }

    public static byte[] digest(byte[] messageBytes) throws NoSuchAlgorithmException {
        //Mac.getInstance("HmacSHA512") // todo check if MessageDigest or MAC or HMAC should be used
        MessageDigest md = MessageDigest.getInstance(DIGEST_ALG);
        return md.digest(messageBytes);
    }

    public static byte[] decrypt(byte[] msg, PrivateKey privateKey) throws NoSuchPaddingException, NoSuchAlgorithmException, InvalidKeyException, BadPaddingException, IllegalBlockSizeException {
        // decrypt the signature
        Cipher cipher = Cipher.getInstance(KEY_ALG);
        cipher.init(Cipher.DECRYPT_MODE, privateKey);
        return cipher.doFinal(msg);
    }

    public static byte[] sign(byte[] data, PrivateKey priv) throws NoSuchAlgorithmException, NoSuchPaddingException, InvalidKeyException, BadPaddingException, IllegalBlockSizeException {
        Cipher cipher = Cipher.getInstance(KEY_ALG);
        cipher.init(Cipher.ENCRYPT_MODE, priv);
        return cipher.doFinal(data);
    }

    public static byte[] digestAndSign(byte[] messageBytes, PrivateKey priv) throws NoSuchAlgorithmException, IllegalBlockSizeException, InvalidKeyException, BadPaddingException, NoSuchPaddingException {
        return sign(digest(messageBytes), priv);
    }

    public static String digestAndSignToB64(byte[] msg, PrivateKey priv) throws NoSuchAlgorithmException, IllegalBlockSizeException, InvalidKeyException, BadPaddingException, NoSuchPaddingException {
        return Base64.getEncoder().encodeToString(MyCrypto.digestAndSign(msg, priv));
    }

    public static boolean verifySignature(byte[] encryptedMessageHash, byte[] messageBytes, PublicKey pub) throws NoSuchAlgorithmException, NoSuchPaddingException, InvalidKeyException, BadPaddingException, IllegalBlockSizeException {
        // decrypt the signature
        Cipher cipher = Cipher.getInstance(KEY_ALG);
        cipher.init(Cipher.DECRYPT_MODE, pub);
        byte[] decryptedMessageHash = cipher.doFinal(encryptedMessageHash);
        // hash the message
        byte[] newMessageHash = digest(messageBytes);
        // compare results
        return Arrays.equals(decryptedMessageHash, newMessageHash);
    }

    public static String getRandomNonce() {
        return UUID.randomUUID().toString();
    }

    public static byte[] XOR(byte[] a, byte[] b) {
        byte[] resp = new byte[a.length];

        for (int i = 0; i < a.length; i++) {
            resp[i] = (byte)((a[i] ^ b[i]) & 0x000000ff);
        }
        return resp;
    }

    public static String publicKeyToB64String(PublicKey key){
        if (key  == null) throw new IllegalArgumentException("key is null");
        return Base64.getEncoder().encodeToString(key.getEncoded());
    }

    public static PublicKey publicKeyFromB64String(String key) throws NoSuchAlgorithmException, InvalidKeySpecException {
        if (key  == null) return null;
        byte[] byteKey = Base64.getDecoder().decode(key);
        X509EncodedKeySpec X509publicKey = new X509EncodedKeySpec(byteKey);
        KeyFactory kf = KeyFactory.getInstance(MyCrypto.KEY_ALG);
        return kf.generatePublic(X509publicKey);
    }

    public static KeyPair generateKeyPair() throws NoSuchAlgorithmException {
        KeyPairGenerator kpg = KeyPairGenerator.getInstance(KEY_ALG);
        kpg.initialize(KEY_SIZE);
        return kpg.generateKeyPair();
    }

    public static byte[] decodeB64(String str) {
        return Base64.getDecoder().decode(str);
    }
}
