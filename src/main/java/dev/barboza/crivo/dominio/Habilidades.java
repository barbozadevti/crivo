package dev.barboza.crivo.dominio;

import java.text.Normalizer;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Lista de habilidades guardada como texto ("Java, Spring Boot, SQL"), comparada sem acento e sem caixa. */
public final class Habilidades {

    public static final int MAXIMO = 12;

    private Habilidades() {
    }

    /** Limpa, remove repetidas (ignorando acento e caixa) e devolve no formato de gravação. */
    public static String normalizarTexto(String texto, String campo) {
        if (texto == null || texto.isBlank()) {
            return null;
        }
        Map<String, String> unicas = new LinkedHashMap<>();
        for (String parte : texto.split("[,;\\n]")) {
            String limpa = parte.trim().replaceAll("\\s+", " ");
            if (limpa.isEmpty()) {
                continue;
            }
            if (limpa.length() > 30) {
                throw new RegraVioladaException("Cada item de " + campo + " pode ter no máximo 30 caracteres.");
            }
            unicas.putIfAbsent(chave(limpa), limpa);
        }
        if (unicas.size() > MAXIMO) {
            throw new RegraVioladaException("Informe no máximo " + MAXIMO + " itens em " + campo + ".");
        }
        String resultado = String.join(", ", unicas.values());
        return resultado.isEmpty() ? null : resultado;
    }

    public static List<String> lista(String texto) {
        return texto == null ? List.of() : Arrays.stream(texto.split(",")).map(String::trim).filter(s -> !s.isEmpty()).toList();
    }

    /** "Comunicação" e "comunicacao" são a mesma habilidade. */
    public static String chave(String habilidade) {
        return Normalizer.normalize(habilidade.trim().toLowerCase(), Normalizer.Form.NFD).replaceAll("\\p{M}", "");
    }
}
