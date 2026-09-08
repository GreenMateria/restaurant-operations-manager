package ca.foodinventory.model;

public class AlcoholVarianceRow {

    private final int productId;
    private final String product;
    private final String category;
    private final String unit;
    private final double actualUsage;
    private final double soldUsage;
    private final double variance;
    private final double varianceDollars;

    public AlcoholVarianceRow(
            int productId,
            String product,
            String category,
            String unit,
            double actualUsage,
            double soldUsage,
            double variance,
            double varianceDollars
    ) {
        this.productId = productId;
        this.product = product;
        this.category = category;
        this.unit = unit;
        this.actualUsage = actualUsage;
        this.soldUsage = soldUsage;
        this.variance = variance;
        this.varianceDollars = varianceDollars;
    }

    public int getProductId() {
        return productId;
    }

    public String getProduct() {
        return product;
    }

    public String getCategory() {
        return category;
    }

    public String getUnit() {
        return unit;
    }

    public double getActualUsage() {
        return actualUsage;
    }

    public double getSoldUsage() {
        return soldUsage;
    }

    public double getVariance() {
        return variance;
    }

    public double getVarianceDollars() {
        return varianceDollars;
    }
}
