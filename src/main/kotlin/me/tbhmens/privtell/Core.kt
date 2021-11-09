package me.tbhmens.privtell

import java.nio.file.Files
import java.security.*
import java.security.spec.EncodedKeySpec
import java.security.spec.PKCS8EncodedKeySpec
import java.security.spec.X509EncodedKeySpec
import javax.crypto.BadPaddingException
import javax.crypto.Cipher
import javax.crypto.IllegalBlockSizeException
import javax.crypto.NoSuchPaddingException

class Core {
    val pubb64: String
    private val pub: PublicKey
    private val priv: PrivateKey

    constructor() {
        val generator: KeyPairGenerator = KeyPairGenerator.getInstance("RSA")
        generator.initialize(512)
        val pair: KeyPair = generator.generateKeyPair()
        priv = pair.private
        pub = pair.public
        val encoder: java.util.Base64.Encoder = java.util.Base64.getEncoder()
        val pubb64: ByteArray = encoder.encode(pub.encoded)
        this.pubb64 = String(pubb64, java.nio.charset.StandardCharsets.UTF_8)
    }

    constructor(file: java.io.File) {
        val lines: List<String> = Files.readAllLines(file.toPath())
        val decoder: java.util.Base64.Decoder = java.util.Base64.getDecoder()
        val publicKeyBytes: ByteArray = decoder.decode(lines[0])
        val privKeyBytes: ByteArray = decoder.decode(lines[1])
        val keyFactory: KeyFactory = KeyFactory.getInstance("RSA")
        val publicKeySpec: EncodedKeySpec = X509EncodedKeySpec(publicKeyBytes)
        val privKeySpec: EncodedKeySpec = PKCS8EncodedKeySpec(privKeyBytes)
        pub = keyFactory.generatePublic(publicKeySpec)
        priv = keyFactory.generatePrivate(privKeySpec)
        pubb64 = lines[0]
    }

    fun save(file: java.io.File) {
        try {
            val encoder: java.util.Base64.Encoder = java.util.Base64.getEncoder()
            val pub: ByteArray = encoder.encode(pub.encoded)
            val splitter = byteArrayOf('\n'.code.toByte())
            val priv: ByteArray = encoder.encode(priv.encoded)
            Files.write(file.toPath(), com.google.common.primitives.Bytes.concat(pub, splitter, priv))
        } catch (e: java.lang.Exception) {
            e.printStackTrace()
        }
    }

    @kotlin.Throws(
        NoSuchPaddingException::class,
        NoSuchAlgorithmException::class,
        java.security.InvalidKeyException::class,
        IllegalBlockSizeException::class,
        BadPaddingException::class
    )
    fun decrypt(message: String?): String {
        val decryptCipher: Cipher = Cipher.getInstance("RSA")
        decryptCipher.init(Cipher.DECRYPT_MODE, priv)
        val decoder: java.util.Base64.Decoder = java.util.Base64.getDecoder()
        val encrypted: ByteArray = decryptCipher.doFinal(decoder.decode(message))
        return String(encrypted, java.nio.charset.StandardCharsets.UTF_8)
    }

    class OtherPlayer(keyb64: String?) {
        var pub: PublicKey

        @kotlin.Throws(
            NoSuchPaddingException::class,
            NoSuchAlgorithmException::class,
            java.security.InvalidKeyException::class,
            IllegalBlockSizeException::class,
            BadPaddingException::class
        )
        fun encrypt(message: String): String {
            val encryptCipher: Cipher = Cipher.getInstance("RSA")
            encryptCipher.init(Cipher.ENCRYPT_MODE, pub)
            val encrypted: ByteArray = encryptCipher.doFinal(message.toByteArray())
            val encoder: java.util.Base64.Encoder = java.util.Base64.getEncoder()
            return String(encoder.encode(encrypted), java.nio.charset.StandardCharsets.UTF_8)
        }

        init {
            val decoder: java.util.Base64.Decoder = java.util.Base64.getDecoder()
            val key: ByteArray = decoder.decode(keyb64)
            val keyFactory: KeyFactory = KeyFactory.getInstance("RSA")
            val publicKeySpec: EncodedKeySpec = X509EncodedKeySpec(key)
            pub = keyFactory.generatePublic(publicKeySpec)
        }
    }
}