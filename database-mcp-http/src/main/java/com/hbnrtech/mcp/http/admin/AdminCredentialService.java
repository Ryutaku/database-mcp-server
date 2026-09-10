package com.hbnrtech.mcp.http.admin;

import com.hbnrtech.mcp.http.admin.ConfigModels.StoredAdminCredential;
import com.hbnrtech.mcp.http.config.DatabaseMcpHttpProperties;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class AdminCredentialService {
   private static final Logger LOGGER = LoggerFactory.getLogger(AdminCredentialService.class);
   private static final DateTimeFormatter DEFAULT_PASSWORD_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd");

   private final SqliteConfigRepository repository;
   private volatile StoredAdminCredential credential;

   public AdminCredentialService(SqliteConfigRepository repository, DatabaseMcpHttpProperties properties) {
      this.repository = repository;
      Optional<StoredAdminCredential> stored = repository.loadAdminCredential();
      if (stored.isPresent()) {
         this.credential = stored.get();
         LOGGER.info("Loaded admin credential for username [{}]", this.credential.username());
      } else {
         StoredAdminCredential seeded = create(resolveDefaultUsername(properties.getAdminUsername()), resolveDefaultPassword(properties.getAdminPassword()));
         repository.upsertAdminCredential(seeded);
         this.credential = seeded;
         LOGGER.info("Initialized default admin credential for username [{}]; change the password in the admin console", seeded.username());
      }
   }

   public String username() {
      return this.credential.username();
   }

   public boolean authenticate(String username, String password) {
      if (username == null || password == null) {
         return false;
      }
      StoredAdminCredential current = this.credential;
      return current.username().equals(username.trim())
         && PasswordHasher.verify(password, current.salt(), current.passwordHash());
   }

   public synchronized void changePassword(String currentPassword, String newPassword) {
      StoredAdminCredential current = this.credential;
      if (!PasswordHasher.verify(currentPassword, current.salt(), current.passwordHash())) {
         throw new IllegalArgumentException("当前密码不正确");
      }
      validatePassword(newPassword);
      if (PasswordHasher.verify(newPassword, current.salt(), current.passwordHash())) {
         throw new IllegalArgumentException("新密码不能与当前密码相同");
      }
      StoredAdminCredential next = create(current.username(), newPassword);
      this.repository.upsertAdminCredential(next);
      this.credential = next;
      LOGGER.info("Admin password updated for username [{}]", next.username());
   }

   private static StoredAdminCredential create(String username, String rawPassword) {
      String salt = PasswordHasher.newSalt();
      String hash = PasswordHasher.hash(rawPassword, salt);
      return new StoredAdminCredential(username, salt, hash, OffsetDateTime.now().toString());
   }

   private static void validatePassword(String password) {
      if (password == null || password.isEmpty()) {
         throw new IllegalArgumentException("新密码不能为空");
      }
      if (password.length() < 8 || password.length() > 64) {
         throw new IllegalArgumentException("密码长度需为 8-64 位");
      }
      int categories = 0;
      if (password.matches(".*[a-z].*")) {
         categories++;
      }
      if (password.matches(".*[A-Z].*")) {
         categories++;
      }
      if (password.matches(".*[0-9].*")) {
         categories++;
      }
      if (password.matches(".*[^A-Za-z0-9].*")) {
         categories++;
      }
      if (categories < 2) {
         throw new IllegalArgumentException("密码需至少包含大写字母、小写字母、数字、特殊字符中的两类");
      }
   }

   private static String resolveDefaultUsername(String username) {
      return username == null || username.isBlank() ? "admin" : username.trim();
   }

   private static String resolveDefaultPassword(String configuredPassword) {
      if (configuredPassword != null && !configuredPassword.isBlank()) {
         return configuredPassword;
      }
      return LocalDate.now().format(DEFAULT_PASSWORD_FORMATTER);
   }
}
