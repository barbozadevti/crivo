package dev.barboza.crivo;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.MethodSource;

import dev.barboza.crivo.dominio.Candidato;
import dev.barboza.crivo.dominio.Etapa;
import dev.barboza.crivo.dominio.Recomendacao;
import dev.barboza.crivo.dominio.RegraVioladaException;
import dev.barboza.crivo.dominio.TransicaoInvalidaException;
import dev.barboza.crivo.dominio.Vaga;

/** Regras puras, sem banco: triagem, máquina de estados e validações. */
class RegrasTest {

    private static final Instant AGORA = Instant.parse("2026-09-27T15:00:00Z");

    /** A regra das aulas, com salário base de R$ 2.000,00. */
    @ParameterizedTest
    @CsvSource({
            "1900.00, LIGAR",
            "1999.99, LIGAR",
            "2000.00, CONTRAPROPOSTA",
            "2000, CONTRAPROPOSTA",
            "2000.01, AGUARDAR",
            "2200.00, AGUARDAR"})
    void triagemPeloSalarioPretendido(String pretendido, Recomendacao esperada) {
        assertThat(Recomendacao.para(new BigDecimal("2000.00"), new BigDecimal(pretendido))).isEqualTo(esperada);
    }

    /**
     * Tabela escrita à parte (não derivada do código): de cada etapa, para onde se pode ir.
     * O teste confere as 64 combinações.
     */
    private static final Map<Etapa, Set<Etapa>> PERMITIDAS = Map.of(
            Etapa.INSCRITO, Set.of(Etapa.SELECIONADO, Etapa.REPROVADO, Etapa.DESISTIU),
            Etapa.SELECIONADO, Set.of(Etapa.ENTREVISTA, Etapa.SEM_CONTATO, Etapa.REPROVADO, Etapa.DESISTIU),
            Etapa.ENTREVISTA, Set.of(Etapa.PROPOSTA, Etapa.REPROVADO, Etapa.DESISTIU),
            Etapa.PROPOSTA, Set.of(Etapa.CONTRATADO, Etapa.REPROVADO, Etapa.DESISTIU),
            Etapa.CONTRATADO, Set.of(),
            Etapa.SEM_CONTATO, Set.of(),
            Etapa.REPROVADO, Set.of(),
            Etapa.DESISTIU, Set.of());

    static Stream<Arguments> todasAsCombinacoes() {
        return Stream.of(Etapa.values()).flatMap(de -> Stream.of(Etapa.values()).map(para -> Arguments.of(de, para)));
    }

    @ParameterizedTest(name = "{0} -> {1}")
    @MethodSource("todasAsCombinacoes")
    void maquinaDeEstados(Etapa de, Etapa para) {
        assertThat(de.podeIrPara(para)).isEqualTo(PERMITIDAS.get(de).contains(para));
    }

    @Test
    void quemOcupaVagaEQuemEncerrou() {
        assertThat(Stream.of(Etapa.values()).filter(Etapa::ocupaVaga))
                .containsExactly(Etapa.SELECIONADO, Etapa.ENTREVISTA, Etapa.PROPOSTA, Etapa.CONTRATADO);
        assertThat(Stream.of(Etapa.values()).filter(Etapa::encerrada))
                .containsExactly(Etapa.CONTRATADO, Etapa.SEM_CONTATO, Etapa.REPROVADO, Etapa.DESISTIU);
    }

    @Test
    void candidatoSoMudaPorTransicaoPermitidaEDentroDoOrcamento() {
        Vaga vaga = new Vaga("Analista", "TI", null, new BigDecimal("3000"), 1, AGORA);
        Candidato caro = new Candidato(vaga, "Ana Souza", "ana@x.com", null, new BigDecimal("3500"), AGORA);
        Candidato ok = new Candidato(vaga, "Joao Silva", "joao@x.com", null, new BigDecimal("3000"), AGORA);

        assertThatThrownBy(() -> caro.mover(Etapa.SELECIONADO, null, AGORA))
                .isInstanceOf(RegraVioladaException.class)
                .hasMessage("Ana Souza pretende acima do orçamento da vaga e não pode ser selecionado.");
        assertThatThrownBy(() -> ok.mover(Etapa.CONTRATADO, null, AGORA))
                .isInstanceOf(TransicaoInvalidaException.class)
                .hasMessage("Não é possível mover de \"Inscritos\" para \"Contratados\".");

        ok.mover(Etapa.SELECIONADO, null, AGORA);
        ok.mover(Etapa.REPROVADO, null, AGORA);
        assertThatThrownBy(() -> ok.mover(Etapa.ENTREVISTA, null, AGORA))
                .hasMessage("Não é possível mover de \"Reprovados\" para \"Entrevista\". O processo deste candidato já foi encerrado.");
    }

    @Test
    void validacoesDeCadastro() {
        assertThatThrownBy(() -> new Vaga("", "TI", null, new BigDecimal("1"), 1, AGORA)).hasMessageContaining("o título");
        assertThatThrownBy(() -> new Vaga("Analista", "TI", null, BigDecimal.ZERO, 1, AGORA))
                .hasMessage("O salário base deve ser maior que zero.");
        assertThatThrownBy(() -> new Vaga("Analista", "TI", null, new BigDecimal("1"), 0, AGORA))
                .hasMessage("A quantidade de vagas deve ser de 1 a 50.");
        Vaga vaga = new Vaga("Analista", "TI", null, new BigDecimal("3000"), 1, AGORA);
        assertThatThrownBy(() -> new Candidato(vaga, "Ana 123", "a@x.com", null, BigDecimal.TEN, AGORA))
                .hasMessage("O nome deve ter apenas letras.");
        assertThatThrownBy(() -> new Candidato(vaga, "Ana Souza", "ana@", null, BigDecimal.TEN, AGORA))
                .hasMessage("E-mail inválido: ana@.");
        assertThatThrownBy(() -> new Candidato(vaga, "Ana Souza", "a@x.com", null, new BigDecimal("10.001"), AGORA))
                .hasMessage("O salário pretendido pode ter no máximo 2 casas decimais.");
        vaga.encerrar(AGORA);
        assertThatThrownBy(() -> new Candidato(vaga, "Ana Souza", "a@x.com", null, BigDecimal.TEN, AGORA))
                .hasMessage("A vaga \"Analista\" está encerrada.");
    }
}
