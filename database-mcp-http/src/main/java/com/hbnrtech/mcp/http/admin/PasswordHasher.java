package com.hbnrtech.mcp.http.admin;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.security.spec.KeySpec;
import java.util.Base64;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;

final class PasswordHasher {
   private static final int ITERATIONS = 120_000;
   private static final int KEY_LENGTH_BITS = 256;
   private static final int SALT_LENGTH_BYTES = 16;
   private static final SecureRandom RANDOM = new SecureRandom();

   private PasswordHasher() {
   }

   static String newSalt() {
      byte[] salt = new byte[SALT_LENGTH_BYTES];
      RANDOM.nextBytes(salt);
      return Base64.getEncoder().encodeToString(salt);
   }

   static String hash(String password, String salt) {
      try {
         byte[] saltBytes = Base64.getDecoder().decode(salt);
         KeySpec spec = new PBEKeySpec(password.toCharArray(), saltBytes, ITERATIONS, KEY_LENGTH_BITS);
         SecretKeyFactory factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256");
         byte[] encoded = factory.generateSecret(spec).getEncoded();
         return Base64.getEncoder().encodeToString(encoded);
      } catch (Exception ex) {
         throw new IllegalStateException("Failed to hash admin password", ex);
      }
   }

   static boolean verify(String password, String salt, String expectedHash) {
      if (password == null || salt == null || expectedHash == null) {
         return false;
      }
      String actual = hash(password, salt);
      return MessageDigest.isEqual(
         actual.getBytes(StandardCharsets.UTF_8),
         expectedHash.getBytes(StandardCharsets.UTF_8)
      );
   }
}
