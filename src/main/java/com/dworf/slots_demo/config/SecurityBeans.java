package com.dworf.slots_demo.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.Clock;

/** Requires only org.springframework.security:spring-security-crypto (NOT the full Spring Security starter). */
@Configuration
@EnableScheduling
public class SecurityBeans {

    @Bean
    public PasswordEncoder passwordEncoder(@Value("${slots.bcrypt-strength:12}") int strength) {
        return new BCryptPasswordEncoder(strength);
    }

    @Bean
    public Clock clock() {
        return Clock.systemUTC();
    }
}
