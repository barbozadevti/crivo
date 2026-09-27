package dev.barboza.crivo.dominio;

import java.math.BigDecimal;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.Locale;

/** Valores em reais no formato brasileiro: R$ 1.234,56. */
public final class Dinheiro {

    private Dinheiro() {
    }

    public static String formatar(BigDecimal valor) {
        return "R$ " + new DecimalFormat("#,##0.00", DecimalFormatSymbols.getInstance(Locale.of("pt", "BR"))).format(valor);
    }
}
