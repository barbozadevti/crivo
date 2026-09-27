package dev.barboza.crivo.dominio;

import java.math.BigDecimal;

/**
 * Triagem pelo salário pretendido em relação ao orçamento da vaga
 * (a regra do processo seletivo das aulas de controle de fluxo).
 */
public enum Recomendacao {

    LIGAR("Ligar para o candidato"),
    CONTRAPROPOSTA("Ligar com contraproposta"),
    AGUARDAR("Aguardar os demais candidatos");

    private final String texto;

    Recomendacao(String texto) {
        this.texto = texto;
    }

    public String texto() {
        return texto;
    }

    public static Recomendacao para(BigDecimal salarioBase, BigDecimal pretendido) {
        int comparacao = salarioBase.compareTo(pretendido);
        if (comparacao > 0) {
            return LIGAR;
        } else if (comparacao == 0) {
            return CONTRAPROPOSTA;
        } else {
            return AGUARDAR;
        }
    }

    /** Só quem cabe no orçamento (pretende até o salário base) pode ser selecionado. */
    public boolean cabeNoOrcamento() {
        return this != AGUARDAR;
    }
}
