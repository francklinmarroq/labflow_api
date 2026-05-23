package marroquinsoftware.labflowapi.payload;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class CustomerDTO {
    private Long id;
    @NotBlank
    private String name;
    private Integer ageInDays;
    private String nationalIdNumber;
    private String taxNumber;
    private String phone;
    private String email;
    private List<Long> pathologyIds;
}
