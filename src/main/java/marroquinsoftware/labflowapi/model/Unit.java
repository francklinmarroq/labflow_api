package marroquinsoftware.labflowapi.model;

import lombok.AllArgsConstructor;

@AllArgsConstructor
public class Unit {
    private Long unitId;
    private String unitSymbol;

    public Long getUnitId() {
        return unitId;
    }

    public void setUnitId(Long unitId) {
        this.unitId = unitId;
    }

    public String getUnitSymbol() {
        return unitSymbol;
    }

    public void setUnitSymbol(String unitSymbol) {
        this.unitSymbol = unitSymbol;
    }
}
