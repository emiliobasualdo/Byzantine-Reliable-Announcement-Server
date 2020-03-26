package pt.tecnico.model;

import javax.crypto.BadPaddingException;
import javax.crypto.Cipher;
import javax.crypto.IllegalBlockSizeException;
import javax.crypto.NoSuchPaddingException;
import java.io.FileInputStream;
import java.io.IOException;
import java.security.*;
import java.security.cert.Certificate;
import java.security.cert.CertificateException;
import java.security.spec.InvalidKeySpecException;
import java.security.spec.X509EncodedKeySpec;
import java.util.Arrays;
import java.util.Base64;

// https://www.baeldung.com/java-digital-signature
public class MyCrypto {
    private static final String DIGEST_ALG = "SHA-512";
    public static final String KEY_ALG = "RSA";
    public static final int KEY_SIZE = 2048;
    private static final String KEY_STORE = "PKCS12";
    private static final String PASSWORD = "pass1234";

    private static final String SERVER_KEYSTORE = "server_keystore.p12";
    public static final String SERVER_ALIAS = "serverKeyPair";

    private static final String CLIENT_KEYSTORE = "client1_keystore.p12";
    public static final String CLIENT_ALIAS = "client1KeyPair";


    public static PrivateKey getPrivateKey(String path, String alias) throws KeyStoreException, UnrecoverableKeyException, NoSuchAlgorithmException, IOException, CertificateException {
        KeyStore keyStore = KeyStore.getInstance(KEY_STORE);
        switch (alias) {
            case SERVER_ALIAS:
                keyStore.load(new FileInputStream(path+SERVER_KEYSTORE), PASSWORD.toCharArray());
                break;
            case CLIENT_ALIAS:
                keyStore.load(new FileInputStream(path+CLIENT_KEYSTORE), PASSWORD.toCharArray());
                break;
        }
        return (PrivateKey) keyStore.getKey(alias, PASSWORD.toCharArray());
    }

    public static PublicKey getPublicKey(String path, String alias) throws KeyStoreException, NoSuchAlgorithmException, IOException, CertificateException {
        switch (alias) {
            case CLIENT_ALIAS:
                KeyStore keyStore = KeyStore.getInstance(KEY_STORE);
                keyStore.load(new FileInputStream(path+CLIENT_KEYSTORE), PASSWORD.toCharArray());
                Certificate certificate = keyStore.getCertificate(CLIENT_ALIAS);
                return certificate.getPublicKey();
        }
        return null;
    }

    public static byte[] digest(byte[] messageBytes) throws NoSuchAlgorithmException {
        MessageDigest md = MessageDigest.getInstance(DIGEST_ALG);
        return md.digest(messageBytes);
    }

    public static byte[] decrypt(byte[] msg, PrivateKey privateKey) throws NoSuchPaddingException, NoSuchAlgorithmException, InvalidKeyException, BadPaddingException, IllegalBlockSizeException {
        // decrypt the signature
        Cipher cipher = Cipher.getInstance(KEY_ALG);
        cipher.init(Cipher.DECRYPT_MODE, privateKey);
        return cipher.doFinal(msg);
    }

    public static byte[] encrypt(byte[] messageHash, PrivateKey priv) throws NoSuchAlgorithmException, NoSuchPaddingException, InvalidKeyException, BadPaddingException, IllegalBlockSizeException {
        Cipher cipher = Cipher.getInstance(KEY_ALG);
        cipher.init(Cipher.ENCRYPT_MODE, priv);
        return cipher.doFinal(messageHash);
    }

    public static byte[] digestAndSign(byte[] messageBytes, PrivateKey priv) throws NoSuchAlgorithmException, IllegalBlockSizeException, InvalidKeyException, BadPaddingException, NoSuchPaddingException {
        return encrypt(digest(messageBytes), priv);
    }

    public static boolean verifySignature(byte[] encryptedMessageHash, byte[] messageBytes, PublicKey pub) throws NoSuchAlgorithmException, NoSuchPaddingException, InvalidKeyException, BadPaddingException, IllegalBlockSizeException {
        // decrypt the signature
        Cipher cipher = Cipher.getInstance(KEY_ALG);
        cipher.init(Cipher.DECRYPT_MODE, pub);
        byte[] decryptedMessageHash = cipher.doFinal(encryptedMessageHash);
        // hash the message
        MessageDigest md = MessageDigest.getInstance(DIGEST_ALG);
        byte[] newMessageHash = md.digest(messageBytes);
        // compare results
        return Arrays.equals(decryptedMessageHash, newMessageHash);
    }

    public static byte[] XOR(byte[] a, byte[] b) {
        byte[] resp = new byte[a.length];

        for (int i = 0; i < a.length; i++) {
            resp[i] = (byte)((a[i] ^ b[i]) & 0x000000ff);
        }
        return resp;
    }

    public static String publicKeyToB64String(PublicKey key){
        if (key  == null) return null;
        byte[] encodedPublicKey = key.getEncoded();
        return Base64.getEncoder().encodeToString(encodedPublicKey);
    }

    public static PublicKey publicKeyFromB64String(String key) throws NoSuchAlgorithmException, InvalidKeySpecException {
        if (key  == null) return null;
        byte[] byteKey = Base64.getDecoder().decode(key.getBytes());
        X509EncodedKeySpec X509publicKey = new X509EncodedKeySpec(byteKey);
        KeyFactory kf = KeyFactory.getInstance(MyCrypto.KEY_ALG);
        return kf.generatePublic(X509publicKey);
    }

    public static String signatureToB64String(byte[] sig){
        if (sig == null) return null;
        return Base64.getEncoder().encodeToString(sig);
    }

    public static byte[] byteArrFromB64String(String str){
        if (str == null) return null;
        return Base64.getDecoder().decode(str.getBytes());
    }

    public static KeyPair generateKeyPair() throws NoSuchAlgorithmException {
        KeyPairGenerator kpg = KeyPairGenerator.getInstance(KEY_ALG);
        kpg.initialize(KEY_SIZE);
        return kpg.generateKeyPair();
    }
}
