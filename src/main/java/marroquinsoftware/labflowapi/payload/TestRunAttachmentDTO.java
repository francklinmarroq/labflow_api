package marroquinsoftware.labflowapi.payload;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class TestRunAttachmentDTO {
    private Long id;
    // URL firmada de lectura, resuelta al vuelo; nunca se persiste.
    private String url;
    private String contentType;
    private Integer displayOrder;
}
