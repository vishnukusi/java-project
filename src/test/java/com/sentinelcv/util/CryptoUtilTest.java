package com.sentinelcv.util;

import org.junit.jupiter.api.Test;

import java.security.KeyPair;

import static org.junit.jupiter.api.Assertions.*;

class CryptoUtilTest {

    @Test
    void testHmacComputeAndVerify() {
        String data = "record-payload-12345";
        String key = "secret-hmac-key";

        String hmac = CryptoUtil.computeHmac(data, key);
        assertNotNull(hmac);
        assertEquals(64, hmac.length());

        assertTrue(CryptoUtil.verifyHmac(data, key, hmac));
        assertFalse(CryptoUtil.verifyHmac(data, "wrong-key", hmac));
        assertFalse(CryptoUtil.verifyHmac("tampered-data", key, hmac));
    }

    @Test
    void testRsaSigningAndVerification() {
        KeyPair keyPair = CryptoUtil.generateRsaKeyPair(2048);
        assertNotNull(keyPair);
        assertNotNull(keyPair.getPublic());
        assertNotNull(keyPair.getPrivate());

        String message = "InferenceRecord-HashChain-Verification";
        String signature = CryptoUtil.sign(message, keyPair.getPrivate());
        assertNotNull(signature);

        assertTrue(CryptoUtil.verifySignature(message, signature, keyPair.getPublic()));
        assertFalse(CryptoUtil.verifySignature("Tampered message", signature, keyPair.getPublic()));
    }

    @Test
    void testNonceGeneration() {
        String nonce1 = CryptoUtil.generateNonce();
        String nonce2 = CryptoUtil.generateNonce();

        assertNotNull(nonce1);
        assertNotNull(nonce2);
        assertEquals(32, nonce1.length()); // 16 bytes = 32 hex chars
        assertNotEquals(nonce1, nonce2); // Secure random uniqueness
    }
}
