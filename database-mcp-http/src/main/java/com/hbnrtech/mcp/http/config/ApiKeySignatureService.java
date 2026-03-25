package com.hbnrtech.mcp.http.config;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.Base64;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.stereotype.Component;

@Component
public class ApiKeySignatureService {
   private static final Base64.Decoder BASE64_URL_DECODER = Base64.getUrlDecoder();

   private final DatabaseMcpHttpProperties properties;

   public ApiKeySignatureService(DatabaseMcpHttpProperties properties) {
      this.properties = properties;
   }

   public boolean isConfigured() {
      return this.properties.getApiKeySecret() != null && !this.properties.getApiKeySecret().isBlank();
   }

   public boolean validate(String apiKey) {
      if (!this.isConfigured() || apiKey == null || apiKey.isBlank()) {
         return false;
      }

      String[] parts = apiKey.split("\\.");
      if (parts.length != 4) {
         return false;
      }

      String clientId = parts[0];
      String timestampPart = parts[1];
      String nonce = parts[2];
      String signature = parts[3];
      if (clientId.isBlank() || nonce.isBlank() || signature.isBlank()) {
         return false;
      }

      long timestamp;
      try {
         timestamp = Long.parseLong(timestampPart);
      } catch (NumberFormatException ex) {
         return false;
      }

      long now = Instant.now().getEpochSecond();
      long allowedPast = this.properties.getApiKeyTtlSeconds() + this.properties.getApiKeyAllowedClockSkewSeconds();
      long allowedFuture = this.properties.getApiKeyAllowedClockSkewSeconds();
      if (timestamp < now - allowedPast || timestamp > now + allowedFuture) {
         return false;
      }

      String payload = clientId + "." + timestampPart + "." + nonce;
      byte[] expected = this.sign(payload);
      byte[] actual;
      try {
         actual = BASE64_URL_DECODER.decode(signature);
      } catch (IllegalArgumentException ex) {
         return false;
      }
      return MessageDigest.isEqual(expected, actual);
   }

   private byte[] sign(String payload) {
      try {
         Mac mac = Mac.getInstance("HmacSHA256");
         mac.init(new SecretKeySpec(this.properties.getApiKeySecret().getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
         return mac.doFinal(payload.getBytes(StandardCharsets.UTF_8));
      } catch (Exception ex) {
         throw new IllegalStateException("Failed to initialize API key signature service", ex);
      }
   }
}
