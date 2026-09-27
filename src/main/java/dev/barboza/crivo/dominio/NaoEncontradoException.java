package dev.barboza.crivo.dominio;

/** Vaga ou candidato inexistente. Vira HTTP 404. */
public class NaoEncontradoException extends RuntimeException {

    public NaoEncontradoException(String mensagem) {
        super(mensagem);
    }
}
