package com.smartstock.backend;

import org.springframework.test.context.DynamicPropertyRegistry;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.GeneralSecurityException;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.util.Base64;

public final class TestRsaKeys {

    private static final Path DIRECTORY;
    private static final Path PUBLIC_KEY;
    private static final Path PRIVATE_KEY;

    static {
        try {
            KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
            generator.initialize(2048);
            KeyPair pair = generator.generateKeyPair();

            DIRECTORY = Files.createTempDirectory("smartstock-test-keys-");
            PUBLIC_KEY = DIRECTORY.resolve("app.pub");
            PRIVATE_KEY = DIRECTORY.resolve("app.key");
            Files.writeString(PUBLIC_KEY, pem("PUBLIC KEY", pair.getPublic().getEncoded()));
            Files.writeString(PRIVATE_KEY, pem("PRIVATE KEY", pair.getPrivate().getEncoded()));
            PRIVATE_KEY.toFile().deleteOnExit();
            PUBLIC_KEY.toFile().deleteOnExit();
            DIRECTORY.toFile().deleteOnExit();
        } catch (GeneralSecurityException | IOException e) {
            throw new ExceptionInInitializerError(e);
        }
    }

    private TestRsaKeys() {
    }

    public static void register(DynamicPropertyRegistry registry) {
        registry.add("rsa.public-key", () -> PUBLIC_KEY.toUri().toString());
        registry.add("rsa.private-key", () -> PRIVATE_KEY.toUri().toString());
        registry.add("google.oauth.client-id", () -> "smartstock-tests.apps.googleusercontent.com");
        registry.add("admin.default.password", () -> "senhaFalsaDeTeste123");
    }

    private static String pem(String type, byte[] encoded) {
        String body = Base64.getMimeEncoder(64, new byte[]{'\n'}).encodeToString(encoded);
        return "-----BEGIN " + type + "-----\n" + body + "\n-----END " + type + "-----\n";
    }
}
