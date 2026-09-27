package dev.barboza.crivo.dominio;

/**
 * Desfecho de uma tentativa de ligação. Hierarquia selada: quem trata o resultado com
 * {@code switch} é obrigado a considerar os três casos.
 */
public sealed interface ResultadoDoContato {

    Candidato candidato();

    /** Atendeu: o candidato segue para a entrevista. */
    record Atendeu(Candidato candidato, int tentativa) implements ResultadoDoContato {
    }

    /** Não atendeu, mas ainda há tentativas. */
    record NaoAtendeu(Candidato candidato, int tentativa, int restantes) implements ResultadoDoContato {
    }

    /** Terceira tentativa sem sucesso: sai do processo e o próximo da fila é chamado (se houver). */
    record SemContato(Candidato candidato, Candidato suplente) implements ResultadoDoContato {
    }
}
