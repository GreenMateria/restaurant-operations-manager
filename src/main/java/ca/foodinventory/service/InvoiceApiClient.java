package ca.foodinventory.service;

import ca.foodinventory.model.InvoiceAdjustment;
import ca.foodinventory.model.InvoiceLine;
import ca.foodinventory.model.PurchasePriceBaseline;

import java.io.IOException;
import java.math.BigDecimal;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class InvoiceApiClient {

    private final HttpClient httpClient;

    public InvoiceApiClient() {
        this(HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .build());
    }

    InvoiceApiClient(HttpClient httpClient) {
        this.httpClient = httpClient;
    }

    public List<String> findSupplierNames() {
        HttpResponse<String> response = send(
                requestBuilder("/invoices/suppliers").GET().build(),
                "load suppliers"
        );
        if (response.statusCode() == 404) {
            return new ReportingApiClient().findInvoices().stream()
                    .map(ca.foodinventory.model.Invoice::getSupplier)
                    .filter(name -> name != null && !name.isBlank())
                    .toList();
        }
        requireStatus(response, 200, "Invoice API");
        List<String> suppliers = new ArrayList<>();
        for (Map<String, Object> row : new JsonObjectArrayParser().parse(response.body())) {
            Object name = row.get("name");
            if (name != null && !name.toString().isBlank()) {
                suppliers.add(name.toString());
            }
        }
        return suppliers;
    }

    public List<PurchasePriceBaseline> findPriceBaselines(List<String> skus, String invoiceDate,
                                                          String supplier, String invoiceNumber) {
        StringBuilder payload = new StringBuilder("{\"skus\":[");
        for (String sku : new java.util.LinkedHashSet<>(skus)) {
            if (payload.charAt(payload.length() - 1) != '[') payload.append(',');
            payload.append(jsonString(sku));
        }
        payload.append("],\"invoiceDate\":").append(jsonString(invoiceDate))
                .append(",\"supplier\":").append(jsonString(supplier))
                .append(",\"invoiceNumber\":").append(jsonString(invoiceNumber)).append('}');
        HttpResponse<String> response = send(requestBuilder("/invoices/price-baselines")
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(payload.toString())).build(),
                "load purchase price history");
        if (response.statusCode() == 404) {
            throw new IllegalStateException("The Cloud API must be updated to review invoice price changes.");
        }
        requireStatus(response, 200, "Invoice API");
        List<PurchasePriceBaseline> baselines = new ArrayList<>();
        for (Map<String, Object> row : new JsonObjectArrayParser().parse(response.body())) {
            baselines.add(new PurchasePriceBaseline(
                    row.get("sku").toString(), new BigDecimal(row.get("totalCost").toString()),
                    new BigDecimal(row.get("baseQuantity").toString()),
                    new BigDecimal(row.get("purchaseCount").toString()).intValueExact(),
                    new BigDecimal(row.get("conversionFactor").toString()),
                    Boolean.parseBoolean(row.get("recent").toString())
            ));
        }
        return baselines;
    }

    public boolean invoiceExists(String invoiceNumber) {
        return invoiceExistsAt("/invoices/exists", null, invoiceNumber);
    }

    public boolean invoiceExists(String supplier, String invoiceNumber) {
        return invoiceExistsAt("/invoices/exists-by-supplier", supplier, invoiceNumber);
    }

    private boolean invoiceExistsAt(String path, String supplier, String invoiceNumber) {
        HttpResponse<String> response = send(
                requestBuilder(path)
                        .header("Content-Type", "application/json")
                        .POST(HttpRequest.BodyPublishers.ofString(
                                "{\"invoiceNumber\":" + jsonString(invoiceNumber)
                                        + ",\"supplier\":" + jsonString(supplier) + "}"
                        ))
                        .build(),
                "check invoice number"
        );
        if (response.statusCode() == 404 && supplier != null) {
            throw new IllegalStateException("The Cloud API must be updated before supplier-scoped invoices can be saved.");
        }
        requireStatus(response, 200, "Invoice API");
        return response.body() != null && response.body().contains("\"exists\":\"true\"");
    }

    public void deleteInvoice(String invoiceNumber) {
        deleteInvoiceAt("/invoices/delete", null, invoiceNumber);
    }

    public void deleteInvoice(String supplier, String invoiceNumber) {
        deleteInvoiceAt("/invoices/delete-by-supplier", supplier, invoiceNumber);
    }

    private void deleteInvoiceAt(String path, String supplier, String invoiceNumber) {
        HttpResponse<String> response = send(
                requestBuilder(path)
                        .header("Content-Type", "application/json")
                        .POST(HttpRequest.BodyPublishers.ofString(
                                "{\"invoiceNumber\":" + jsonString(invoiceNumber)
                                        + ",\"supplier\":" + jsonString(supplier) + "}"
                        ))
                        .build(),
                "delete invoice"
        );
        if (response.statusCode() == 404 && supplier != null) {
            throw new IllegalStateException("The Cloud API must be updated before supplier-scoped invoices can be overwritten.");
        }
        requireStatus(response, 200, "Invoice API");
    }

    public void saveInvoice(
            String department,
            String supplier,
            String invoiceNumber,
            String invoiceDate,
            BigDecimal invoiceTotal,
            List<InvoiceLine> lines
    ) {
        saveInvoice(
                invoicePathForDepartment(department),
                supplier,
                invoiceNumber,
                invoiceDate,
                invoiceTotal,
                merchandiseSubtotal(lines),
                invoiceTotal,
                List.of(),
                lines
        );
    }

    public void saveInvoice(
            String supplier,
            String invoiceNumber,
            String invoiceDate,
            BigDecimal importedTotal,
            BigDecimal merchandiseSubtotal,
            BigDecimal invoiceTotal,
            List<InvoiceAdjustment> adjustments,
            List<InvoiceLine> lines
    ) {
        saveInvoice(
                "/food-invoices",
                supplier,
                invoiceNumber,
                invoiceDate,
                importedTotal,
                merchandiseSubtotal,
                invoiceTotal,
                adjustments,
                lines
        );
    }

    public void saveAlcoholInvoice(
            String supplier,
            String invoiceNumber,
            String invoiceDate,
            BigDecimal importedTotal,
            BigDecimal merchandiseSubtotal,
            BigDecimal invoiceTotal,
            List<InvoiceAdjustment> adjustments,
            List<InvoiceLine> lines
    ) {
        saveInvoice(
                "/alcohol-invoices",
                supplier,
                invoiceNumber,
                invoiceDate,
                importedTotal,
                merchandiseSubtotal,
                invoiceTotal,
                adjustments,
                lines
        );
    }

    public void saveSuppliesInvoice(
            String supplier,
            String invoiceNumber,
            String invoiceDate,
            BigDecimal importedTotal,
            BigDecimal merchandiseSubtotal,
            BigDecimal invoiceTotal,
            List<InvoiceAdjustment> adjustments,
            List<InvoiceLine> lines
    ) {
        saveInvoice(
                "/supplies-invoices",
                supplier,
                invoiceNumber,
                invoiceDate,
                importedTotal,
                merchandiseSubtotal,
                invoiceTotal,
                adjustments,
                lines
        );
    }

    private void saveInvoice(
            String path,
            String supplier,
            String invoiceNumber,
            String invoiceDate,
            BigDecimal importedTotal,
            BigDecimal merchandiseSubtotal,
            BigDecimal invoiceTotal,
            List<InvoiceAdjustment> adjustments,
            List<InvoiceLine> lines
    ) {
        HttpResponse<String> response = send(
                requestBuilder(path)
                        .header("Content-Type", "application/json")
                        .POST(HttpRequest.BodyPublishers.ofString(invoicePayloadJson(
                                supplier,
                                invoiceNumber,
                                invoiceDate,
                                importedTotal,
                                merchandiseSubtotal,
                                invoiceTotal,
                                adjustments,
                                lines
                        )))
                        .build(),
                "save invoice"
        );
        requireStatus(response, 201, "Invoice API");
    }

    public void saveInvoice(
            String supplier,
            String invoiceNumber,
            String invoiceDate,
            BigDecimal invoiceTotal,
            List<InvoiceLine> lines
    ) {
        saveInvoice(
                supplier,
                invoiceNumber,
                invoiceDate,
                invoiceTotal,
                merchandiseSubtotal(lines),
                invoiceTotal,
                List.of(),
                lines
        );
    }

    private BigDecimal merchandiseSubtotal(List<InvoiceLine> lines) {
        BigDecimal subtotal = BigDecimal.ZERO;
        if (lines != null) {
            for (InvoiceLine line : lines) {
                if (line.getExtendedCost() != null) {
                    subtotal = subtotal.add(line.getExtendedCost());
                }
            }
        }

        return subtotal;
    }

    private String invoicePathForDepartment(String department) {
        String normalizedDepartment = department == null
                ? ""
                : department.trim().toUpperCase();

        return switch (normalizedDepartment) {
            case "FOOD" -> "/food-invoices";
            case "SUPPLIES" -> "/supplies-invoices";
            default -> throw new IllegalArgumentException(
                    "Invoice API is not available for department: " + department
            );
        };
    }

    private HttpRequest.Builder requestBuilder(String path) {
        return ApiRequestSupport.requestBuilder(path);
    }

    private HttpResponse<String> send(HttpRequest request, String action) {
        try {
            return httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        } catch (IOException e) {
            throw new RuntimeException("Failed to " + action + ".", e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("Request interrupted while trying to " + action + ".", e);
        }
    }

    private void requireStatus(
            HttpResponse<String> response,
            int expectedStatus,
            String apiName
    ) {
        if (response.statusCode() != expectedStatus) {
            String detail = response.body() == null || response.body().isBlank()
                    ? ""
                    : ": " + response.body();
            throw new RuntimeException(
                    apiName + " returned HTTP " + response.statusCode() + detail
            );
        }
    }

    private String invoicePayloadJson(
            String supplier,
            String invoiceNumber,
            String invoiceDate,
            BigDecimal importedTotal,
            BigDecimal merchandiseSubtotal,
            BigDecimal invoiceTotal,
            List<InvoiceAdjustment> adjustments,
            List<InvoiceLine> lines
    ) {
        StringBuilder json = new StringBuilder("[");
        json.append('{')
                .append("\"recordType\":\"invoice\",")
                .append("\"supplier\":").append(jsonString(supplier)).append(',')
                .append("\"invoiceNumber\":").append(jsonString(invoiceNumber)).append(',')
                .append("\"invoiceDate\":").append(jsonString(invoiceDate)).append(',')
                .append("\"importedTotal\":").append(money(importedTotal)).append(',')
                .append("\"merchandiseSubtotal\":").append(money(merchandiseSubtotal)).append(',')
                .append("\"invoiceTotal\":").append(money(invoiceTotal))
                .append('}');

        if (lines != null) {
            for (InvoiceLine line : lines) {
                json.append(',').append(lineJson(line));
            }
        }

        if (adjustments != null) {
            for (InvoiceAdjustment adjustment : adjustments) {
                json.append(',').append(adjustmentJson(adjustment));
            }
        }

        return json.append(']').toString();
    }

    private String lineJson(InvoiceLine line) {
        return new StringBuilder("{")
                .append("\"recordType\":\"line\",")
                .append("\"sku\":").append(jsonString(line.getSku())).append(',')
                .append("\"caseQty\":").append(line.getCaseQty()).append(',')
                .append("\"splitQty\":").append(line.getSplitQty()).append(',')
                .append("\"packSize\":").append(jsonString(line.getPackSize())).append(',')
                .append("\"caseCost\":").append(money(line.getCaseCost())).append(',')
                .append("\"eachCost\":").append(money(line.getEachCost())).append(',')
                .append("\"extendedCost\":").append(money(line.getExtendedCost()))
                .append('}')
                .toString();
    }

    private String adjustmentJson(InvoiceAdjustment adjustment) {
        return new StringBuilder("{")
                .append("\"recordType\":\"adjustment\",")
                .append("\"description\":").append(jsonString(adjustment.getDescription())).append(',')
                .append("\"amount\":").append(money(adjustment.getAmount())).append(',')
                .append("\"displayOrder\":").append(adjustment.getDisplayOrder())
                .append('}')
                .toString();
    }

    private String money(BigDecimal value) {
        return value == null ? "0" : value.toPlainString();
    }

    private String jsonString(String value) {
        if (value == null) {
            return "null";
        }

        return "\"" + escape(value) + "\"";
    }

    private String escape(String value) {
        StringBuilder escaped = new StringBuilder();
        for (int i = 0; i < value.length(); i++) {
            char ch = value.charAt(i);
            switch (ch) {
                case '"' -> escaped.append("\\\"");
                case '\\' -> escaped.append("\\\\");
                case '\b' -> escaped.append("\\b");
                case '\f' -> escaped.append("\\f");
                case '\n' -> escaped.append("\\n");
                case '\r' -> escaped.append("\\r");
                case '\t' -> escaped.append("\\t");
                default -> {
                    if (ch < 0x20) {
                        escaped.append("\\u%04x".formatted((int) ch));
                    } else {
                        escaped.append(ch);
                    }
                }
            }
        }

        return escaped.toString();
    }
}
