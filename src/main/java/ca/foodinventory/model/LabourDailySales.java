package ca.foodinventory.model;

import java.math.BigDecimal;
import java.time.LocalDate;

public class LabourDailySales {

    private int id;
    private LocalDate salesDate;
    private BigDecimal netSales = BigDecimal.ZERO;
    private BigDecimal tipOutPool = BigDecimal.ZERO;
    private boolean finalized;

    public int getId() {
        return id;
    }

    public void setId(int id) {
        this.id = id;
    }

    public LocalDate getSalesDate() {
        return salesDate;
    }

    public void setSalesDate(LocalDate salesDate) {
        this.salesDate = salesDate;
    }

    public BigDecimal getNetSales() {
        return netSales == null ? BigDecimal.ZERO : netSales;
    }

    public void setNetSales(BigDecimal netSales) {
        this.netSales = netSales == null ? BigDecimal.ZERO : netSales;
    }

    public BigDecimal getTipOutPool() {
        return tipOutPool == null ? BigDecimal.ZERO : tipOutPool;
    }

    public void setTipOutPool(BigDecimal tipOutPool) {
        this.tipOutPool = tipOutPool == null ? BigDecimal.ZERO : tipOutPool;
    }

    public boolean isFinalized() {
        return finalized;
    }

    public boolean getFinalized() {
        return finalized;
    }

    public void setFinalized(boolean finalized) {
        this.finalized = finalized;
    }
}
