package com.dworf.slots_demo.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

/**
 * C3 one-shot migration: hashes every legacy plaintext users.password into users.password_hash and then
 * drops the plaintext column, but ONLY when every row has a hash. Safe to leave enabled: it is a no-op
 * once the legacy column is gone. Passwords BCrypt cannot hash (> 72 bytes) get an unusable hash ('!')
 * and the player must be reset manually by the operator.
 */
@Component
public class LegacyPasswordMigrator implements ApplicationRunner {
    private static final Logger log = LoggerFactory.getLogger(LegacyPasswordMigrator.class);

    private final JdbcTemplate jdbc;
    private final PasswordEncoder encoder;
    private final boolean dropColumn;

    public LegacyPasswordMigrator(JdbcTemplate jdbc, PasswordEncoder encoder,
                                  @Value("${slots.migration.drop-legacy-password-column:true}") boolean dropColumn) {
        this.jdbc = jdbc;
        this.encoder = encoder;
        this.dropColumn = dropColumn;
    }

    @Override
    public void run(ApplicationArguments args) {
        List<Map<String, Object>> rows;
        try {
            rows = jdbc.queryForList("SELECT id, password FROM users WHERE password_hash IS NULL");
        } catch (DataAccessException noLegacyColumn) {
            return; // schema is already clean
        }
        int hashed = 0;
        int unusable = 0;
        for (Map<String, Object> r : rows) {
            Object id = r.get("id");
            String plain = r.get("password") == null ? "" : r.get("password").toString();
            String hash;
            try {
                hash = plain.isBlank() ? "!" : encoder.encode(plain);
            } catch (IllegalArgumentException tooLong) {
                hash = "!";
            }
            if ("!".equals(hash)) unusable++; else hashed++;
            jdbc.update("UPDATE users SET password_hash = ? WHERE id = ?", hash, id);
        }
        if (!rows.isEmpty()) {
            log.warn("Legacy password migration: {} hashed, {} marked unusable (manual reset needed)", hashed, unusable);
        }
        Integer missing = jdbc.queryForObject("SELECT COUNT(*) FROM users WHERE password_hash IS NULL", Integer.class);
        if (dropColumn && missing != null && missing == 0) {
            jdbc.execute("ALTER TABLE users DROP COLUMN password");
            log.warn("Legacy plaintext column users.password has been dropped");
        }
    }
}
