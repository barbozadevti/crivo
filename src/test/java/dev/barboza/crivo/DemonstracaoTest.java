package dev.barboza.crivo;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

import dev.barboza.crivo.config.Demonstracao;
import dev.barboza.crivo.dominio.Etapa;
import dev.barboza.crivo.servico.RecrutamentoService;
import dev.barboza.crivo.servico.RecrutamentoService.Funil;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE,
        properties = {"crivo.demo=true", "spring.datasource.url=jdbc:h2:mem:crivo-demo;DB_CLOSE_DELAY=-1"})
@ActiveProfiles("test")
@Import(RelogioFixo.class)
class DemonstracaoTest {

    @Autowired
    RecrutamentoService recrutamento;

    @Autowired
    Demonstracao demonstracao;

    private Funil vaga(String titulo) {
        return recrutamento.vagas().stream().filter(f -> f.vaga().getTitulo().equals(titulo)).findFirst().orElseThrow();
    }

    @Test
    void vagasDeExemploEmMomentosDiferentesDoProcesso() {
        assertThat(recrutamento.vagas()).hasSize(4);

        Funil suporte = vaga("Analista de Suporte Júnior");
        assertThat(suporte.contratados()).isEqualTo(1);
        assertThat(suporte.porEtapa().get(Etapa.ENTREVISTA)).isEqualTo(1);
        assertThat(suporte.porEtapa().get(Etapa.SEM_CONTATO)).isEqualTo(1);
        assertThat(suporte.livres()).isZero();

        assertThat(vaga("Desenvolvedor Java Pleno").porEtapa().get(Etapa.SELECIONADO)).isEqualTo(2);
        assertThat(vaga("Assistente Administrativo").porEtapa().get(Etapa.INSCRITO)).isEqualTo(10);
        assertThat(vaga("Estágio em Recursos Humanos").vaga().aberta()).isFalse();
    }

    @Test
    void reiniciarRecriaTudo() {
        demonstracao.reiniciar();
        assertThat(recrutamento.vagas()).hasSize(4);
    }
}
