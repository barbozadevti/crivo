package dev.barboza.crivo;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import dev.barboza.crivo.dominio.Candidato;
import dev.barboza.crivo.dominio.Etapa;
import dev.barboza.crivo.dominio.Evento;
import dev.barboza.crivo.dominio.Recomendacao;
import dev.barboza.crivo.dominio.RegraVioladaException;
import dev.barboza.crivo.dominio.ResultadoDoContato;
import dev.barboza.crivo.dominio.TransicaoInvalidaException;
import dev.barboza.crivo.dominio.Vaga;
import dev.barboza.crivo.servico.RecrutamentoService;
import dev.barboza.crivo.servico.RecrutamentoService.NovaVaga;
import dev.barboza.crivo.servico.RecrutamentoService.NovoCandidato;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@ActiveProfiles("test")
@Import(RelogioFixo.class)
@Transactional
class RecrutamentoServiceTest {

    @Autowired
    RecrutamentoService recrutamento;

    private Vaga vaga(String base, int vagas) {
        return recrutamento.criarVaga(new NovaVaga("Analista de Suporte", "Tecnologia", null, new BigDecimal(base), vagas));
    }

    private Candidato inscrever(Vaga vaga, String nome, String salario) {
        String email = nome.toLowerCase().replace(' ', '.') + "@teste.dev";
        return recrutamento.inscrever(vaga.getId(), new NovoCandidato(nome, email, null, new BigDecimal(salario)));
    }

    private List<String> nomesEm(Vaga vaga, Etapa etapa) {
        return recrutamento.candidatosDa(vaga.getId()).stream().filter(c -> c.getEtapa() == etapa).map(Candidato::getNome).toList();
    }

    @Test
    void inscricaoFazTriagemERegistraHistorico() {
        Vaga vaga = vaga("2000", 5);

        Candidato ana = inscrever(vaga, "Ana Souza", "1900");

        assertThat(ana.getEtapa()).isEqualTo(Etapa.INSCRITO);
        assertThat(ana.recomendacao()).isEqualTo(Recomendacao.LIGAR);
        assertThat(recrutamento.historico(ana.getId())).extracting(Evento::getDescricao)
                .containsExactly("Inscrição (cadastro manual). Triagem: ligar para o candidato.");
        assertThatThrownBy(() -> inscrever(vaga, "Ana Souza", "1800"))
                .hasMessage("ana.souza@teste.dev já está inscrito nesta vaga.");
    }

    @Test
    void selecaoSegueAOrdemDeInscricaoPulaQuemEstaAcimaEParaNoLimite() {
        Vaga vaga = vaga("2000", 2);
        inscrever(vaga, "Felipe Moreira", "2194");
        inscrever(vaga, "Marcia Oliveira", "1813");
        inscrever(vaga, "Julia Cardoso", "2000");
        inscrever(vaga, "Paulo Henrique", "1954");

        List<Candidato> selecionados = recrutamento.selecionar(vaga.getId());

        assertThat(selecionados).extracting(Candidato::getNome).containsExactly("Marcia Oliveira", "Julia Cardoso");
        assertThat(nomesEm(vaga, Etapa.INSCRITO)).containsExactly("Felipe Moreira", "Paulo Henrique");
        assertThatThrownBy(() -> recrutamento.selecionar(vaga.getId()))
                .hasMessage("Todas as vagas já estão ocupadas por candidatos em andamento.");
    }

    @Test
    void semNinguemNoOrcamentoAvisa() {
        Vaga vaga = vaga("2000", 1);
        inscrever(vaga, "Felipe Moreira", "2194");
        assertThatThrownBy(() -> recrutamento.selecionar(vaga.getId()))
                .hasMessage("Nenhum inscrito cabe no orçamento de R$ 2.000,00.");
    }

    @Test
    void naoSelecionaManualmenteAlemDoLimite() {
        Vaga vaga = vaga("3000", 1);
        Candidato ana = inscrever(vaga, "Ana Souza", "2500");
        Candidato bia = inscrever(vaga, "Beatriz Lima", "2600");
        recrutamento.mover(ana.getId(), Etapa.SELECIONADO, null);

        assertThatThrownBy(() -> recrutamento.mover(bia.getId(), Etapa.SELECIONADO, null))
                .isInstanceOf(RegraVioladaException.class)
                .hasMessage("Todas as 1 vagas já estão ocupadas por candidatos em andamento.");
    }

    @Test
    void semContatoSoPeloCrivoEAposTresTentativasComSuplente() {
        Vaga vaga = vaga("3000", 1);
        Candidato ana = inscrever(vaga, "Ana Souza", "2500");
        inscrever(vaga, "Carlos Caro", "3500");
        Candidato bia = inscrever(vaga, "Beatriz Lima", "2600");
        recrutamento.selecionar(vaga.getId());

        assertThatThrownBy(() -> recrutamento.mover(ana.getId(), Etapa.SEM_CONTATO, null))
                .hasMessageContaining("depois de 3 tentativas");

        assertThat(recrutamento.registrarContato(ana.getId(), false))
                .isInstanceOfSatisfying(ResultadoDoContato.NaoAtendeu.class, r -> assertThat(r.restantes()).isEqualTo(2));
        recrutamento.registrarContato(ana.getId(), false);
        ResultadoDoContato terceira = recrutamento.registrarContato(ana.getId(), false);

        // Carlos pede acima do orçamento: o suplente é a Beatriz, a próxima que cabe.
        assertThat(terceira).isInstanceOfSatisfying(ResultadoDoContato.SemContato.class,
                r -> assertThat(r.suplente().getNome()).isEqualTo("Beatriz Lima"));
        assertThat(ana.getEtapa()).isEqualTo(Etapa.SEM_CONTATO);
        assertThat(bia.getEtapa()).isEqualTo(Etapa.SELECIONADO);
        assertThat(recrutamento.historico(ana.getId())).extracting(Evento::getDescricao).contains(
                "Não atendeu (1ª tentativa).", "Não atendeu (3ª tentativa).", "Selecionados → Sem contato: 3 tentativas sem resposta");
    }

    @Test
    void atenderLevaParaEntrevista() {
        Vaga vaga = vaga("3000", 1);
        Candidato ana = inscrever(vaga, "Ana Souza", "2500");
        recrutamento.selecionar(vaga.getId());
        recrutamento.registrarContato(ana.getId(), false);

        assertThat(recrutamento.registrarContato(ana.getId(), true))
                .isInstanceOfSatisfying(ResultadoDoContato.Atendeu.class, r -> assertThat(r.tentativa()).isEqualTo(2));
        assertThat(ana.getEtapa()).isEqualTo(Etapa.ENTREVISTA);
        assertThatThrownBy(() -> recrutamento.registrarContato(ana.getId(), true))
                .hasMessage("Só é possível registrar contato com candidatos selecionados.");
    }

    @Test
    void reprovarQuemOcupavaVagaChamaOSuplente() {
        Vaga vaga = vaga("3000", 1);
        Candidato ana = inscrever(vaga, "Ana Souza", "2500");
        Candidato bia = inscrever(vaga, "Beatriz Lima", "2600");
        recrutamento.selecionar(vaga.getId());
        recrutamento.registrarContato(ana.getId(), true);

        recrutamento.mover(ana.getId(), Etapa.REPROVADO, "não passou na entrevista");

        assertThat(bia.getEtapa()).isEqualTo(Etapa.SELECIONADO);
        assertThat(recrutamento.historico(bia.getId())).extracting(Evento::getDescricao)
                .contains("Inscritos → Selecionados: suplente chamado para a vaga liberada");
    }

    @Test
    void transicaoInvalidaEhRecusada() {
        Vaga vaga = vaga("3000", 1);
        Candidato ana = inscrever(vaga, "Ana Souza", "2500");
        assertThatThrownBy(() -> recrutamento.mover(ana.getId(), Etapa.PROPOSTA, null))
                .isInstanceOf(TransicaoInvalidaException.class);
    }

    @Test
    void vagaEncerraSozinhaAoCompletarAsContratacoes() {
        Vaga vaga = vaga("3000", 1);
        Candidato ana = inscrever(vaga, "Ana Souza", "2500");
        Candidato bia = inscrever(vaga, "Beatriz Lima", "2600");
        recrutamento.selecionar(vaga.getId());
        recrutamento.registrarContato(ana.getId(), true);
        recrutamento.avaliar(ana.getId(), 4, "Boa entrevista técnica.");
        recrutamento.enviarProposta(ana.getId(), new BigDecimal("2500"));
        recrutamento.mover(ana.getId(), Etapa.CONTRATADO, null);

        RecrutamentoService.Funil funil = recrutamento.funil(vaga.getId());
        assertThat(funil.vaga().aberta()).isFalse();
        assertThat(funil.contratados()).isEqualTo(1);
        assertThatThrownBy(() -> recrutamento.mover(bia.getId(), Etapa.SELECIONADO, null))
                .hasMessage("A vaga \"Analista de Suporte\" está encerrada.");
        assertThatThrownBy(() -> inscrever(vaga, "Carlos Lima", "2000")).hasMessageContaining("está encerrada");
    }

    @Test
    void funilContaEtapasEQuemPedeAcimaDoOrcamento() {
        Vaga vaga = vaga("2000", 2);
        inscrever(vaga, "Felipe Moreira", "2194");
        inscrever(vaga, "Marcia Oliveira", "1813");
        inscrever(vaga, "Julia Cardoso", "2168");
        recrutamento.selecionar(vaga.getId());

        RecrutamentoService.Funil funil = recrutamento.funil(vaga.getId());
        assertThat(funil.total()).isEqualTo(3);
        assertThat(funil.porEtapa().get(Etapa.SELECIONADO)).isEqualTo(1);
        assertThat(funil.ocupadas()).isEqualTo(1);
        assertThat(funil.livres()).isEqualTo(1);
        assertThat(funil.acimaDoOrcamento()).isEqualTo(2);
    }

    @Test
    void importaCsvComLinhasValidasEInvalidas() {
        Vaga vaga = vaga("3000", 2);
        String csv = """
                nome;email;telefone;salario
                Ana Souza;ana@x.com;(27) 99999-0000;2.500,00
                Bruno Dias,bruno@x.com,,2800.50
                Carla 123;carla@x.com;;2000
                Diego Costa;diego@x.com;;abc
                só três;campos;aqui
                """;

        RecrutamentoService.Importacao r = recrutamento.importar(vaga.getId(), csv);

        assertThat(r.importados()).isEqualTo(2);
        assertThat(r.erros()).containsExactly(
                "Linha 4: O nome deve ter apenas letras.",
                "Linha 5: salário inválido (abc)",
                "Linha 6: use nome;e-mail;telefone;salário");
        assertThat(recrutamento.candidatosDa(vaga.getId())).extracting(Candidato::getSalarioPretendido)
                .containsExactly(new BigDecimal("2500.00"), new BigDecimal("2800.50"));
    }
}
