package com.curius.iocraft.security;

import com.curius.iocraft.mensajeria.Mensaje;
import com.google.gson.JsonObject;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AuthManagerTest {

    @TempDir
    Path tempDir;

    @BeforeEach
    void setUp() {
        AuthManager.initPersistence(tempDir.resolve("iocraft_auth_test.json"));
    }

    @AfterEach
    void tearDown() {
        AuthManager.clearSecrets();
    }

    @Test
    void processHelloAcceptsValidHmacAndNonce() throws Exception {
        String device = "sensor-1";
        String secret = AuthManager.createOrRotateSecret(device, Set.of("sensor"), true);
        UUID connId = UUID.randomUUID();
        long ts = System.currentTimeMillis();
        String nonce = "n-" + ts;
        String sig = hmac(secret, device + ":" + ts + ":" + nonce);

        JsonObject hello = new JsonObject();
        hello.addProperty("device", device);
        hello.addProperty("ts", ts);
        hello.addProperty("nonce", nonce);
        hello.addProperty("sig", sig);

        JsonObject result = AuthManager.processHello(Mensaje.crearTipado("hello", hello, null, connId));
        assertTrue(result.get("ok").getAsBoolean());
        assertEquals(device, result.get("device").getAsString());
        assertTrue(AuthManager.isAuthenticated(connId));
    }

    @Test
    void processHelloRejectsBadSignature() throws Exception {
        String device = "sensor-2";
        AuthManager.createOrRotateSecret(device, Set.of("sensor"), true);
        UUID connId = UUID.randomUUID();
        long ts = System.currentTimeMillis();

        JsonObject hello = new JsonObject();
        hello.addProperty("device", device);
        hello.addProperty("ts", ts);
        hello.addProperty("nonce", "nonce-a");
        hello.addProperty("sig", "bad-signature");

        JsonObject result = AuthManager.processHello(Mensaje.crearTipado("hello", hello, null, connId));
        assertFalse(result.get("ok").getAsBoolean());
        assertEquals("bad_sig", result.get("code").getAsString());
        assertFalse(AuthManager.isAuthenticated(connId));
    }

    @Test
    void processHelloRejectsReplayNonce() throws Exception {
        String device = "sensor-3";
        String secret = AuthManager.createOrRotateSecret(device, Set.of("sensor"), true);
        UUID firstConn = UUID.randomUUID();
        UUID secondConn = UUID.randomUUID();
        long ts = System.currentTimeMillis();
        String nonce = "same-nonce";
        String sig = hmac(secret, device + ":" + ts + ":" + nonce);

        JsonObject firstHello = new JsonObject();
        firstHello.addProperty("device", device);
        firstHello.addProperty("ts", ts);
        firstHello.addProperty("nonce", nonce);
        firstHello.addProperty("sig", sig);
        JsonObject first = AuthManager.processHello(Mensaje.crearTipado("hello", firstHello, null, firstConn));
        assertTrue(first.get("ok").getAsBoolean());

        JsonObject secondHello = new JsonObject();
        secondHello.addProperty("device", device);
        secondHello.addProperty("ts", ts);
        secondHello.addProperty("nonce", nonce);
        secondHello.addProperty("sig", sig);
        JsonObject second = AuthManager.processHello(Mensaje.crearTipado("hello", secondHello, null, secondConn));

        assertFalse(second.get("ok").getAsBoolean());
        assertEquals("replay_nonce", second.get("code").getAsString());
    }

    @Test
    void revokeSecretRemovesDeviceAndSession() {
        String device = "sensor-4";
        String secret = AuthManager.createOrRotateSecret(device, Set.of("sensor"), true);
        assertNotNull(secret);
        UUID connId = UUID.randomUUID();
        long ts = System.currentTimeMillis();
        String nonce = "n-" + ts;
        String sig = hmacUnchecked(secret, device + ":" + ts + ":" + nonce);

        JsonObject hello = new JsonObject();
        hello.addProperty("device", device);
        hello.addProperty("ts", ts);
        hello.addProperty("nonce", nonce);
        hello.addProperty("sig", sig);
        JsonObject ok = AuthManager.processHello(Mensaje.crearTipado("hello", hello, null, connId));
        assertTrue(ok.get("ok").getAsBoolean());
        assertTrue(AuthManager.hasDevice(device));

        AuthManager.revokeSecret(device);

        assertFalse(AuthManager.hasDevice(device));
        assertFalse(AuthManager.isAuthenticated(connId));
    }

    private static String hmac(String secret, String data) throws Exception {
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
        byte[] out = mac.doFinal(data.getBytes(StandardCharsets.UTF_8));
        return toHex(out);
    }

    private static String hmacUnchecked(String secret, String data) {
        try {
            return hmac(secret, data);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    private static String toHex(byte[] bytes) {
        char[] hex = "0123456789abcdef".toCharArray();
        char[] out = new char[bytes.length * 2];
        for (int i = 0, j = 0; i < bytes.length; i++) {
            int v = bytes[i] & 0xFF;
            out[j++] = hex[v >>> 4];
            out[j++] = hex[v & 0x0F];
        }
        return new String(out);
    }
}

