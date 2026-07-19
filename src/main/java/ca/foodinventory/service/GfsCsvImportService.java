package ca.foodinventory.service;

import ca.foodinventory.model.InvoiceLine;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
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

        try (BufferedReader reader = new BufferedReader(new FileReader(file))) {
            String row;

            while ((row = reader.readLine()) != null) {
                List<String> columns = parseCsvLine(row);

                if (columns.isEmpty()) {
                    continue;
                }

                String rowType = clean(columns.get(0));

                if (rowType.equalsIgnoreCase("H")) {
                    invoiceNumber = get(columns, 9);
                    invoiceDate = get(columns, 5);
                    invoiceTotal = parseMoney(get(columns, 11));
                    continue;
                }

                if (!rowType.equalsIgnoreCase("P")) {
                    continue;
                }

                String sku = get(columns, 1);
                double caseQty = parseDouble(get(columns, 2));
                double splitQty = parseDouble(get(columns, 3));
                String packSize = get(columns, 5);
                String description = get(columns, 7);

                BigDecimal caseCost = parseUnitCost(get(columns, 10));
                BigDecimal eachCost = parseUnitCost(get(columns, 11));

                BigDecimal extendedCost = caseCost
                        .multiply(BigDecimal.valueOf(caseQty))
                        .add(eachCost.multiply(BigDecimal.valueOf(splitQty)))
                        .setScale(2, RoundingMode.HALF_UP);

                lines.add(new InvoiceLine(
                        sku,
                        description,
                        caseQty,
                        splitQty,
                        packSize,
                        caseCost,
                        eachCost,
                        extendedCost
                ));
            }

        } catch (Exception e) {
            throw new RuntimeException("Failed to import GFS invoice CSV", e);
        }

        return new InvoiceImportResult(
                invoiceNumber,
                invoiceDate,
                invoiceTotal,
                lines
        );
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

    private double parseDouble(String value) {
        if (value == null || value.isBlank()) {
            return 0;
        }

        try {
            return Double.parseDouble(value.trim());
        } catch (NumberFormatException e) {
            return 0;
        }
    }


    private BigDecimal parseUnitCost(String value) {
        if (value == null || value.isBlank()) {
            return BigDecimal.ZERO.setScale(4, RoundingMode.HALF_UP);
        }

        try {
            return new BigDecimal(
                    value.replace("$", "")
                            .replace(",", "")
                            .trim()
            ).setScale(4, RoundingMode.HALF_UP);

        } catch (NumberFormatException e) {
            return BigDecimal.ZERO.setScale(4, RoundingMode.HALF_UP);
        }
    }

    private BigDecimal parseMoney(String value) {
        if (value == null || value.isBlank()) {
            return BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        }

        try {
            return new BigDecimal(
                    value.replace("$", "")
                            .replace(",", "")
                            .trim()
            ).setScale(2, RoundingMode.HALF_UP);

        } catch (NumberFormatException e) {
            return BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        }
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