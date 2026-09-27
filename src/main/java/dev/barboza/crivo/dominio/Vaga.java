package dev.barboza.crivo.dominio;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

@Entity
@Table(name = "vaga")
public class Vaga {

    public enum Status { ABERTA, ENCERRADA }

    public static final int MAXIMO_DE_VAGAS = 50;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 80)
    private String titulo;

    @Column(nullable = false, length = 40)
    private String area;

    @Column(length = 500)
    private String descricao;

    /** Orçamento: o maior salário que a empresa aceita pagar. */
    @Column(name = "salario_base", nullable = false, precision = 12, scale = 2)
    private BigDecimal salarioBase;

    /** Quantas pessoas serão contratadas. */
    @Column(nullable = false)
    private int quantidade;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private Status status;

    @Column(name = "criada_em", nullable = false)
    private Instant criadaEm;

    @Column(name = "encerrada_em")
    private Instant encerradaEm;

    @Version
    private long versao;

    protected Vaga() {
    }

    public Vaga(String titulo, String area, String descricao, BigDecimal salarioBase, int quantidade, Instant criadaEm) {
        this.titulo = texto(titulo, "o título", 3, 80);
        this.area = texto(area, "a área", 2, 40);
        this.descricao = descricao == null || descricao.isBlank() ? null : texto(descricao, "a descrição", 1, 500);
        this.salarioBase = validarSalario(salarioBase, "O salário base");
        if (quantidade < 1 || quantidade > MAXIMO_DE_VAGAS) {
            throw new RegraVioladaException("A quantidade de vagas deve ser de 1 a " + MAXIMO_DE_VAGAS + ".");
        }
        this.quantidade = quantidade;
        this.status = Status.ABERTA;
        this.criadaEm = criadaEm;
    }

    public void exigirAberta() {
        if (status == Status.ENCERRADA) {
            throw new RegraVioladaException("A vaga \"" + titulo + "\" está encerrada.");
        }
    }

    public void encerrar(Instant quando) {
        exigirAberta();
        status = Status.ENCERRADA;
        encerradaEm = quando;
    }

    public boolean aberta() {
        return status == Status.ABERTA;
    }

    static BigDecimal validarSalario(BigDecimal valor, String campo) {
        if (valor == null || valor.signum() <= 0) {
            throw new RegraVioladaException(campo + " deve ser maior que zero.");
        }
        if (valor.compareTo(new BigDecimal("1000000")) > 0) {
            throw new RegraVioladaException(campo + " parece alto demais: confira o valor.");
        }
        if (valor.stripTrailingZeros().scale() > 2) {
            throw new RegraVioladaException(campo + " pode ter no máximo 2 casas decimais.");
        }
        return valor.setScale(2, RoundingMode.UNNECESSARY);
    }

    static String texto(String valor, String campo, int minimo, int maximo) {
        String limpo = valor == null ? "" : valor.trim().replaceAll("\\s+", " ");
        if (limpo.length() < minimo || limpo.length() > maximo) {
            throw new RegraVioladaException("Informe " + campo + " (de " + minimo + " a " + maximo + " caracteres).");
        }
        return limpo;
    }

    public Long getId() {
        return id;
    }

    public String getTitulo() {
        return titulo;
    }

    public String getArea() {
        return area;
    }

    public String getDescricao() {
        return descricao;
    }

    public BigDecimal getSalarioBase() {
        return salarioBase;
    }

    public int getQuantidade() {
        return quantidade;
    }

    public Status getStatus() {
        return status;
    }

    public Instant getCriadaEm() {
        return criadaEm;
    }

    public Instant getEncerradaEm() {
        return encerradaEm;
    }
}
