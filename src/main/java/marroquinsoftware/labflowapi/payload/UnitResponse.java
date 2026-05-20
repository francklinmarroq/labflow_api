package marroquinsoftware.labflowapi.payload;


import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class UnitResponse {
    private List<UnitDTO> content;
}
