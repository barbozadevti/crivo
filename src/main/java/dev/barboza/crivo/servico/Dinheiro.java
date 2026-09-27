package dev.barboza.crivo.servico;

import java.math.BigDecimal;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.Locale;

final class Dinheiro {

    private Dinheiro() {
    }

    static String formatar(BigDecimal valor) {
        return "R$ " + new DecimalFormat("#,##0.00", DecimalFormatSymbols.getInstance(Locale.of("pt", "BR"))).format(valor);
    }
}
