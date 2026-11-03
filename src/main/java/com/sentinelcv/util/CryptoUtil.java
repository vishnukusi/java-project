package com.sentinelcv.util;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.NoSuchAlgorithmException;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.SecureRandom;
import java.security.Signature;
import java.security.SignatureException;
import java.util.Base64;

/**
 * Cryptographic utility providing HMAC, Digital Signatures (SHA256withRSA),
 * and cryptographically secure random nonces.
 * Adheres to Java Cryptography Architecture (JCA) standards.
 */
public final class CryptoUtil {

    private static final String HMAC_SHA256 = "HmacSHA256";
    private static final String RSA_SIGN_ALGORITHM = "SHA256withRSA";
    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    private CryptoUtil() {}

    /**
     * Generates a cryptographically secure random hexadecimal nonce of the specified byte length.
     */
    public static String generateNonce(int byteLength) {
        byte[] bytes = new byte[byteLength];
        SECURE_RANDOM.nextBytes(bytes);
        return HashUtil.bytesToHex(bytes);
    }

    /**
     * Generates a default 16-byte (32 hex characters) secure nonce.
     */
    public static String generateNonce() {
        return generateNonce(16);
    }

    /**
     * Generates a cryptographically secure 256-bit (32-byte) key formatted as hex.
     */
    public static String generateSecureKey() {
        byte[] keyBytes = new byte[32];
        SECURE_RANDOM.nextBytes(keyBytes);
        return HashUtil.bytesToHex(keyBytes);
    }

    /**
     * Computes HMAC-SHA256 for a given payload using a secret key.
     */
    public static String computeHmac(String data, String key) {
        try {
            Mac mac = Mac.getInstance(HMAC_SHA256);
            SecretKeySpec secretKeySpec = new SecretKeySpec(key.getBytes(StandardCharsets.UTF_8), HMAC_SHA256);
            mac.init(secretKeySpec);
            byte[] hmacBytes = mac.doFinal(data.getBytes(StandardCharsets.UTF_8));
            return HashUtil.bytesToHex(hmacBytes);
        } catch (NoSuchAlgorithmException | InvalidKeyException e) {
            throw new IllegalStateException("Failed to calculate HMAC-SHA256", e);
        }
    }

    /**
     * Verifies whether the provided HMAC matches the recomputed HMAC using constant-time comparison.
     */
    public static boolean verifyHmac(String data, String key, String expectedHmac) {
        if (data == null || key == null || expectedHmac == null) {
            return false;
        }
        String computed = computeHmac(data, key);
        return MessageDigestEqual(computed.getBytes(StandardCharsets.UTF_8), expectedHmac.getBytes(StandardCharsets.UTF_8));
    }

    /**
     * Generates an RSA key pair for testing or local verification.
     */
    public static KeyPair generateRsaKeyPair(int keySize) {
        try {
            KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
            generator.initialize(keySize, SECURE_RANDOM);
            return generator.generateKeyPair();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("RSA algorithm unavailable", e);
        }
    }

    /**
     * Signs data using an RSA private key.
     */
    public static String sign(String data, PrivateKey privateKey) {
        try {
            Signature signature = Signature.getInstance(RSA_SIGN_ALGORITHM);
            signature.initSign(privateKey);
            signature.update(data.getBytes(StandardCharsets.UTF_8));
            byte[] signedBytes = signature.sign();
            return Base64.getEncoder().encodeToString(signedBytes);
        } catch (NoSuchAlgorithmException | InvalidKeyException | SignatureException e) {
            throw new IllegalStateException("Failed to generate digital signature", e);
        }
    }

    /**
     * Verifies an RSA digital signature using the corresponding public key.
     */
    public static boolean verifySignature(String data, String base64Signature, PublicKey publicKey) {
        try {
            Signature signature = Signature.getInstance(RSA_SIGN_ALGORITHM);
            signature.initVerify(publicKey);
            signature.update(data.getBytes(StandardCharsets.UTF_8));
            byte[] signatureBytes = Base64.getDecoder().decode(base64Signature);
            return signature.verify(signatureBytes);
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * Constant-time byte array comparison to prevent timing attacks.
     */
    private static boolean MessageDigestEqual(byte[] a, byte[] b) {
        if (a.length != b.length) {
            return false;
        }
        int result = 0;
        for (int i = 0; i < a.length; i++) {
            result |= a[i] ^ b[i];
        }
        return result == 0;
    }
}
