package example.com.utils

import java.security.SecureRandom
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec
import java.util.Base64

object PasswordUtils {
    private const val ITERATIONS = 65536
    private const val KEY_LENGTH = 256
    private const val ALGORITHM = "PBKDF2WithHmacSHA256"
    private const val SALT_LENGTH = 16

    fun hashPassword(password: String, salt: ByteArray): ByteArray {
        val keySpec = PBEKeySpec(password.toCharArray(), salt, ITERATIONS, KEY_LENGTH)
        val keyFactory = SecretKeyFactory.getInstance(ALGORITHM)
        return keyFactory.generateSecret(keySpec).encoded
    }

    fun generateSalt(): ByteArray {
        val salt = ByteArray(SALT_LENGTH)
        SecureRandom().nextBytes(salt)
        return salt
    }

    fun verifyPassword(password: String, storedHash: String, salt: ByteArray): Boolean {
        val hashedInput = hashPassword(password, salt)
        return Base64.getEncoder().encodeToString(hashedInput) == storedHash
    }
}