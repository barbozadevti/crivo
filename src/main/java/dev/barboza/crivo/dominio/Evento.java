package dev.barboza.crivo.dominio;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

/** Linha do histórico do candidato. Imutável. */
@Entity
@Table(name = "evento")
public class Evento {

    public enum Tipo { INSCRICAO, MUDANCA_DE_ETAPA, CONTATO, AVALIACAO, NOTA }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "candidato_id")
    private Candidato candidato;

    @Column(name = "data_hora", nullable = false)
    private Instant dataHora;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Tipo tipo;

    @Column(nullable = false, length = 500)
    private String descricao;

    @Enumerated(EnumType.STRING)
    @Column(name = "etapa_anterior", length = 15)
    private Etapa etapaAnterior;

    @Enumerated(EnumType.STRING)
    @Column(name = "etapa_nova", length = 15)
    private Etapa etapaNova;

    protected Evento() {
    }

    private Evento(Candidato candidato, Tipo tipo, String descricao, Etapa anterior, Etapa nova, Instant dataHora) {
        this.candidato = candidato;
        this.tipo = tipo;
        this.descricao = descricao.length() > 500 ? descricao.substring(0, 500) : descricao;
        this.etapaAnterior = anterior;
        this.etapaNova = nova;
        this.dataHora = dataHora;
    }

    public static Evento inscricao(Candidato c, Instant quando) {
        String origem = c.getOrigem() == Candidato.Origem.MANUAL ? " (cadastro manual)" : " via " + c.getOrigem().nome();
        return new Evento(c, Tipo.INSCRICAO, "Inscrição" + origem + ". Triagem: " + c.recomendacao().texto().toLowerCase() + ".",
                null, Etapa.INSCRITO, quando);
    }

    static Evento mudanca(Candidato c, Etapa de, Etapa para, String motivo, Instant quando) {
        String texto = de.nome() + " → " + para.nome() + (motivo == null || motivo.isBlank() ? "" : ": " + motivo);
        return new Evento(c, Tipo.MUDANCA_DE_ETAPA, texto, de, para, quando);
    }

    public static Evento contato(Candidato c, String descricao, Instant quando) {
        return new Evento(c, Tipo.CONTATO, descricao, null, null, quando);
    }

    static Evento avaliacao(Candidato c, String descricao, Instant quando) {
        return new Evento(c, Tipo.AVALIACAO, descricao, null, null, quando);
    }

    /** Anotação livre do recrutador ("pediu retorno na segunda", "boa referência"). */
    public static Evento nota(Candidato c, String texto, Instant quando) {
        String limpo = texto == null ? "" : texto.trim().replaceAll("\\s+", " ");
        if (limpo.length() < 2 || limpo.length() > 500) {
            throw new RegraVioladaException("A nota deve ter de 2 a 500 caracteres.");
        }
        return new Evento(c, Tipo.NOTA, limpo, null, null, quando);
    }

    public Candidato getCandidato() {
        return candidato;
    }

    public Long getId() {
        return id;
    }

    public Instant getDataHora() {
        return dataHora;
    }

    public Tipo getTipo() {
        return tipo;
    }

    public String getDescricao() {
        return descricao;
    }

    public Etapa getEtapaAnterior() {
        return etapaAnterior;
    }

    public Etapa getEtapaNova() {
        return etapaNova;
    }
}
