package dev.barboza.crivo.dominio;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.regex.Pattern;

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
import jakarta.persistence.Version;

/**
 * Candidato a uma vaga. A etapa só muda por {@link #mover}, que consulta a máquina de estados
 * de {@link Etapa}, e cada mudança gera um {@link Evento} para o histórico.
 */
@Entity
@Table(name = "candidato")
public class Candidato {

    public static final int MAXIMO_DE_TENTATIVAS = 3;

    private static final Pattern EMAIL = Pattern.compile("^[\\w.+-]+@[\\w-]+(\\.[\\w-]+)+$");

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.EAGER, optional = false)
    @JoinColumn(name = "vaga_id")
    private Vaga vaga;

    @Column(nullable = false, length = 80)
    private String nome;

    @Column(nullable = false, length = 120)
    private String email;

    @Column(length = 20)
    private String telefone;

    @Column(name = "salario_pretendido", nullable = false, precision = 12, scale = 2)
    private BigDecimal salarioPretendido;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 15)
    private Etapa etapa;

    @Column(name = "tentativas_contato", nullable = false)
    private int tentativasDeContato;

    @Column(name = "inscrito_em", nullable = false)
    private Instant inscritoEm;

    @Column(name = "atualizado_em", nullable = false)
    private Instant atualizadoEm;

    @Version
    private long versao;

    protected Candidato() {
    }

    public Candidato(Vaga vaga, String nome, String email, String telefone, BigDecimal salarioPretendido, Instant quando) {
        vaga.exigirAberta();
        this.vaga = vaga;
        this.nome = validarNome(nome);
        this.email = validarEmail(email);
        this.telefone = telefone == null || telefone.isBlank() ? null : Vaga.texto(telefone, "o telefone", 8, 20);
        this.salarioPretendido = Vaga.validarSalario(salarioPretendido, "O salário pretendido");
        this.etapa = Etapa.INSCRITO;
        this.inscritoEm = quando;
        this.atualizadoEm = quando;
    }

    /** Triagem automática: calculada na hora, pois o orçamento da vaga pode mudar. */
    public Recomendacao recomendacao() {
        return Recomendacao.para(vaga.getSalarioBase(), salarioPretendido);
    }

    /** Muda de etapa se a máquina de estados permitir; devolve o evento para o histórico. */
    public Evento mover(Etapa destino, String motivo, Instant quando) {
        if (!etapa.podeIrPara(destino)) {
            throw new TransicaoInvalidaException(etapa, destino);
        }
        if (destino == Etapa.SELECIONADO && !recomendacao().cabeNoOrcamento()) {
            throw new RegraVioladaException(nome + " pretende acima do orçamento da vaga e não pode ser selecionado.");
        }
        Etapa anterior = etapa;
        etapa = destino;
        atualizadoEm = quando;
        return Evento.mudanca(this, anterior, destino, motivo, quando);
    }

    /** Registra uma tentativa de ligação sem sucesso; devolve quantas já foram feitas. */
    public int registrarTentativaSemSucesso(Instant quando) {
        exigirSelecionado();
        tentativasDeContato++;
        atualizadoEm = quando;
        return tentativasDeContato;
    }

    /** Ligações só fazem sentido para quem foi selecionado e ainda não foi contatado. */
    public void exigirSelecionado() {
        if (etapa != Etapa.SELECIONADO) {
            throw new RegraVioladaException("Só é possível registrar contato com candidatos selecionados.");
        }
    }

    public int tentativaAtual() {
        return tentativasDeContato + 1;
    }

    private static String validarNome(String nome) {
        String limpo = Vaga.texto(nome, "o nome", 3, 80);
        if (!limpo.matches("[\\p{L} .'-]+")) {
            throw new RegraVioladaException("O nome deve ter apenas letras.");
        }
        return limpo;
    }

    private static String validarEmail(String email) {
        String limpo = email == null ? "" : email.trim().toLowerCase();
        if (limpo.length() > 120 || !EMAIL.matcher(limpo).matches()) {
            throw new RegraVioladaException("E-mail inválido: " + (email == null ? "" : email.trim()) + ".");
        }
        return limpo;
    }

    public Long getId() {
        return id;
    }

    public Vaga getVaga() {
        return vaga;
    }

    public String getNome() {
        return nome;
    }

    public String getEmail() {
        return email;
    }

    public String getTelefone() {
        return telefone;
    }

    public BigDecimal getSalarioPretendido() {
        return salarioPretendido;
    }

    public Etapa getEtapa() {
        return etapa;
    }

    public int getTentativasDeContato() {
        return tentativasDeContato;
    }

    public Instant getInscritoEm() {
        return inscritoEm;
    }

    public Instant getAtualizadoEm() {
        return atualizadoEm;
    }
}
