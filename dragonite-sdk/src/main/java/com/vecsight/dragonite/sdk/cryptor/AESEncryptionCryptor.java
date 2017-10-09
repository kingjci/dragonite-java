/*
 * VECTORSIGHT CONFIDENTIAL
 * ------------------------
 * Copyright (c) [2015] - [2017]
 * VectorSight Systems Co., Ltd.
 * All Rights Reserved.
 *
 * Unauthorized copying of this file, via any medium is strictly prohibited
 * Proprietary and confidential
 *
 * Written by Toby Huang <t@vecsight.com>, June 2017
 */

package com.vecsight.dragonite.sdk.cryptor;

import com.vecsight.dragonite.sdk.exception.EncryptionException;

import javax.crypto.Cipher;
import javax.crypto.NoSuchPaddingException;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.PBEKeySpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.BufferUnderflowException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.InvalidAlgorithmParameterException;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.security.spec.InvalidKeySpecException;
import java.security.spec.KeySpec;

public class AESEncryptionCryptor implements PacketCryptor {

    private static final int IV_LENGTH = 16;

    private static final String ENCRYPTION_ALGORITHM = "AES";

    private static final String ENCRYPTION_ALGORITHM_WITH_MODE = "AES/CFB8/NoPadding";

    private static final String PASSWORD_HASH_ALGORITHM = "PBKDF2WithHmacSHA1";

    private static final byte[] PASSWORD_HASH_SALT = "*1w@UTcZLS@6fS713x80".getBytes(StandardCharsets.UTF_8);

    private static final int PASSWORD_HASH_ITERATION_COUNT = 12450;

    private static final int PASSWORD_HASH_LENGTH_BITS = 128;

    private final SecretKeySpec keySpec;

    private final Cipher decryptionCipher;

    private final Cipher encryptionCipher;

    private final SecureRandom random = new SecureRandom();

    public AESEncryptionCryptor(final String password) throws EncryptionException {
        this(getKey(password));
    }

    public AESEncryptionCryptor(final byte[] encryptionKey) throws EncryptionException {
        this.keySpec = new SecretKeySpec(encryptionKey, ENCRYPTION_ALGORITHM);
        try {
            decryptionCipher = Cipher.getInstance(ENCRYPTION_ALGORITHM_WITH_MODE);
            encryptionCipher = Cipher.getInstance(ENCRYPTION_ALGORITHM_WITH_MODE);
        } catch (final NoSuchAlgorithmException | NoSuchPaddingException e) {
            throw new EncryptionException(e.getMessage());
        }
    }

    private byte[] decryptImpl(final byte[] bytes) {
        final ByteBuffer buffer = ByteBuffer.wrap(bytes);
        try {

            final byte[] iv = new byte[IV_LENGTH];
            buffer.get(iv);

            final byte[] content = new byte[buffer.remaining()];
            buffer.get(content);

            final IvParameterSpec ivParameterSpec = new IvParameterSpec(iv);

            synchronized (decryptionCipher) {
                decryptionCipher.init(Cipher.DECRYPT_MODE, keySpec, ivParameterSpec);
                return decryptionCipher.update(content);
            }

        } catch (final BufferUnderflowException | InvalidAlgorithmParameterException | InvalidKeyException e) {
            return null;
        }
    }

    private byte[] encryptImpl(final byte[] bytes) {
        final ByteBuffer buffer = ByteBuffer.allocate(IV_LENGTH + bytes.length);
        try {

            final byte[] iv = new byte[IV_LENGTH];
            random.nextBytes(iv);
            buffer.put(iv);

            final IvParameterSpec ivParameterSpec = new IvParameterSpec(iv);

            synchronized (encryptionCipher) {
                encryptionCipher.init(Cipher.ENCRYPT_MODE, keySpec, ivParameterSpec);
                buffer.put(encryptionCipher.update(bytes));
            }

            return buffer.array();

        } catch (final BufferUnderflowException | InvalidAlgorithmParameterException | InvalidKeyException e) {
            return null;
        }
    }

    @Override
    public byte[] encrypt(final byte[] rawData) {
        return encryptImpl(rawData);
    }

    @Override
    public byte[] decrypt(final byte[] encryptedData) {
        return decryptImpl(encryptedData);
    }

    @Override
    public int getReceiveBufferOverhead() {
        return IV_LENGTH;
    }

    private static SecretKeyFactory getFactory() throws NoSuchAlgorithmException {
        return SecretKeyFactory.getInstance(PASSWORD_HASH_ALGORITHM);
    }

    private static byte[] getKey(final String password) throws EncryptionException {
        final KeySpec keySpec = new PBEKeySpec(password.toCharArray(), PASSWORD_HASH_SALT,
                PASSWORD_HASH_ITERATION_COUNT, PASSWORD_HASH_LENGTH_BITS);
        try {
            return getFactory().generateSecret(keySpec).getEncoded();
        } catch (final NoSuchAlgorithmException | InvalidKeySpecException e) {
            throw new EncryptionException(e.getMessage());
        }
    }
}