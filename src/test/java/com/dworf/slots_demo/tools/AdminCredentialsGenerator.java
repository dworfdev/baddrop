package com.dworf.slots_demo.tools;

import com.dworf.slots_demo.security.Totp;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

/**
 * Not a test. Run its main() from the IDE with the admin password as the only program argument:
 *   prints ADMIN_PASSWORD_HASH and a new ADMIN_TOTP_SECRET.
 * Add the secret to an authenticator app by hand (type: time-based, SHA-1, 6 digits, 30 s).
 * Keep the password and secret out of git; put the printed values into environment variables.
 */
public final class AdminCredentialsGenerator {
    private AdminCredentialsGenerator() {}

    public static void main(String[] args) {
        if (args.length != 1 || args[0].length() < 12) {
            System.err.println("Usage: AdminCredentialsGenerator <admin-password, at least 12 characters>");
            System.exit(1);
        }
        String secret = Totp.generateSecret();
        System.out.println("ADMIN_PASSWORD_HASH=" + new BCryptPasswordEncoder(12).encode(args[0]));
        System.out.println("ADMIN_TOTP_SECRET=" + secret);
        System.out.println("(authenticator app: add account manually, key = ADMIN_TOTP_SECRET, time-based)");
    }
}