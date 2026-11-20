package za.co.mongala.legal.backend.security;

import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.PBEKeySpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Base64;

/**
 * AES-256-GCM encryption for uploaded documents at rest (FR11: encryption of
 * client data / POPIA). Files are stored encrypted; the AI pipeline decrypts
 * them in memory only for processing.
 */
public final class EncryptionUtil {

    private static final int IV_BYTES = 12;
    private static final int TAG_BITS = 128;
    private static final SecureRandom RANDOM = new SecureRandom();

    private final SecretKeySpec key;

    public EncryptionUtil(String passphrase) {
        try {
            // derive a 256-bit key from the passphrase
            PBEKeySpec spec = new PBEKeySpec(passphrase.toCharArray(),
                    "legal-case-erp".getBytes(StandardCharsets.UTF_8), 60_000, 256);
            SecretKeyFactory f = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256");
            this.key = new SecretKeySpec(f.generateSecret(spec).getEncoded(), "AES");
        } catch (Exception e) {
            throw new IllegalStateException("Cannot initialise encryption", e);
        }
    }

    /** @return base64(iv + ciphertext + tag) */
    public String encrypt(byte[] plain) {
        try {
            byte[] iv = new byte[IV_BYTES];
            RANDOM.nextBytes(iv);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, key, new GCMParameterSpec(TAG_BITS, iv));
            byte[] ct = cipher.doFinal(plain);
            byte[] out = new byte[iv.length + ct.length];
            System.arraycopy(iv, 0, out, 0, iv.length);
            System.arraycopy(ct, 0, out, iv.length, ct.length);
            return Base64.getEncoder().encodeToString(out);
        } catch (Exception e) {
            throw new IllegalStateException("Encryption failed", e);
        }
    }

    public byte[] decrypt(String b64) {
        try {
            byte[] all = Base64.getDecoder().decode(b64);
            byte[] iv = java.util.Arrays.copyOfRange(all, 0, IV_BYTES);
            byte[] ct = java.util.Arrays.copyOfRange(all, IV_BYTES, all.length);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, key, new GCMParameterSpec(TAG_BITS, iv));
            return cipher.doFinal(ct);
        } catch (Exception e) {
            throw new IllegalStateException("Decryption failed - key mismatch or corrupted file", e);
        }
    }
}
