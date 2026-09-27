package dev.barboza.crivo.dominio;

/** Regra de negócio violada (orçamento, limite de vagas, dados inválidos). Vira HTTP 422. */
public class RegraVioladaException extends RuntimeException {

    public RegraVioladaException(String mensagem) {
        super(mensagem);
    }
}
