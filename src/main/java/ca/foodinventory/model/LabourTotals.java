package ca.foodinventory.model;

import java.math.BigDecimal;

public record LabourTotals(
        BigDecimal hours,
        BigDecimal labourDollars
) {

    public LabourTotals {
        hours = hours == null ? BigDecimal.ZERO : hours;
        labourDollars = labourDollars == null ? BigDecimal.ZERO : labourDollars;
    }

    public static LabourTotals zero() {
        return new LabourTotals(BigDecimal.ZERO, BigDecimal.ZERO);
    }

    public LabourTotals add(LabourTotals other) {
        if (other == null) {
            return this;
        }
        return new LabourTotals(
                hours.add(other.hours()),
                labourDollars.add(other.labourDollars())
        );
    }
}
