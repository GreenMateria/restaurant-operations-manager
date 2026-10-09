package ca.foodinventory.service;

import ca.foodinventory.model.InvoiceLine;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.nio.charset.StandardCharsets;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;

public class GfsCsvImportService {

    public InvoiceImportResult readInvoice(File file) {
        List<InvoiceLine> lines = new ArrayList<>();

        String invoiceNumber = "";
        String invoiceDate = "";
        BigDecimal invoiceTotal = BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);

        try (BufferedReader reader = new BufferedReader(new FileReader(file, StandardCharsets.UTF_8))) {
            String header = reader.readLine();
            if (header == null) {
                throw new IllegalArgumentException("The invoice CSV is empty.");
            }
            List<String> headers = parseCsvLine(header);
            String[] required = {"Item Description", "Current Quantity", "Unit Price", "Line Total",
                    "Unit of Measure", "Split Item Indicator", "Item Code"};
            for (String name : required) {
                if (!headers.contains(name)) {
                    throw new IllegalArgumentException("Choose the finalized invoice LineItemList CSV. Missing column: " + name);
                }
            }
            String row;
            int rowNumber = 1;

            while ((row = reader.readLine()) != null) {
                rowNumber++;
                if (row.isBlank()) {
                    continue;
                }
                List<String> columns = parseCsvLine(row);
                String sku = get(columns, headers.indexOf("Item Code"));
                String description = get(columns, headers.indexOf("Item Description"));
                BigDecimal quantity = readNumber(columns, headers, "Current Quantity", rowNumber);
                BigDecimal extendedCost = readNumber(columns, headers, "Line Total", rowNumber)
                        .setScale(2, RoundingMode.HALF_UP);
                if (quantity.signum() == 0 && extendedCost.signum() == 0) {
                    continue;
                }
                if (sku.isBlank() || description.isBlank() || quantity.signum() <= 0 || extendedCost.signum() < 0) {
                    throw new IllegalArgumentException("Invalid item or received quantity on CSV row " + rowNumber);
                }
                String splitFlag = get(columns, headers.indexOf("Split Item Indicator"));
                String weightFlag = get(columns, headers.indexOf("Unit of Measure"));
                if ((!splitFlag.equalsIgnoreCase("Y") && !splitFlag.equalsIgnoreCase("N"))
                        || (!weightFlag.equalsIgnoreCase("Y") && !weightFlag.equalsIgnoreCase("N"))) {
                    throw new IllegalArgumentException("Unrecognized split/weight indicator on CSV row " + rowNumber);
                }
                boolean split = splitFlag.equalsIgnoreCase("Y");
                BigDecimal unitCost = readNumber(columns, headers, "Unit Price", rowNumber);
                if (weightFlag.equalsIgnoreCase("Y")) {
                    // Store the billed cost per received case/each, rather than the per-pound price.
                    unitCost = extendedCost.divide(quantity, 4, RoundingMode.HALF_UP);
                }
                if (unitCost.signum() < 0) {
                    throw new IllegalArgumentException("Negative unit price on CSV row " + rowNumber);
                }
                double caseQty = split ? 0 : quantity.doubleValue();
                double splitQty = split ? quantity.doubleValue() : 0;
                BigDecimal caseCost = split ? BigDecimal.ZERO : unitCost;
                BigDecimal eachCost = split ? unitCost : BigDecimal.ZERO;
                invoiceTotal = invoiceTotal.add(extendedCost);

                lines.add(new InvoiceLine(
                        sku,
                        description,
                        caseQty,
                        splitQty,
                        "",
                        caseCost,
                        eachCost,
                        extendedCost
                ));
            }

        } catch (Exception e) {
            throw new RuntimeException("Failed to import finalized GFS invoice CSV: " + e.getMessage(), e);
        }

        return new InvoiceImportResult(
                invoiceNumber,
                invoiceDate,
                invoiceTotal,
                lines
        );
    }

    private BigDecimal readNumber(List<String> columns, List<String> headers, String name, int row) {
        String value = get(columns, headers.indexOf(name)).replace("$", "").replace(",", "").trim();
        try {
            return new BigDecimal(value);
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("Invalid " + name + " on CSV row " + row + ": " + value);
        }
    }

    public List<InvoiceLine> readInvoiceLines(File file) {
        return readInvoice(file).getLines();
    }

    private List<String> parseCsvLine(String line) {
        List<String> values = new ArrayList<>();
        StringBuilder current = new StringBuilder();

        boolean inQuotes = false;

        for (int i = 0; i < line.length(); i++) {
            char c = line.charAt(i);

            if (c == '"') {
                inQuotes = !inQuotes;
            } else if (c == ',' && !inQuotes) {
                values.add(clean(current.toString()));
                current.setLength(0);
            } else {
                current.append(c);
            }
        }

        values.add(clean(current.toString()));
        return values;
    }

    private String get(List<String> columns, int index) {
        if (columns.size() <= index) {
            return "";
        }

        return clean(columns.get(index));
    }

    private String clean(String value) {
        if (value == null) {
            return "";
        }

        return value.trim()
                .replace("\"", "")
                .replace("\uFEFF", "")
                .trim();
    }

    public static class InvoiceImportResult {
        private final String invoiceNumber;
        private final String invoiceDate;
        private final BigDecimal invoiceTotal;
        private final List<InvoiceLine> lines;

        public InvoiceImportResult(
                String invoiceNumber,
                String invoiceDate,
                BigDecimal invoiceTotal,
                List<InvoiceLine> lines
        ) {
            this.invoiceNumber = invoiceNumber;
            this.invoiceDate = invoiceDate;
            this.invoiceTotal = invoiceTotal;
            this.lines = lines;
        }

        public String getInvoiceNumber() {
            return invoiceNumber;
        }

        public String getInvoiceDate() {
            return invoiceDate;
        }

        public BigDecimal getInvoiceTotal() {
            return invoiceTotal;
        }

        public List<InvoiceLine> getLines() {
            return lines;
        }
    }
}
