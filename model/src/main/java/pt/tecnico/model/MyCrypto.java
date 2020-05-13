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
import java.util.UUID;

// https://www.baeldung.com/java-digital-signature

/**
 * Class to support the crypto operations used in the Dependable Public Announcement Server communication and messages handling
 */
public class MyCrypto {
    public static final int NONCE_LENGTH = UUID.randomUUID().toString().length();
    private static final String DIGEST_ALG = "SHA-512";
    private static final String KEY_ALG = "RSA";
    private static final int KEY_SIZE = 2048;
    private static final String KEY_STORE = "PKCS12";

    /**
     * @param path     String corresponding to the private key path
     * @param alias    String corresponding to the key alias
     * @param password String corresponding to the keystore password
     * @return the loaded PrivateKey
     * @throws KeyStoreException         in case no Provider supports a KeyStoreSpi implementation for the KEY_STORE type
     * @throws UnrecoverableKeyException in case the key cannot be recovered (e.g., the given password is wrong)
     * @throws NoSuchAlgorithmException  in case the specified KEY_ALG does not exist
     * @throws IOException               in case the specified path can not be accessed
     * @throws CertificateException      if case any of the certificates in the keystore could not be loaded
     */
    public static PrivateKey getPrivateKey(String path, String alias, String password) throws KeyStoreException, UnrecoverableKeyException, NoSuchAlgorithmException, IOException, CertificateException {
        KeyStore keyStore = KeyStore.getInstance(KEY_STORE);
        keyStore.load(new FileInputStream(path), password.toCharArray());
        return (PrivateKey) keyStore.getKey(alias, password.toCharArray());
    }

    /**
     * @param path     String corresponding to the public key path
     * @param alias    String corresponding to the key alias
     * @param password String corresponding to the keystore password
     * @return the loaded PublicKey
     * @throws KeyStoreException        in case no Provider supports a KeyStoreSpi implementation for the KEY_STORE type
     * @throws NoSuchAlgorithmException in case the specified KEY_ALG does not exist
     * @throws IOException              in case the specified path can not be accessed
     * @throws CertificateException     if case any of the certificates in the keystore could not be loaded
     */
    public static PublicKey getPublicKey(String path, String alias, String password) throws KeyStoreException, NoSuchAlgorithmException, IOException, CertificateException {
        Certificate certificate;
        KeyStore keyStore;
        keyStore = KeyStore.getInstance(KEY_STORE);
        keyStore.load(new FileInputStream(path), password.toCharArray());
        certificate = keyStore.getCertificate(alias);
        return certificate.getPublicKey();
    }

    /**
     * Compute the digest of a Byte array with DIGEST_ALG algorithm
     *
     * @param messageBytes Byte array to compute the digest
     * @return the corresponding digest Byte array
     * @throws NoSuchAlgorithmException in case the specified DIGEST_ALG does not exist
     */
    public static byte[] digest(byte[] messageBytes) throws NoSuchAlgorithmException {
        MessageDigest md = MessageDigest.getInstance(DIGEST_ALG);
        return md.digest(messageBytes);
    }

    /**
     * Decrypt a message using a provided private key
     *
     * @param msg        Byte array corresponding to the message to decrypt
     * @param privateKey PrivateKey used to decrypt the message
     * @return the decrypted message, as a Byte array
     * @throws NoSuchPaddingException    in case transformation contains a padding scheme that is not available
     * @throws NoSuchAlgorithmException  in case the specified KEY_ALG does not exist
     * @throws InvalidKeyException       in case the provided privateKey is invalid (invalid encoding, wrong length, uninitialized, etc)
     * @throws BadPaddingException       in case the msg is null
     * @throws IllegalBlockSizeException in case the length of msg provided to a block cipher is incorrect (e.g., does not match the block size of the cipher)
     */
    public static byte[] decrypt(byte[] msg, PrivateKey privateKey) throws NoSuchPaddingException, NoSuchAlgorithmException, InvalidKeyException, BadPaddingException, IllegalBlockSizeException {
        // decrypt the signature
        Cipher cipher = Cipher.getInstance(KEY_ALG);
        cipher.init(Cipher.DECRYPT_MODE, privateKey);
        return cipher.doFinal(msg);
    }

    /**
     * Sign a message using a provided private key
     *
     * @param data Byte array corresponding to the message to sign
     * @param priv PrivateKey used to sign the message
     * @return the signed message, as a Byte array
     * @throws NoSuchAlgorithmException  in case the specified KEY_ALG does not exist
     * @throws NoSuchPaddingException    in case transformation contains a padding scheme that is not available
     * @throws InvalidKeyException       in case the provided key is invalid (invalid encoding, wrong length, uninitialized, etc)
     * @throws BadPaddingException       in case the data is null
     * @throws IllegalBlockSizeException in case the length of data provided to a block cipher is incorrect (e.g., does not match the block size of the cipher)
     */
    public static byte[] sign(byte[] data, PrivateKey priv) throws NoSuchAlgorithmException, NoSuchPaddingException, InvalidKeyException, BadPaddingException, IllegalBlockSizeException {
        Cipher cipher = Cipher.getInstance(KEY_ALG);
        cipher.init(Cipher.ENCRYPT_MODE, priv);
        return cipher.doFinal(data);
    }

    /**
     * Compute the digest of a message and sign it using the provided private key
     *
     * @param messageBytes Byte array corresponding to the message to compute the digest and sign
     * @param priv         PrivateKey used to sign the message
     * @return the signed digest of the message, as a Byte array
     * @throws NoSuchAlgorithmException  in case the specified KEY_ALG does not exist
     * @throws IllegalBlockSizeException in case the length of messageBytes provided to a block cipher is incorrect (e.g., does not match the block size of the cipher)
     * @throws InvalidKeyException       in case the provided priv key is invalid (invalid encoding, wrong length, uninitialized, etc)
     * @throws BadPaddingException       in case the messageBytes is null
     * @throws NoSuchPaddingException    in case transformation contains a padding scheme that is not available
     */
    public static byte[] digestAndSign(byte[] messageBytes, PrivateKey priv) throws NoSuchAlgorithmException, IllegalBlockSizeException, InvalidKeyException, BadPaddingException, NoSuchPaddingException {
        return sign(digest(messageBytes), priv);
    }

    /**
     * Compute the digest of a message, sign it using the provided private key and encode it to a Base64 String
     *
     * @param msg  Byte array corresponding to the message to compute the digest, sign and Base64 encode
     * @param priv PrivateKey used to sign the message
     * @return the signed digest of the message, as a Base64 encoded String
     * @throws NoSuchAlgorithmException  in case the specified KEY_ALG does not exist
     * @throws IllegalBlockSizeException in case the length of messageBytes provided to a block cipher is incorrect (e.g., does not match the block size of the cipher)
     * @throws InvalidKeyException       in case the provided priv key is invalid (invalid encoding, wrong length, uninitialized, etc)
     * @throws BadPaddingException       in case the msg is null
     * @throws NoSuchPaddingException    in case transformation contains a padding scheme that is not available
     */
    public static String digestAndSignToB64(byte[] msg, PrivateKey priv) throws NoSuchAlgorithmException, IllegalBlockSizeException, InvalidKeyException, BadPaddingException, NoSuchPaddingException {
        return Base64.getEncoder().encodeToString(MyCrypto.digestAndSign(msg, priv));
    }

    /**
     * Checks if the signature of a message is correct
     *
     * @param encryptedMessageHash Byte array corresponding to the message to check
     * @param messageBytes         the unencrypted message to compare to
     * @param pub                  PublicKey used to decrypt the message
     * @return true if the signature of the encryptedMessageHash is correct
     * @throws NoSuchAlgorithmException  in case the specified KEY_ALG does not exist
     * @throws NoSuchPaddingException    in case transformation contains a padding scheme that is not available
     * @throws InvalidKeyException       in case the provided pub key is invalid (invalid encoding, wrong length, uninitialized, etc)
     * @throws BadPaddingException       in case the encryptedMessageHash is null
     * @throws IllegalBlockSizeException in case the length of messageBytes provided to a block cipher is incorrect (e.g., does not match the block size of the cipher)
     */
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


    /**
     * Generates a random nonce, based on the UUID generator
     *
     * @return a String corresponding to the generated nonce
     */
    public static String getRandomNonce() {
        return UUID.randomUUID().toString();
    }

    /**
     * Convert a PublicKey to its Base64 String representation (PEM format)
     *
     * @param key PublicKey to convert
     * @return a String corresponding to the Base64 key representation
     */
    public static String publicKeyToB64String(PublicKey key) {
        if (key == null)
            throw new IllegalArgumentException("key is null");
        return Base64.getEncoder().encodeToString(key.getEncoded());
    }

    /**
     * Generate a PublicKey from the given Base64 encoded public key
     *
     * @param key Base64 encoded public key
     * @return a PublicKey based on the provided key
     * @throws NoSuchAlgorithmException in case the specified KEY_ALG does not exist
     * @throws InvalidKeySpecException  in case the given key specification is inappropriate for this key factory to produce a public key
     */
    public static PublicKey publicKeyFromB64String(String key) throws NoSuchAlgorithmException, InvalidKeySpecException {
        if (key == null)
            return null;
        byte[] byteKey = Base64.getDecoder().decode(key);
        X509EncodedKeySpec X509publicKey = new X509EncodedKeySpec(byteKey);
        KeyFactory kf = KeyFactory.getInstance(MyCrypto.KEY_ALG);
        return kf.generatePublic(X509publicKey);
    }

    /**
     * Generate a new KeyPair
     *
     * @return a new KeyPair
     * @throws NoSuchAlgorithmException in case the specified KEY_ALG does not exist
     */
    public static KeyPair generateKeyPair() throws NoSuchAlgorithmException {
        KeyPairGenerator kpg = KeyPairGenerator.getInstance(KEY_ALG);
        kpg.initialize(KEY_SIZE);
        return kpg.generateKeyPair();
    }

    /**
     * Decode a Base64 string
     *
     * @param str Base64 encoded String
     * @return Byte array corresponding to the decoded str
     */
    public static byte[] decodeB64(String str) {
        return Base64.getDecoder().decode(str);
    }


}
