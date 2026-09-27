package dev.barboza.crivo;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

/** Relógio fixo em 27/09/2026 12:00 (Brasília) para testes determinísticos. */
@TestConfiguration
public class RelogioFixo {

    public static final Instant AGORA = Instant.parse("2026-09-27T15:00:00Z");

    @Bean
    @Primary
    Clock relogioFixo() {
        return Clock.fixed(AGORA, ZoneOffset.UTC);
    }
}
