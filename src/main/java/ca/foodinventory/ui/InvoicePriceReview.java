package ca.foodinventory.ui;

import ca.foodinventory.dao.InvoiceDao;
import ca.foodinventory.database.DatabaseManager;
import ca.foodinventory.model.InvoiceLine;
import ca.foodinventory.model.PurchasePriceBaseline;
import ca.foodinventory.service.InvoiceApiClient;
import javafx.beans.property.SimpleStringProperty;
import javafx.scene.control.Alert;
import javafx.scene.control.ButtonType;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableCell;
import javafx.scene.control.TextArea;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Collectors;

final class InvoicePriceReview {
    private static final BigDecimal MIN_PERCENT = new BigDecimal("0.15");
    private static final BigDecimal MIN_LINE_IMPACT = new BigDecimal("2.00");

    private InvoicePriceReview() {
    }

    static List<Flag> findFlags(List<InvoiceLine> lines, String date, String supplier, String invoiceNumber) {
        List<String> skus = lines.stream().map(InvoiceLine::getSku).distinct().toList();
        List<PurchasePriceBaseline> history = DatabaseManager.isApiDatabase()
                ? new InvoiceApiClient().findPriceBaselines(skus, date, supplier, invoiceNumber)
                : new InvoiceDao().findPriceBaselines(skus, date, supplier, invoiceNumber);
        Map<String, PurchasePriceBaseline> bySku = history.stream()
                .collect(Collectors.toMap(PurchasePriceBaseline::sku, Function.identity()));
        List<Flag> flags = new ArrayList<>();
        for (InvoiceLine line : lines) {
            PurchasePriceBaseline baseline = bySku.get(line.getSku());
            if (baseline == null || baseline.baseQuantity().compareTo(BigDecimal.ZERO) <= 0
                    || baseline.totalCost().compareTo(BigDecimal.ZERO) <= 0
                    || line.getExtendedCost().compareTo(BigDecimal.ZERO) <= 0) continue;
            BigDecimal quantity = BigDecimal.valueOf(line.getCaseQty())
                    .multiply(baseline.conversionFactor())
                    .add(BigDecimal.valueOf(line.getSplitQty()));
            if (quantity.compareTo(BigDecimal.ZERO) <= 0) continue;
            BigDecimal currentUnit = line.getExtendedCost().divide(quantity, 6, RoundingMode.HALF_UP);
            BigDecimal previousUnit = baseline.unitCost();
            if (previousUnit.compareTo(BigDecimal.ZERO) <= 0) continue;
            BigDecimal increase = currentUnit.subtract(previousUnit);
            BigDecimal percent = increase.divide(previousUnit, 4, RoundingMode.HALF_UP);
            BigDecimal impact = increase.multiply(quantity);
            if (percent.compareTo(MIN_PERCENT) >= 0 && impact.compareTo(MIN_LINE_IMPACT) >= 0) {
                flags.add(new Flag(line, baseline, currentUnit, percent, impact));
            }
        }
        return flags;
    }

    static <T> TableColumn<T, String> markerColumn(Function<T, String> skuForRow,
                                                    Supplier<List<Flag>> flags) {
        TableColumn<T, String> column = new TableColumn<>("Price");
        column.setPrefWidth(120);
        column.setCellValueFactory(cell -> new SimpleStringProperty(
                flags.get().stream().anyMatch(flag -> flag.line().getSku().equals(skuForRow.apply(cell.getValue())))
                        ? "▲ Increase" : ""));
        column.setCellFactory(ignored -> new TableCell<>() {
            @Override
            protected void updateItem(String value, boolean empty) {
                super.updateItem(value, empty);
                setText(empty ? null : value);
                setStyle(empty || value == null || value.isBlank() ? "" :
                        "-fx-text-fill: #ffbf47; -fx-font-weight: bold;");
            }
        });
        return column;
    }

    static boolean confirmBeforeSave(List<Flag> flags) {
        if (flags.isEmpty()) return true;
        ButtonType review = new ButtonType("Review Items");
        ButtonType save = new ButtonType("Save Anyway");
        ButtonType cancel = new ButtonType("Cancel");
        Alert alert = new Alert(Alert.AlertType.WARNING);
        alert.setTitle("Price Increase Review");
        alert.setHeaderText(flags.size() + " item(s) have notable price increases");
        alert.setContentText("Review the flagged prices before saving this invoice.");
        alert.getButtonTypes().setAll(review, save, cancel);
        ButtonType result = alert.showAndWait().orElse(cancel);
        if (result == review) show(flags);
        return result == save;
    }

    static ReviewResult reviewBeforeSave(List<InvoiceLine> lines, String date,
                                         String supplier, String invoiceNumber) {
        try {
            List<Flag> flags = findFlags(lines, date, supplier, invoiceNumber);
            return new ReviewResult(confirmBeforeSave(flags), flags);
        } catch (RuntimeException e) {
            ButtonType continueButton = new ButtonType("Save Without Price Review");
            ButtonType cancelButton = new ButtonType("Cancel");
            Alert alert = new Alert(Alert.AlertType.WARNING);
            alert.setTitle("Price Review Unavailable");
            alert.setHeaderText("Could not check recent purchase prices");
            alert.setContentText(e.getMessage());
            alert.getButtonTypes().setAll(continueButton, cancelButton);
            return new ReviewResult(alert.showAndWait().orElse(cancelButton) == continueButton, List.of());
        }
    }

    static void show(List<Flag> flags) {
        if (flags.isEmpty()) return;
        StringBuilder details = new StringBuilder();
        for (Flag flag : flags) {
            BigDecimal equivalentFactor = flag.line().getCaseQty() > 0
                    ? flag.baseline().conversionFactor() : BigDecimal.ONE;
            String unit = flag.line().getCaseQty() > 0 ? "case" : "each";
            BigDecimal now = flag.currentUnit().multiply(equivalentFactor);
            BigDecimal before = flag.baseline().unitCost().multiply(equivalentFactor);
            details.append(flag.line().getDescription()).append("\n")
                    .append("  Now: $").append(money(now)).append('/').append(unit)
                    .append("   Previous: $").append(money(before)).append('/').append(unit)
                    .append("   Up ").append(flag.percent().multiply(BigDecimal.valueOf(100)).setScale(1, RoundingMode.HALF_UP))
                    .append("%\n  ")
                    .append(flag.baseline().recent() ? "Previous 4 weeks" : "Last earlier purchase")
                    .append(" · ").append(flag.baseline().purchaseCount()).append(" invoice(s)")
                    .append(" · about $").append(money(flag.impact())).append(" extra on this line\n\n");
        }
        Alert alert = new Alert(Alert.AlertType.INFORMATION, "", ButtonType.OK);
        alert.setTitle("Price Increase Review");
        alert.setHeaderText(flags.size() + " item(s) have notable price increases");
        alert.getDialogPane().setPrefWidth(650);
        TextArea detailArea = new TextArea(details.toString());
        detailArea.setEditable(false);
        detailArea.setWrapText(true);
        detailArea.setPrefRowCount(Math.min(14, Math.max(5, flags.size() * 4)));
        alert.getDialogPane().setContent(detailArea);
        alert.showAndWait();
    }

    private static String money(BigDecimal value) {
        return value.setScale(2, RoundingMode.HALF_UP).toPlainString();
    }

    record Flag(InvoiceLine line, PurchasePriceBaseline baseline, BigDecimal currentUnit,
                BigDecimal percent, BigDecimal impact) {
    }

    record ReviewResult(boolean proceed, List<Flag> flags) {
    }
}
