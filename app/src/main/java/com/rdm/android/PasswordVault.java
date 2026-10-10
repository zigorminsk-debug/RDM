package com.rdm.android;

import android.security.keystore.KeyGenParameterSpec;
import android.security.keystore.KeyProperties;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.KeyStore;
import java.util.Arrays;

import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;

/** Encrypts saved credentials with an AES key that never leaves Android Keystore. */
final class PasswordVault {
    private static final String KEY_ALIAS = "com.rdm.android.password.aes-gcm.v1";
    private static final String ANDROID_KEYSTORE = "AndroidKeyStore";
    private static final String TRANSFORMATION = "AES/GCM/NoPadding";
    private static final int TAG_BITS = 128;
    private static final int IV_LENGTH_BYTES = 12;

    private PasswordVault() { }

    static byte[] encrypt(String password) throws GeneralSecurityException, IOException {
        Cipher cipher = Cipher.getInstance(TRANSFORMATION);
        cipher.init(Cipher.ENCRYPT_MODE, getOrCreateKey());
        byte[] iv = cipher.getIV();
        byte[] ciphertext = cipher.doFinal(password.getBytes(StandardCharsets.UTF_8));
        ByteBuffer packed = ByteBuffer.allocate(Integer.BYTES + iv.length + ciphertext.length);
        packed.putInt(iv.length);
        packed.put(iv);
        packed.put(ciphertext);
        return packed.array();
    }

    static String decrypt(byte[] packed) throws GeneralSecurityException, IOException {
        if (packed == null || packed.length < Integer.BYTES + IV_LENGTH_BYTES + 1) {
            throw new GeneralSecurityException("Saved password data is incomplete");
        }

        ByteBuffer input = ByteBuffer.wrap(packed);
        int ivLength = input.getInt();
        if (ivLength != IV_LENGTH_BYTES || input.remaining() <= ivLength) {
            throw new GeneralSecurityException("Saved password data has an invalid format");
        }

        byte[] iv = new byte[ivLength];
        input.get(iv);
        byte[] ciphertext = new byte[input.remaining()];
        input.get(ciphertext);

        Cipher cipher = Cipher.getInstance(TRANSFORMATION);
        cipher.init(Cipher.DECRYPT_MODE, getOrCreateKey(), new GCMParameterSpec(TAG_BITS, iv));
        byte[] cleartext = cipher.doFinal(ciphertext);
        try {
            return new String(cleartext, StandardCharsets.UTF_8);
        } finally {
            Arrays.fill(cleartext, (byte) 0);
        }
    }

    private static SecretKey getOrCreateKey() throws GeneralSecurityException, IOException {
        KeyStore keyStore = KeyStore.getInstance(ANDROID_KEYSTORE);
        keyStore.load(null);
        if (keyStore.containsAlias(KEY_ALIAS)) {
            return (SecretKey) keyStore.getKey(KEY_ALIAS, null);
        }

        KeyGenerator generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES,
                ANDROID_KEYSTORE);
        KeyGenParameterSpec spec = new KeyGenParameterSpec.Builder(KEY_ALIAS,
                KeyProperties.PURPOSE_ENCRYPT | KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setRandomizedEncryptionRequired(true)
                .setUserAuthenticationRequired(false)
                .build();
        generator.init(spec);
        return generator.generateKey();
    }
}
