package marroquinsoftware.labflowapi.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.ToString;

@Entity
@Table(name = "test_run_attachments")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class TestRunAttachment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne
    @JoinColumn(name = "test_run_id", nullable = false)
    @ToString.Exclude
    @EqualsAndHashCode.Exclude
    private TestRun testRun;

    // Llave dentro del bucket privado (no una URL): igual que el logo/sello del
    // laboratorio, cada lectura firma una URL temporal al vuelo.
    @Column(name = "object_key", nullable = false)
    private String objectKey;

    @Column(name = "content_type")
    private String contentType;

    // Orden en que se subieron/se muestran las imágenes de la corrida.
    @Column(name = "display_order")
    private Integer displayOrder;
}
