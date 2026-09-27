package dev.barboza.crivo.dominio;

import java.util.EnumSet;
import java.util.Set;

/**
 * Etapas do processo seletivo, como uma máquina de estados: cada etapa sabe para quais
 * pode ir. O {@code switch} é exaustivo, então uma etapa nova sem regra nem compila.
 */
public enum Etapa {

    INSCRITO("Inscritos"),
    SELECIONADO("Selecionados"),
    ENTREVISTA("Entrevista"),
    PROPOSTA("Proposta"),
    CONTRATADO("Contratados"),
    SEM_CONTATO("Sem contato"),
    REPROVADO("Reprovados"),
    DESISTIU("Desistências");

    private final String nome;

    Etapa(String nome) {
        this.nome = nome;
    }

    public String nome() {
        return nome;
    }

    /** Destinos permitidos a partir desta etapa. */
    public Set<Etapa> proximas() {
        return switch (this) {
            case INSCRITO -> EnumSet.of(SELECIONADO, REPROVADO, DESISTIU);
            case SELECIONADO -> EnumSet.of(ENTREVISTA, SEM_CONTATO, REPROVADO, DESISTIU);
            case ENTREVISTA -> EnumSet.of(PROPOSTA, REPROVADO, DESISTIU);
            case PROPOSTA -> EnumSet.of(CONTRATADO, REPROVADO, DESISTIU);
            case CONTRATADO, SEM_CONTATO, REPROVADO, DESISTIU -> EnumSet.noneOf(Etapa.class);
        };
    }

    public boolean podeIrPara(Etapa destino) {
        return proximas().contains(destino);
    }

    /** Candidato nestas etapas ocupa uma das vagas (conta para o limite). */
    public boolean ocupaVaga() {
        return this == SELECIONADO || this == ENTREVISTA || this == PROPOSTA || this == CONTRATADO;
    }

    public boolean encerrada() {
        return proximas().isEmpty();
    }
}
