package marroquinsoftware.labflowapi.payload;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class LaboratoryDTO {

    private Long id;

    @NotBlank(message = "El nombre del laboratorio es obligatorio")
    private String name;

    private String rtn;
    private String phone;
    private String email;
    private String address1;
    private String address2;

    // Rótulos y registros fiscales que salen en la factura impresa.
    private String invoiceHeadline;
    private String invoiceFooterNote;
    private String pacNumber;
    private String regExonerado;
    private String regSag;
    private String ordenCompraExenta;

    // Solo de lectura: la API la firma en cada respuesta y vence a la hora. No se
    // guarda en la base ni se acepta del cliente; el logo se cambia por
    // POST /laboratory/logo.
    private String logoUrl;

    // Sello del regente, mismo esquema de solo lectura que logoUrl. Se cambia por
    // POST /laboratory/stamp y se imprime en la firma del reporte de resultados.
    private String stampUrl;

    private String cai1;
    private LocalDate cai1ExpirationDate;
    private String cai1RangeFrom;
    private String cai1RangeTo;
    private String cai1CurrentNumber;

    private String cai2;
    private LocalDate cai2ExpirationDate;
    private String cai2RangeFrom;
    private String cai2RangeTo;
    private String cai2CurrentNumber;

    // Descuentos por edad aplicados automáticamente en las cotizaciones.
    @Min(value = 0, message = "La edad de la tercera edad no puede ser negativa")
    @Max(value = 130, message = "La edad de la tercera edad no es válida")
    private Integer thirdAgeMinYears;
    @DecimalMin(value = "0.0", message = "El descuento de tercera edad no puede ser negativo")
    @DecimalMax(value = "100.0", message = "El descuento de tercera edad no puede pasar de 100%")
    private BigDecimal thirdAgeDiscountPercent;

    @Min(value = 0, message = "La edad de la cuarta edad no puede ser negativa")
    @Max(value = 130, message = "La edad de la cuarta edad no es válida")
    private Integer fourthAgeMinYears;
    @DecimalMin(value = "0.0", message = "El descuento de cuarta edad no puede ser negativo")
    @DecimalMax(value = "100.0", message = "El descuento de cuarta edad no puede pasar de 100%")
    private BigDecimal fourthAgeDiscountPercent;
}
