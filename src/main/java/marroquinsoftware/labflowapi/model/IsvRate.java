package marroquinsoftware.labflowapi.model;

import java.math.BigDecimal;

/** Tasa de ISV de una línea de compra, según la factura del proveedor. */
public enum IsvRate {

    EXENTO("Exento", BigDecimal.ZERO),
    GRAVADO_15("Gravado 15%", new BigDecimal("0.15")),
    GRAVADO_18("Gravado 18%", new BigDecimal("0.18"));

    private final String label;
    private final BigDecimal rate;

    IsvRate(String label, BigDecimal rate) {
        this.label = label;
        this.rate = rate;
    }

    public String getLabel() {
        return label;
    }

    public BigDecimal getRate() {
        return rate;
    }
}
