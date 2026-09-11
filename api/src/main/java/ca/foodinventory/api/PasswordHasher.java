package ca.foodinventory.api;

import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.security.spec.KeySpec;
import java.util.Base64;

class PasswordHasher {

    static final int DEFAULT_ITERATIONS = 600_000;
    private static final int SALT_BYTES = 16;
    private static final int HASH_BITS = 256;

    String newSalt() {
        byte[] salt = new byte[SALT_BYTES];
        new SecureRandom().nextBytes(salt);
        return Base64.getEncoder().encodeToString(salt);
    }

    String hash(String password, String encodedSalt, int iterations) {
        byte[] salt = Base64.getDecoder().decode(encodedSalt);
        return Base64.getEncoder().encodeToString(hashPassword(password, salt, iterations));
    }

    boolean verify(String password, String encodedSalt, String encodedHash, int iterations) {
        if (password == null || encodedSalt == null || encodedHash == null) {
            return false;
        }

        try {
            byte[] expected = Base64.getDecoder().decode(encodedHash);
            byte[] actual = Base64.getDecoder().decode(hash(password, encodedSalt, iterations));
            return MessageDigest.isEqual(expected, actual);
        } catch (RuntimeException e) {
            return false;
        }
    }

    private byte[] hashPassword(String password, byte[] salt, int iterations) {
        try {
            KeySpec spec = new PBEKeySpec(password.toCharArray(), salt, iterations, HASH_BITS);
            SecretKeyFactory factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256");
            return factory.generateSecret(spec).getEncoded();
        } catch (Exception e) {
            throw new RuntimeException("Failed to hash location password.", e);
        }
    }
}
