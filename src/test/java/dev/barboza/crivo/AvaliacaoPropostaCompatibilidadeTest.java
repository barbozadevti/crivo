package dev.barboza.crivo;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.time.Instant;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import dev.barboza.crivo.dominio.Candidato;
import dev.barboza.crivo.dominio.Compatibilidade;
import dev.barboza.crivo.dominio.Etapa;
import dev.barboza.crivo.dominio.Evento;
import dev.barboza.crivo.dominio.Habilidades;
import dev.barboza.crivo.dominio.Vaga;
import dev.barboza.crivo.servico.PainelService;
import dev.barboza.crivo.servico.RecrutamentoService;
import dev.barboza.crivo.servico.RecrutamentoService.NovaVaga;
import dev.barboza.crivo.servico.RecrutamentoService.NovoCandidato;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@ActiveProfiles("test")
@Import(RelogioFixo.class)
@Transactional
class AvaliacaoPropostaCompatibilidadeTest {

    private static final Instant AGORA = RelogioFixo.AGORA;

    @Autowired
    RecrutamentoService recrutamento;

    @Autowired
    PainelService painel;

    private Vaga vaga() {
        return recrutamento.criarVaga(new NovaVaga("Dev Java", "TI", null, new BigDecimal("9000"), 1,
                "Java, Spring Boot, SQL, Docker", Vaga.Modelo.REMOTO, "Brasil"));
    }

    private Candidato inscrever(Vaga vaga, String nome, String salario, String habilidades) {
        return recrutamento.inscrever(vaga.getId(), new NovoCandidato(nome, nome.toLowerCase().replace(' ', '.') + "@x.com", null,
                new BigDecimal(salario), habilidades, null, Candidato.Origem.CARREIRAS));
    }

    /** Coloca o candidato em entrevista (selecionado e com contato feito). */
    private Candidato naEntrevista(Vaga vaga, String salario) {
        Candidato c = inscrever(vaga, "Ana Souza", salario, "Java, SQL");
        recrutamento.selecionar(vaga.getId());
        recrutamento.registrarContato(c.getId(), true);
        return c;
    }

    // ---------- Compatibilidade ----------

    @ParameterizedTest(name = "{0} pedindo {1} -> {2}")
    @CsvSource(delimiter = '|', value = {
            "java, spring boot, sql, docker | 8000 | 100",
            "Java, SQL                      | 8000 | 60",
            "JAVA, Sql, Kotlin              | 9000 | 55",
            "Python                         | 8000 | 20",
            "Java, Spring Boot, SQL, Docker | 9500 | 80"})
    void compatibilidadeSomaRequisitosEOrcamento(String habilidades, String salario, int esperada) {
        Vaga vaga = new Vaga("Dev", "TI", null, new BigDecimal("9000"), 1, "Java, Spring Boot, SQL, Docker", null, null, AGORA);
        Candidato c = new Candidato(vaga, "Ana Souza", "a@x.com", null, new BigDecimal(salario), habilidades, null, null, AGORA);
        assertThat(Compatibilidade.de(vaga, c).pontuacao()).isEqualTo(esperada);
    }

    @Test
    void compatibilidadeIgnoraAcentoECaixaEListaOQueFalta() {
        Vaga vaga = new Vaga("Suporte", "TI", null, new BigDecimal("3000"), 1, "Comunicação, Redes", null, null, AGORA);
        Candidato c = new Candidato(vaga, "Ana Souza", "a@x.com", null, new BigDecimal("2500"), "comunicacao", null, null, AGORA);

        Compatibilidade r = Compatibilidade.de(vaga, c);

        assertThat(r.atende()).containsExactly("Comunicação");
        assertThat(r.faltam()).containsExactly("Redes");
        Vaga semRequisitos = new Vaga("Suporte", "TI", null, new BigDecimal("3000"), 1, AGORA);
        assertThat(Compatibilidade.de(semRequisitos, new Candidato(semRequisitos, "Ana Souza", "a@x.com", null,
                new BigDecimal("2500"), AGORA)).pontuacao()).isEqualTo(100);
    }

    @Test
    void habilidadesSemRepeticaoEComLimite() {
        assertThat(Habilidades.normalizarTexto(" Java ;java, SQL\nDocker ,, ", "habilidades")).isEqualTo("Java, SQL, Docker");
        assertThatThrownBy(() -> Habilidades.normalizarTexto("a,b,c,d,e,f,g,h,i,j,k,l,m", "habilidades"))
                .hasMessage("Informe no máximo 12 itens em habilidades.");
    }

    // ---------- Avaliação e proposta ----------

    @Test
    void propostaExigeAvaliacaoComNotaMinima() {
        Candidato ana = naEntrevista(vaga(), "8500");

        assertThatThrownBy(() -> recrutamento.mover(ana.getId(), Etapa.PROPOSTA, null))
                .hasMessage("Para ir para Proposta, envie a proposta com o valor oferecido.");
        assertThatThrownBy(() -> recrutamento.enviarProposta(ana.getId(), new BigDecimal("8500")))
                .hasMessage("Registre a avaliação da entrevista antes de enviar a proposta.");
        assertThatThrownBy(() -> recrutamento.avaliar(ana.getId(), 6, "Parecer suficiente.")).hasMessage("A nota da entrevista vai de 1 a 5.");
        assertThatThrownBy(() -> recrutamento.avaliar(ana.getId(), 4, "curto")).hasMessage("Escreva um parecer de 10 a 500 caracteres.");

        recrutamento.avaliar(ana.getId(), 2, "Não demonstrou domínio de SQL.");
        assertThatThrownBy(() -> recrutamento.enviarProposta(ana.getId(), new BigDecimal("8500")))
                .hasMessage("A entrevista teve nota 2/5; a proposta exige pelo menos 3.");

        recrutamento.avaliar(ana.getId(), 4, "Reavaliada após segunda conversa técnica.");
        recrutamento.enviarProposta(ana.getId(), new BigDecimal("8500"));
        assertThat(ana.getEtapa()).isEqualTo(Etapa.PROPOSTA);
        assertThat(ana.getSalarioOfertado()).isEqualByComparingTo("8500");
    }

    @Test
    void propostaDentroDoOrcamentoEContrapropostaNoHistorico() {
        Candidato ana = naEntrevista(vaga(), "8800");
        recrutamento.avaliar(ana.getId(), 5, "Excelente entrevista técnica.");

        assertThatThrownBy(() -> recrutamento.enviarProposta(ana.getId(), new BigDecimal("9000.01")))
                .hasMessage("A proposta passa do orçamento da vaga.");
        recrutamento.enviarProposta(ana.getId(), new BigDecimal("8200"));

        assertThat(recrutamento.historico(ana.getId())).extracting(Evento::getDescricao).contains(
                "Entrevista avaliada com nota 5/5: Excelente entrevista técnica.",
                "Entrevista → Proposta: proposta de R$ 8.200,00 (contraproposta: pretendia R$ 8.800,00)");
    }

    @Test
    void avaliacaoSoDepoisDaEntrevista() {
        Vaga vaga = vaga();
        Candidato ana = inscrever(vaga, "Ana Souza", "8000", null);
        assertThatThrownBy(() -> recrutamento.avaliar(ana.getId(), 4, "Parecer qualquer aqui.")).hasMessage("A avaliação é registrada depois da entrevista.");
    }

    @Test
    void notasDoRecrutadorEntramNoHistorico() {
        Candidato ana = inscrever(vaga(), "Ana Souza", "8000", null);
        recrutamento.anotar(ana.getId(), "  Pediu retorno   na segunda  ");
        assertThat(recrutamento.historico(ana.getId())).extracting(Evento::getDescricao).contains("Pediu retorno na segunda");
        assertThatThrownBy(() -> recrutamento.anotar(ana.getId(), " ")).hasMessage("A nota deve ter de 2 a 500 caracteres.");
    }

    @Test
    void linkedinValidado() {
        Vaga vaga = vaga();
        assertThatThrownBy(() -> recrutamento.inscrever(vaga.getId(), new NovoCandidato("Ana Souza", "a@x.com", null,
                new BigDecimal("1"), null, "https://twitter.com/ana", null)))
                .hasMessageContaining("linkedin.com/in/");
        Candidato ana = recrutamento.inscrever(vaga.getId(), new NovoCandidato("Ana Souza", "a@x.com", null, new BigDecimal("1"),
                null, "linkedin.com/in/ana-souza", null));
        assertThat(ana.getLinkedin()).isEqualTo("https://linkedin.com/in/ana-souza");
    }

    // ---------- Visão geral ----------

    @Test
    void painelApontaOQuePrecisaDeAtencao() {
        Vaga vaga = recrutamento.criarVaga(new NovaVaga("Suporte", "TI", null, new BigDecimal("3000"), 2));
        Candidato ana = inscrever(vaga, "Ana Souza", "2500", null);
        Candidato bia = inscrever(vaga, "Bia Lima", "2600", null);
        inscrever(vaga, "Caio Reis", "2700", null);

        assertThat(painel.painel().atencao()).extracting(PainelService.Atencao::tipo)
                .contains(PainelService.TipoDeAtencao.SELECIONAR);

        recrutamento.selecionar(vaga.getId());
        recrutamento.registrarContato(ana.getId(), true);
        recrutamento.registrarContato(bia.getId(), false);

        PainelService.Painel p = painel.painel();
        assertThat(p.atencao()).extracting(PainelService.Atencao::titulo).contains(
                "Registrar a avaliação: Ana Souza", "Tentar contato de novo: Bia Lima");
        assertThat(p.atencao()).extracting(PainelService.Atencao::tipo).doesNotContain(PainelService.TipoDeAtencao.SELECIONAR);
        assertThat(p.candidatosAtivos()).isGreaterThanOrEqualTo(3);
        assertThat(p.origens().get(Candidato.Origem.CARREIRAS)).isGreaterThanOrEqualTo(3);
        assertThat(p.atividade()).isNotEmpty();
    }
}
