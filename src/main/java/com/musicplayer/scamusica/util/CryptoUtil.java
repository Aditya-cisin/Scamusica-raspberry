package com.musicplayer.scamusica.util;

import javax.crypto.Cipher;
import javax.crypto.CipherInputStream;
import javax.crypto.CipherOutputStream;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.io.InputStream;
import java.io.OutputStream;

public class CryptoUtil {

    private static final String ALGO = "AES/CBC/PKCS5Padding";
    private static final byte[] KEY = "1234567890123456".getBytes();
    // Fixed IV — same on encrypt & decrypt (16 bytes)
    private static final byte[] IV  = "abcdef1234567890".getBytes();

    public static CipherOutputStream encrypt(OutputStream os) throws Exception {
        SecretKeySpec keySpec = new SecretKeySpec(KEY, "AES");
        IvParameterSpec ivSpec = new IvParameterSpec(IV);
        Cipher cipher = Cipher.getInstance(ALGO);
        cipher.init(Cipher.ENCRYPT_MODE, keySpec, ivSpec);
        return new CipherOutputStream(os, cipher);
    }

    public static CipherInputStream decrypt(InputStream is) throws Exception {
        SecretKeySpec keySpec = new SecretKeySpec(KEY, "AES");
        IvParameterSpec ivSpec = new IvParameterSpec(IV);
        Cipher cipher = Cipher.getInstance(ALGO);
        cipher.init(Cipher.DECRYPT_MODE, keySpec, ivSpec);
        return new CipherInputStream(is, cipher);
    }

}
