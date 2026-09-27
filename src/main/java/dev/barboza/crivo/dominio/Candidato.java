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
 * Candidato a uma vaga. A etapa só muda por {@link #mover} (ou {@link #enviarProposta}), que
 * consultam a máquina de estados de {@link Etapa}, e cada mudança gera um {@link Evento}.
 */
@Entity
@Table(name = "candidato")
public class Candidato {

    public static final int MAXIMO_DE_TENTATIVAS = 3;
    public static final int NOTA_MINIMA_PARA_PROPOSTA = 3;

    public enum Origem {
        CARREIRAS("Página de carreiras"), LINKEDIN("LinkedIn"), INDICACAO("Indicação"), IMPORTACAO("Importação"),
        MANUAL("Cadastro manual");

        private final String nome;

        Origem(String nome) {
            this.nome = nome;
        }

        public String nome() {
            return nome;
        }
    }

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

    @Column(length = 200)
    private String linkedin;

    @Column(name = "salario_pretendido", nullable = false, precision = 12, scale = 2)
    private BigDecimal salarioPretendido;

    @Column(length = 300)
    private String habilidades;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 12)
    private Origem origem;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 15)
    private Etapa etapa;

    @Column(name = "tentativas_contato", nullable = false)
    private int tentativasDeContato;

    /** Avaliação da entrevista, de 1 a 5. */
    @Column(name = "nota_entrevista")
    private Integer notaEntrevista;

    @Column(length = 500)
    private String parecer;

    @Column(name = "salario_ofertado", precision = 12, scale = 2)
    private BigDecimal salarioOfertado;

    @Column(name = "inscrito_em", nullable = false)
    private Instant inscritoEm;

    @Column(name = "atualizado_em", nullable = false)
    private Instant atualizadoEm;

    @Version
    private long versao;

    protected Candidato() {
    }

    public Candidato(Vaga vaga, String nome, String email, String telefone, BigDecimal salarioPretendido, Instant quando) {
        this(vaga, nome, email, telefone, salarioPretendido, null, null, Origem.MANUAL, quando);
    }

    public Candidato(Vaga vaga, String nome, String email, String telefone, BigDecimal salarioPretendido, String habilidades,
            String linkedin, Origem origem, Instant quando) {
        vaga.exigirAberta();
        this.vaga = vaga;
        this.nome = validarNome(nome);
        this.email = validarEmail(email);
        this.telefone = telefone == null || telefone.isBlank() ? null : Vaga.texto(telefone, "o telefone", 8, 20);
        this.linkedin = validarLinkedin(linkedin);
        this.salarioPretendido = Vaga.validarSalario(salarioPretendido, "O salário pretendido");
        this.habilidades = Habilidades.normalizarTexto(habilidades, "habilidades");
        this.origem = origem == null ? Origem.MANUAL : origem;
        this.etapa = Etapa.INSCRITO;
        this.inscritoEm = quando;
        this.atualizadoEm = quando;
    }

    /** Triagem automática: calculada na hora, pois o orçamento da vaga pode mudar. */
    public Recomendacao recomendacao() {
        return Recomendacao.para(vaga.getSalarioBase(), salarioPretendido);
    }

    public Compatibilidade compatibilidade() {
        return Compatibilidade.de(vaga, this);
    }

    /** Muda de etapa se a máquina de estados permitir; devolve o evento para o histórico. */
    public Evento mover(Etapa destino, String motivo, Instant quando) {
        if (!etapa.podeIrPara(destino)) {
            throw new TransicaoInvalidaException(etapa, destino);
        }
        if (destino == Etapa.PROPOSTA) {
            throw new RegraVioladaException("Para ir para Proposta, envie a proposta com o valor oferecido.");
        }
        return mudarPara(destino, motivo, quando);
    }

    /** Avaliação da entrevista (nota de 1 a 5 e parecer): condição para enviar proposta. */
    public Evento avaliar(int nota, String parecer, Instant quando) {
        if (etapa != Etapa.ENTREVISTA) {
            throw new RegraVioladaException("A avaliação é registrada depois da entrevista.");
        }
        if (nota < 1 || nota > 5) {
            throw new RegraVioladaException("A nota da entrevista vai de 1 a 5.");
        }
        String texto = parecer == null ? "" : parecer.trim().replaceAll("\\s+", " ");
        if (texto.length() < 10 || texto.length() > 500) {
            throw new RegraVioladaException("Escreva um parecer de 10 a 500 caracteres.");
        }
        notaEntrevista = nota;
        this.parecer = texto;
        atualizadoEm = quando;
        return Evento.avaliacao(this, "Entrevista avaliada com nota " + nota + "/5: " + texto, quando);
    }

    /**
     * Envia a proposta: exige avaliação com nota mínima e valor dentro do orçamento. Se o valor
     * ficar abaixo da pretensão, fica registrado como contraproposta.
     */
    public Evento enviarProposta(BigDecimal valor, Instant quando) {
        if (etapa != Etapa.ENTREVISTA) {
            throw new TransicaoInvalidaException(etapa, Etapa.PROPOSTA);
        }
        if (notaEntrevista == null) {
            throw new RegraVioladaException("Registre a avaliação da entrevista antes de enviar a proposta.");
        }
        if (notaEntrevista < NOTA_MINIMA_PARA_PROPOSTA) {
            throw new RegraVioladaException("A entrevista teve nota " + notaEntrevista + "/5; a proposta exige pelo menos "
                    + NOTA_MINIMA_PARA_PROPOSTA + ".");
        }
        BigDecimal oferta = Vaga.validarSalario(valor, "O valor da proposta");
        if (oferta.compareTo(vaga.getSalarioBase()) > 0) {
            throw new RegraVioladaException("A proposta passa do orçamento da vaga.");
        }
        salarioOfertado = oferta;
        boolean contraproposta = oferta.compareTo(salarioPretendido) < 0;
        return mudarPara(Etapa.PROPOSTA, "proposta de " + Dinheiro.formatar(oferta)
                + (contraproposta ? " (contraproposta: pretendia " + Dinheiro.formatar(salarioPretendido) + ")" : ""), quando);
    }

    private Evento mudarPara(Etapa destino, String motivo, Instant quando) {
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

    private static String validarLinkedin(String linkedin) {
        if (linkedin == null || linkedin.isBlank()) {
            return null;
        }
        String limpo = linkedin.trim();
        if (!limpo.matches("^(https?://)?(www\\.)?linkedin\\.com/in/[\\w-]{3,100}/?$")) {
            throw new RegraVioladaException("Use o endereço do perfil, por exemplo linkedin.com/in/seu-nome.");
        }
        return limpo.startsWith("http") ? limpo : "https://" + limpo;
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

    public String getLinkedin() {
        return linkedin;
    }

    public BigDecimal getSalarioPretendido() {
        return salarioPretendido;
    }

    public String getHabilidades() {
        return habilidades;
    }

    public Origem getOrigem() {
        return origem;
    }

    public Etapa getEtapa() {
        return etapa;
    }

    public int getTentativasDeContato() {
        return tentativasDeContato;
    }

    public Integer getNotaEntrevista() {
        return notaEntrevista;
    }

    public String getParecer() {
        return parecer;
    }

    public BigDecimal getSalarioOfertado() {
        return salarioOfertado;
    }

    public Instant getInscritoEm() {
        return inscritoEm;
    }

    public Instant getAtualizadoEm() {
        return atualizadoEm;
    }
}
