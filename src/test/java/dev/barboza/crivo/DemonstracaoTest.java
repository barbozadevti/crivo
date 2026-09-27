package dev.barboza.crivo;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

import dev.barboza.crivo.config.Demonstracao;
import dev.barboza.crivo.dominio.Etapa;
import dev.barboza.crivo.servico.PainelService;
import dev.barboza.crivo.servico.PainelService.TipoDeAtencao;
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
    PainelService painel;

    @Autowired
    Demonstracao demonstracao;

    private Funil vaga(String titulo) {
        return recrutamento.vagas().stream().filter(f -> f.vaga().getTitulo().equals(titulo)).findFirst().orElseThrow();
    }

    @Test
    void vagasDeExemploEmMomentosDiferentesDoProcesso() {
        assertThat(recrutamento.vagas()).hasSize(6);

        Funil java = vaga("Desenvolvedor(a) Java Pleno");
        assertThat(java.porEtapa().get(Etapa.PROPOSTA)).isEqualTo(1);
        assertThat(java.porEtapa().get(Etapa.ENTREVISTA)).isEqualTo(1);
        assertThat(java.livres()).isZero();

        Funil suporte = vaga("Analista de Suporte Júnior");
        assertThat(suporte.contratados()).isEqualTo(1);
        assertThat(suporte.porEtapa().get(Etapa.SEM_CONTATO)).isEqualTo(1);

        assertThat(vaga("Assistente Administrativo").porEtapa().get(Etapa.INSCRITO)).isEqualTo(10);
        assertThat(vaga("Estágio em Recursos Humanos").vaga().aberta()).isFalse();
    }

    @Test
    void painelTemUmExemploDeCadaAlerta() {
        PainelService.Painel p = painel.painel();

        assertThat(p.atencao()).extracting(PainelService.Atencao::tipo).contains(TipoDeAtencao.SELECIONAR, TipoDeAtencao.LIGAR,
                TipoDeAtencao.AVALIAR, TipoDeAtencao.ENVIAR_PROPOSTA, TipoDeAtencao.REVER_AVALIACAO, TipoDeAtencao.COBRAR_PROPOSTA);
        assertThat(p.vagasAbertas()).isEqualTo(5);
        assertThat(p.diasAteContratar()).isNotNull().isPositive();
        assertThat(p.atividade()).hasSize(12);
    }

    @Test
    void reiniciarRecriaTudo() {
        demonstracao.reiniciar();
        assertThat(recrutamento.vagas()).hasSize(6);
    }
}
