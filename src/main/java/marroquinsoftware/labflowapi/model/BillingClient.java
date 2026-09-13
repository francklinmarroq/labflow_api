package marroquinsoftware.labflowapi.model;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.TenantId;

/**
 * Cliente de facturación: la empresa, aseguradora o titular de convenio a cuyo
 * nombre se emite una factura. Es un catálogo aparte del padrón de pacientes
 * ({@link Customer}) a propósito: una empresa no tiene edad, sexo ni patologías,
 * no es sujeto de una orden y no debe aparecer nunca en un selector de pacientes.
 *
 * <p>El RTN es único por laboratorio: es con lo que el destinatario deduce el
 * gasto, y dos registros con el mismo RTN parten en dos el estado de cuenta.
 */
@Entity
@Table(name = "billing_clients", uniqueConstraints = @UniqueConstraint(
        name = "uk_billing_client_rtn_per_lab", columnNames = {"laboratory_id", "rtn"}))
@Data
@NoArgsConstructor
@AllArgsConstructor
public class BillingClient {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @TenantId
    @Column(name = "laboratory_id", updatable = false)
    private Long laboratoryId;

    /** Razón social con la que se imprime la factura. */
    @NotBlank
    @Column(nullable = false)
    private String name;

    /** RTN del cliente; obligatorio y único dentro del laboratorio. */
    @NotBlank
    @Column(nullable = false)
    private String rtn;

    private String phone;
    private String email;
    private String address;
}
