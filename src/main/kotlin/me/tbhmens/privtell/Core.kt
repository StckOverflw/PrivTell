package me.tbhmens.privtell

import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File
import java.nio.file.Files
import java.security.*
import java.security.spec.PKCS8EncodedKeySpec
import java.security.spec.X509EncodedKeySpec
import javax.crypto.Cipher
import kotlin.text.Charsets.UTF_8


class Core {
    private val signPair: KeyPair
    private val encryptPair: KeyPair
    private val file: KeysFile
    val encryptPubB64: String
    val signPubB64: String

    constructor() {
        val signGen = KeyPairGenerator.getInstance("EC")
        signGen.initialize(256)
        signPair = signGen.generateKeyPair()

        val encryptGen = KeyPairGenerator.getInstance("RSA")
        encryptGen.initialize(512)
        encryptPair = encryptGen.generateKeyPair()

        file = KeysFile(KPair.from(signPair, "EC"), KPair.from(encryptPair, "RSA"))

        encryptPubB64 = Base64.encode(encryptPair.public.encoded)
        signPubB64 = Base64.encode(signPair.public.encoded)
    }

    companion object {
        fun fromFile(file: File): Core {
            return try {
                Core(KeysFile.parse(file))
            } catch (err: SerializationException) {
                Core().apply { save(file) }
            }
        }
    }

    constructor(file: KeysFile) {
        signPair = file.sign.toKeyPair()
        encryptPair = file.encrypt.toKeyPair()
        this.file = file

        encryptPubB64 = Base64.encode(encryptPair.public.encoded)
        signPubB64 = Base64.encode(signPair.public.encoded)
    }

    /** @return Pair(message, signature) */
    fun message(player: OtherPlayer, msg: String): Pair<String, String> {
        val (message, signature) = message(player, msg.toByteArray(UTF_8))
        return Pair(Base64.encode(message), Base64.encode(signature))
    }

    private fun message(player: OtherPlayer, data: ByteArray): Pair<ByteArray, ByteArray> {
        // signed_message = sign(message, connection.your_private_sign_key)
        val signer = Signature.getInstance("SHA256withECDSA")
        signer.initSign(signPair.private)
        signer.update(data)
        val signature = signer.sign()

        // encrypted_message = encrypt(signed_message, connection.their_public_encrypt_key)
        val encryptCipher = Cipher.getInstance("RSA")
        encryptCipher.init(Cipher.ENCRYPT_MODE, player.pubEnc)
        val message = encryptCipher.doFinal(data)

        // send(encrypted_message)
        return Pair(message, signature)
    }


    /** @return null if wrong signature. */
    fun receive(player: OtherPlayer, message: String, signature: String): String? {
        return receive(player, Base64.decode(message), Base64.decode(signature))
    }

    private fun receive(player: OtherPlayer, encryptedMessage: ByteArray, signature: ByteArray): String? {
        // message = decrypt(encrypted_message, connection.your_private_encrypt_key)
        val decryptCipher = Cipher.getInstance("RSA")
        decryptCipher.init(Cipher.DECRYPT_MODE, this.encryptPair.private)
        val message = decryptCipher.doFinal(encryptedMessage)

        // success = check_signature(message, signature, connection.their_public_sign_key)
        val verifier = Signature.getInstance("SHA256withECDSA")
        verifier.initVerify(player.pubSign)
        verifier.update(message)
        val success = verifier.verify(signature)
        if (!success)
            return null
        return message.toString(UTF_8)
    }

    fun save(to: File) {
        file.save(to)
    }
}

fun decodeSignPub(pub: String): PublicKey {
    val pubB = Base64.decode(pub)
    val factory = KeyFactory.getInstance("EC")
    val pubSpec = X509EncodedKeySpec(pubB)
    return factory.generatePublic(pubSpec)
}

fun decodeEncPub(pub: String): PublicKey {
    val pubB = Base64.decode(pub)
    val factory = KeyFactory.getInstance("RSA")
    val pubSpec = X509EncodedKeySpec(pubB)
    return factory.generatePublic(pubSpec)
}

data class OtherPlayer(var pubSign: PublicKey?, var pubEnc: PublicKey?)

@Serializable
data class KPair(val priv: String, val pub: String, val alg: String) {
    companion object {
        /** @param algorithm "RSA" for encrypt, "EC" for sign */
        fun from(pair: KeyPair, algorithm: String): KPair {
            val priv = Base64.encode(pair.private.encoded)
            val pub = Base64.encode(pair.public.encoded)
            return KPair(priv, pub, algorithm)
        }
    }

    fun toKeyPair(): KeyPair {
        val privB = Base64.decode(this.priv)
        val pubB = Base64.decode(this.pub)
        val factory = KeyFactory.getInstance(alg)
        val privSpec = PKCS8EncodedKeySpec(privB)
        val pubSpec = X509EncodedKeySpec(pubB)
        val privK = factory.generatePrivate(privSpec)
        val pubK = factory.generatePublic(pubSpec)

        return KeyPair(pubK, privK)
    }
}

@Serializable
data class KeysFile(val sign: KPair, val encrypt: KPair) {
    companion object {
        fun parse(file: File): KeysFile {
            val str = Files.readString(file.toPath(), UTF_8)
            return Json.decodeFromString(str)
        }
    }

    fun save(file: File) {
        val str = Json.encodeToString(this)
        Files.write(file.toPath(), str.toByteArray(UTF_8))
    }
}

class Base64 {
    companion object {
        private val decoder = java.util.Base64.getDecoder()
        private val encoder = java.util.Base64.getEncoder()
        fun encode(str: ByteArray): String {
            return encoder.encode(str).toString(UTF_8)
        }

        fun decode(str: String): ByteArray {
            return decoder.decode(str)
        }
    }
}