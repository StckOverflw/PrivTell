package me.tbhmens.privtell;

import com.google.common.primitives.Bytes;

import javax.crypto.BadPaddingException;
import javax.crypto.Cipher;
import javax.crypto.IllegalBlockSizeException;
import javax.crypto.NoSuchPaddingException;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.security.*;
import java.security.spec.EncodedKeySpec;
import java.security.spec.InvalidKeySpecException;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;
import java.util.List;

import static java.nio.charset.StandardCharsets.UTF_8;

public class Core {
    public final String pubb64;
    private final PublicKey pub;
    private final PrivateKey priv;

    public Core() throws NoSuchAlgorithmException {
        final KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
        generator.initialize(512);
        final KeyPair pair = generator.generateKeyPair();

        this.priv = pair.getPrivate();
        this.pub = pair.getPublic();

        final Base64.Encoder encoder = Base64.getEncoder();
        final byte[] pubb64 = encoder.encode(this.pub.getEncoded());
        this.pubb64 = new String(pubb64, UTF_8);
    }

    public Core(File file) throws NoSuchAlgorithmException, IOException, InvalidKeySpecException {
        final List<String> lines = Files.readAllLines(file.toPath());
        final Base64.Decoder decoder = Base64.getDecoder();
        final byte[] publicKeyBytes = decoder.decode(lines.get(0));
        final byte[] privKeyBytes = decoder.decode(lines.get(1));
        final KeyFactory keyFactory = KeyFactory.getInstance("RSA");
        final EncodedKeySpec publicKeySpec = new X509EncodedKeySpec(publicKeyBytes);
        final EncodedKeySpec privKeySpec = new PKCS8EncodedKeySpec(privKeyBytes);

        this.pub = keyFactory.generatePublic(publicKeySpec);
        this.priv = keyFactory.generatePrivate(privKeySpec);

        this.pubb64 = lines.get(0);
    }

    public void save(File file) {
        try {
            final Base64.Encoder encoder = Base64.getEncoder();
            final byte[] pub = encoder.encode(this.pub.getEncoded());
            final byte[] SPLITTER = {'\n'};
            final byte[] priv = encoder.encode(this.priv.getEncoded());
            Files.write(file.toPath(), Bytes.concat(pub, SPLITTER, priv));
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public String decrypt(String message) throws NoSuchPaddingException, NoSuchAlgorithmException, InvalidKeyException, IllegalBlockSizeException, BadPaddingException {
        final Cipher decryptCipher = Cipher.getInstance("RSA");
        decryptCipher.init(Cipher.DECRYPT_MODE, this.priv);
        final Base64.Decoder decoder = Base64.getDecoder();
        final byte[] encrypted = decryptCipher.doFinal(decoder.decode(message));
        return new String(encrypted, UTF_8);
    }

    public static class OtherPlayer {
        public PublicKey pub;

        public OtherPlayer(String keyb64) throws NoSuchAlgorithmException, InvalidKeySpecException {
            final Base64.Decoder decoder = Base64.getDecoder();
            final byte[] key = decoder.decode(keyb64);
            final KeyFactory keyFactory = KeyFactory.getInstance("RSA");
            final EncodedKeySpec publicKeySpec = new X509EncodedKeySpec(key);
            this.pub = keyFactory.generatePublic(publicKeySpec);
        }

        public String encrypt(String message) throws NoSuchPaddingException, NoSuchAlgorithmException, InvalidKeyException, IllegalBlockSizeException, BadPaddingException {
            final Cipher encryptCipher = Cipher.getInstance("RSA");
            encryptCipher.init(Cipher.ENCRYPT_MODE, this.pub);
            final byte[] encrypted = encryptCipher.doFinal(message.getBytes());
            final Base64.Encoder encoder = Base64.getEncoder();
            return new String(encoder.encode(encrypted), UTF_8);
        }
    }
}
