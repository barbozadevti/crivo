package dev.barboza.crivo.dominio;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Quanto o candidato combina com a vaga, de 0 a 100: 80 pontos pelos requisitos que ele atende
 * e 20 pelo encaixe no orçamento (no orçamento 20, no limite 15, acima 0). Sem requisitos
 * cadastrados, só o orçamento conta.
 */
public record Compatibilidade(int pontuacao, List<String> atende, List<String> faltam) {

    public static Compatibilidade de(Vaga vaga, Candidato candidato) {
        List<String> requisitos = Habilidades.lista(vaga.getRequisitos());
        Set<String> doCandidato = Habilidades.lista(candidato.getHabilidades()).stream()
                .map(Habilidades::chave).collect(Collectors.toSet());
        List<String> atende = new ArrayList<>();
        List<String> faltam = new ArrayList<>();
        for (String requisito : requisitos) {
            (doCandidato.contains(Habilidades.chave(requisito)) ? atende : faltam).add(requisito);
        }
        double orcamento = switch (candidato.recomendacao()) {
            case LIGAR -> 1.0;
            case CONTRAPROPOSTA -> 0.75;
            case AGUARDAR -> 0.0;
        };
        double pontos = requisitos.isEmpty()
                ? 30 + 70 * orcamento
                : 80.0 * atende.size() / requisitos.size() + 20 * orcamento;
        return new Compatibilidade((int) Math.round(pontos), atende, faltam);
    }
}
