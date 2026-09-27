package dev.barboza.crivo.config;

import java.io.IOException;
import java.time.Clock;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.boot.web.server.context.WebServerApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.event.EventListener;
import org.springframework.core.env.Environment;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;

@Configuration
public class Configuracao {

    private static final Logger log = LoggerFactory.getLogger(Configuracao.class);

    /** Relógio injetável: os testes trocam por um relógio fixo. */
    @Bean
    Clock relogio() {
        return Clock.systemUTC();
    }

    @Bean
    OpenAPI documentacao() {
        return new OpenAPI().info(new Info()
                .title("Crivo — API de recrutamento")
                .version("1.0")
                .description("Vagas, triagem pelo orçamento, seleção até completar as vagas, tentativas de contato "
                        + "com chamada de suplente e máquina de estados das etapas do processo seletivo."));
    }

    /** Usado pelo atalho: quando o servidor fica pronto, abre o site no navegador padrão (Windows). */
    @EventListener
    public void aoFicarPronto(ApplicationReadyEvent evento) {
        Environment ambiente = evento.getApplicationContext().getEnvironment();
        if (!ambiente.getProperty("crivo.abrir-navegador", Boolean.class, false)
                || !(evento.getApplicationContext() instanceof WebServerApplicationContext web)) {
            return;
        }
        String endereco = "http://localhost:" + web.getWebServer().getPort();
        try {
            new ProcessBuilder("cmd", "/c", "start", "", endereco).start();
        } catch (IOException e) {
            log.warn("Não foi possível abrir o navegador; acesse {}", endereco);
        }
    }
}
