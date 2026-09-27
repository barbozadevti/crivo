package dev.barboza.crivo.dominio;

/** Mudança de etapa que a máquina de estados não permite. Vira HTTP 409. */
public class TransicaoInvalidaException extends RuntimeException {

    public TransicaoInvalidaException(Etapa de, Etapa para) {
        super("Não é possível mover de \"" + de.nome() + "\" para \"" + para.nome() + "\"."
                + (de.encerrada() ? " O processo deste candidato já foi encerrado." : ""));
    }
}
