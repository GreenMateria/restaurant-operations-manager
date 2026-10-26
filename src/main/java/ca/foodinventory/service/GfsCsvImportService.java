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
        return readInvoice(file, null);
    }

    public InvoiceImportResult readInvoice(File file, QuantityCorrection quantityCorrection) {
        List<InvoiceLine> lines = new ArrayList<>();

        String invoiceNumber = "";
        String invoiceDate = "";
        BigDecimal invoiceTotal = BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        BigDecimal taxTotal = BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        BigDecimal supplierTotal = null;

        try (BufferedReader reader = new BufferedReader(new FileReader(file, StandardCharsets.UTF_8))) {
            String header = reader.readLine();
            if (header == null) {
                throw new IllegalArgumentException("The invoice CSV is empty.");
            }
            List<String> headers = parseCsvLine(header);
            boolean invoicesList = headers.contains("Invoice") && headers.contains("Line Item");
            String[] required = invoicesList
                    ? new String[]{"Invoice", "Invoice Date", "Original Amount", "Line Item", "Item Description",
                    "Original Quantity", "Current Quantity", "Unit Price", "Total Amount", "Tax Amount",
                    "Unit of Measure", "Weight", "Split Item Indicator"}
                    : new String[]{"Item Description", "Current Quantity", "Unit Price", "Line Total",
                    "Unit of Measure", "Split Item Indicator", "Item Code"};
            for (String name : required) {
                if (!headers.contains(name)) {
                    throw new IllegalArgumentException("Choose a finalized LineItemList or InvoicesList CSV. Missing column: " + name);
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
                if (invoicesList) {
                    String rowInvoice = get(columns, headers.indexOf("Invoice"));
                    String rowDate = get(columns, headers.indexOf("Invoice Date"));
                    BigDecimal rowTotal = readNumber(columns, headers, "Original Amount", rowNumber)
                            .setScale(2, RoundingMode.HALF_UP);
                    if (rowInvoice.isBlank() || rowDate.isBlank() || rowTotal.signum() < 0) {
                        throw new IllegalArgumentException("Invalid invoice details on CSV row " + rowNumber);
                    }
                    if (supplierTotal == null) {
                        invoiceNumber = rowInvoice;
                        invoiceDate = rowDate;
                        supplierTotal = rowTotal;
                    } else if (!invoiceNumber.equals(rowInvoice) || !invoiceDate.equals(rowDate)
                            || supplierTotal.compareTo(rowTotal) != 0) {
                        throw new IllegalArgumentException("InvoicesList CSV contains multiple invoices or inconsistent totals on row " + rowNumber);
                    }
                }
                String sku = get(columns, headers.indexOf(invoicesList ? "Line Item" : "Item Code"));
                String description = get(columns, headers.indexOf("Item Description"));
                BigDecimal quantity = readNumber(columns, headers, "Current Quantity", rowNumber);
                BigDecimal extendedCost = readNumber(columns, headers, invoicesList ? "Total Amount" : "Line Total", rowNumber)
                        .setScale(2, RoundingMode.HALF_UP);
                if (invoicesList) {
                    taxTotal = taxTotal.add(readNumber(columns, headers, "Tax Amount", rowNumber));
                }
                if (quantity.signum() == 0 && extendedCost.signum() == 0) {
                    continue;
                }
                String splitFlag = get(columns, headers.indexOf("Split Item Indicator"));
                String weightFlag = get(columns, headers.indexOf("Unit of Measure"));
                boolean split = readIndicator(splitFlag, "Split Item Indicator", rowNumber);
                boolean weighted = readIndicator(weightFlag, "Unit of Measure", rowNumber);
                BigDecimal billedWeight = invoicesList
                        ? readNumber(columns, headers, "Weight", rowNumber) : BigDecimal.ZERO;
                if (billedWeight.signum() < 0) {
                    throw new IllegalArgumentException("Negative Weight on CSV row " + rowNumber);
                }
                if (quantity.signum() == 0 && extendedCost.signum() > 0 && quantityCorrection != null) {
                    BigDecimal suggested = invoicesList
                            ? readNumber(columns, headers, "Original Quantity", rowNumber) : null;
                    quantity = quantityCorrection.correct(rowNumber, sku, description, extendedCost, split, suggested);
                    if (quantity == null) {
                        throw new ImportCancelledException();
                    }
                }
                if (quantity.signum() <= 0) {
                    throw new IllegalArgumentException("Invalid Current Quantity on CSV row " + rowNumber
                            + ": " + quantity + " (Line Total: " + extendedCost + ")");
                }
                if (sku.isBlank() || description.isBlank() || extendedCost.signum() < 0) {
                    throw new IllegalArgumentException("Invalid item or Line Total on CSV row " + rowNumber);
                }
                BigDecimal unitCost = readNumber(columns, headers, "Unit Price", rowNumber);
                if (weighted) {
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

                InvoiceLine line = new InvoiceLine(
                        sku,
                        description,
                        caseQty,
                        splitQty,
                        "",
                        caseCost,
                        eachCost,
                        extendedCost
                );
                if (weighted && billedWeight.signum() > 0) {
                    line.setBilledWeight(billedWeight);
                }
                lines.add(line);
            }

        } catch (ImportCancelledException e) {
            throw e;
        } catch (Exception e) {
            throw new RuntimeException("Failed to import finalized invoice CSV: " + e.getMessage(), e);
        }

        return new InvoiceImportResult(invoiceNumber, invoiceDate, invoiceTotal,
                taxTotal.setScale(2, RoundingMode.HALF_UP), supplierTotal, lines);
    }

    private BigDecimal readNumber(List<String> columns, List<String> headers, String name, int row) {
        String value = get(columns, headers.indexOf(name)).replace("$", "").replace(",", "").trim();
        try {
            return new BigDecimal(value);
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("Invalid " + name + " on CSV row " + row + ": " + value);
        }
    }

    private boolean readIndicator(String value, String name, int row) {
        if (value.equalsIgnoreCase("Y") || value.equals("1")) {
            return true;
        }
        if (value.equalsIgnoreCase("N") || value.equals("0")) {
            return false;
        }
        throw new IllegalArgumentException("Unrecognized " + name + " on CSV row " + row + ": " + value);
    }

    @FunctionalInterface
    public interface QuantityCorrection {
        BigDecimal correct(int row, String sku, String description, BigDecimal lineTotal,
                           boolean split, BigDecimal suggestedQuantity);
    }

    public static class ImportCancelledException extends RuntimeException {
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
        private final BigDecimal taxTotal;
        private final BigDecimal supplierTotal;
        private final List<InvoiceLine> lines;

        public InvoiceImportResult(
                String invoiceNumber,
                String invoiceDate,
                BigDecimal invoiceTotal,
                BigDecimal taxTotal,
                BigDecimal supplierTotal,
                List<InvoiceLine> lines
        ) {
            this.invoiceNumber = invoiceNumber;
            this.invoiceDate = invoiceDate;
            this.invoiceTotal = invoiceTotal;
            this.taxTotal = taxTotal;
            this.supplierTotal = supplierTotal;
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

        public BigDecimal getTaxTotal() {
            return taxTotal;
        }

        public BigDecimal getSupplierTotal() {
            return supplierTotal;
        }

        public List<InvoiceLine> getLines() {
            return lines;
        }
    }
}
