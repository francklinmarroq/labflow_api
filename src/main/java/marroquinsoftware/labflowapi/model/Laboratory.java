package marroquinsoftware.labflowapi.model;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;

@Entity
@Data
@NoArgsConstructor
@AllArgsConstructor
public class Laboratory {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @NotBlank
    private String name;

    private String rtn;

    private String phone;
    private String email;

    private String address1;
    private String address2;

    // CAI 1
    private String cai1;
    private LocalDate cai1ExpirationDate;
    private String cai1RangeFrom;
    private String cai1RangeTo;
    private String cai1CurrentNumber;

    // CAI 2 (de respaldo cuando CAI 1 vence o llega al límite)
    private String cai2;
    private LocalDate cai2ExpirationDate;
    private String cai2RangeFrom;
    private String cai2RangeTo;
    private String cai2CurrentNumber;
}
