package ca.foodinventory.service;

import ca.foodinventory.model.InventoryValuationLine;
import ca.foodinventory.model.Invoice;
import ca.foodinventory.model.InvoiceCategoryBreakdownLine;
import ca.foodinventory.model.InvoiceLine;
import ca.foodinventory.model.SalesPeriod;

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

public class ReportingApiClient {

    private final HttpClient httpClient;

    public ReportingApiClient() {
        this(HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .build());
    }

    ReportingApiClient(HttpClient httpClient) {
        this.httpClient = httpClient;
    }

    public List<Invoice> findInvoices() {
        return parseInvoices(get("/reporting/invoices", "load invoices"));
    }

    public List<InvoiceLine> findInvoiceLines(int invoiceId) {
        return parseInvoiceLines(get("/reporting/invoices/" + invoiceId + "/lines", "load invoice lines"));
    }

    public List<InvoiceCategoryBreakdownLine> findInvoiceBreakdown(int invoiceId) {
        return parseBreakdown(get("/reporting/invoices/" + invoiceId + "/breakdown", "load invoice breakdown"));
    }

    public void deleteInvoice(int invoiceId) {
        HttpResponse<String> response = send(
                requestBuilder("/reporting/invoices/" + invoiceId)
                        .DELETE()
                        .build(),
                "delete invoice"
        );
        requireStatus(response, 200, "Reporting API");
    }

    public List<SalesPeriod> findSalesPeriods() {
        return parseSalesPeriods(get("/sales-periods", "load sales periods"));
    }

    public void saveSalesPeriod(SalesPeriod salesPeriod) {
        HttpResponse<String> response = send(
                requestBuilder("/sales-periods")
                        .header("Content-Type", "application/json")
                        .POST(HttpRequest.BodyPublishers.ofString(salesPeriodJson(salesPeriod)))
                        .build(),
                "save sales period"
        );
        requireStatus(response, 201, "Sales Period API");
    }

    public List<InventoryValuationLine> calculateValuation(int countId) {
        return parseValuationLines(get(
                "/reporting/inventory-valuations/" + countId,
                "load inventory valuation"
        ));
    }

    public String generateWeeklyCostReportText(int openingCountId, int closingCountId) {
        Map<String, Object> object = parseObject(get(
                "/reporting/weekly-cost/" + openingCountId + "/" + closingCountId,
                "load weekly cost report"
        ));
        Object reportText = object.get("reportText");
        return reportText == null ? "" : reportText.toString();
    }

    private String get(String path, String action) {
        HttpResponse<String> response = send(
                requestBuilder(path).GET().build(),
                action
        );
        requireStatus(response, 200, "Reporting API");
        return response.body();
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

    private void requireStatus(HttpResponse<String> response, int expectedStatus, String apiName) {
        if (response.statusCode() != expectedStatus) {
            throw new RuntimeException(apiName + " returned HTTP " + response.statusCode());
        }
    }

    private List<Invoice> parseInvoices(String json) {
        List<Invoice> invoices = new ArrayList<>();
        for (Map<String, Object> object : objects(json)) {
            invoices.add(new Invoice(
                    intValue(object.get("id")),
                    stringValue(object.get("invoiceNumber")),
                    stringValue(object.get("supplier")),
                    stringValue(object.get("invoiceDate")),
                    moneyValue(object.get("importedTotal")),
                    moneyValue(object.get("merchandiseSubtotal")),
                    moneyValue(object.get("freight")),
                    moneyValue(object.get("hst")),
                    moneyValue(object.get("invoiceTotal"))
            ));
        }
        return invoices;
    }

    private List<InvoiceLine> parseInvoiceLines(String json) {
        List<InvoiceLine> lines = new ArrayList<>();
        for (Map<String, Object> object : objects(json)) {
            lines.add(new InvoiceLine(
                    stringValue(object.get("sku")),
                    stringValue(object.get("description")),
                    doubleValue(object.get("caseQty")),
                    doubleValue(object.get("splitQty")),
                    stringValue(object.get("packSize")),
                    moneyValue(object.get("caseCost")),
                    moneyValue(object.get("eachCost")),
                    moneyValue(object.get("extendedCost"))
            ));
        }
        return lines;
    }

    private List<InvoiceCategoryBreakdownLine> parseBreakdown(String json) {
        List<InvoiceCategoryBreakdownLine> rows = new ArrayList<>();
        for (Map<String, Object> object : objects(json)) {
            rows.add(new InvoiceCategoryBreakdownLine(
                    stringValue(object.get("reportingCategory")),
                    moneyValue(object.get("total"))
            ));
        }
        return rows;
    }

    private List<SalesPeriod> parseSalesPeriods(String json) {
        List<SalesPeriod> periods = new ArrayList<>();
        for (Map<String, Object> object : objects(json)) {
            periods.add(new SalesPeriod(
                    intValue(object.get("id")),
                    stringValue(object.get("periodStartDate")),
                    stringValue(object.get("periodEndDate")),
                    moneyValue(object.get("foodSales")),
                    moneyValue(object.get("beerSales")),
                    moneyValue(object.get("wineSales")),
                    moneyValue(object.get("draughtSales")),
                    moneyValue(object.get("importDraughtSales")),
                    moneyValue(object.get("liquorSales")),
                    moneyValue(object.get("foodNetSales")),
                    moneyValue(object.get("beerNetSales")),
                    moneyValue(object.get("wineNetSales")),
                    moneyValue(object.get("draughtNetSales")),
                    moneyValue(object.get("importDraughtNetSales")),
                    moneyValue(object.get("liquorNetSales"))
            ));
        }
        return periods;
    }

    private List<InventoryValuationLine> parseValuationLines(String json) {
        List<InventoryValuationLine> lines = new ArrayList<>();
        for (Map<String, Object> object : objects(json)) {
            lines.add(new InventoryValuationLine(
                    stringValue(object.get("sku")),
                    stringValue(object.get("productDescription")),
                    stringValue(object.get("category")),
                    stringValue(object.get("reportingCategory")),
                    doubleValue(object.get("countedQuantity")),
                    moneyValue(object.get("averageCost")),
                    moneyValue(object.get("inventoryValue")),
                    stringValue(object.get("costSource"))
            ));
        }
        return lines;
    }

    private List<Map<String, Object>> objects(String json) {
        return new JsonObjectArrayParser().parse(json);
    }

    private Map<String, Object> parseObject(String json) {
        List<Map<String, Object>> objects = new JsonObjectArrayParser().parse("[" + json + "]");
        return objects.isEmpty() ? Map.of() : objects.getFirst();
    }

    private String salesPeriodJson(SalesPeriod salesPeriod) {
        return new StringBuilder("{")
                .append("\"periodStartDate\":").append(jsonString(salesPeriod.getPeriodStartDate())).append(',')
                .append("\"periodEndDate\":").append(jsonString(salesPeriod.getPeriodEndDate())).append(',')
                .append("\"foodSales\":").append(money(salesPeriod.getFoodSales())).append(',')
                .append("\"beerSales\":").append(money(salesPeriod.getBeerSales())).append(',')
                .append("\"wineSales\":").append(money(salesPeriod.getWineSales())).append(',')
                .append("\"draughtSales\":").append(money(salesPeriod.getDraughtSales())).append(',')
                .append("\"importDraughtSales\":").append(money(salesPeriod.getImportDraughtSales())).append(',')
                .append("\"liquorSales\":").append(money(salesPeriod.getLiquorSales())).append(',')
                .append("\"foodNetSales\":").append(money(salesPeriod.getFoodNetSales())).append(',')
                .append("\"beerNetSales\":").append(money(salesPeriod.getBeerNetSales())).append(',')
                .append("\"wineNetSales\":").append(money(salesPeriod.getWineNetSales())).append(',')
                .append("\"draughtNetSales\":").append(money(salesPeriod.getDraughtNetSales())).append(',')
                .append("\"importDraughtNetSales\":").append(money(salesPeriod.getImportDraughtNetSales())).append(',')
                .append("\"liquorNetSales\":").append(money(salesPeriod.getLiquorNetSales()))
                .append('}')
                .toString();
    }

    private String money(BigDecimal value) {
        return value == null ? "0" : value.toPlainString();
    }

    private String jsonString(String value) {
        return value == null ? "null" : "\"" + escape(value) + "\"";
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

    private int intValue(Object value) {
        return value instanceof Number number ? number.intValue() : 0;
    }

    private double doubleValue(Object value) {
        return value instanceof Number number ? number.doubleValue() : 0;
    }

    private String stringValue(Object value) {
        return value == null ? null : value.toString();
    }

    private BigDecimal moneyValue(Object value) {
        String text = stringValue(value);
        if (text == null || text.isBlank()) {
            return BigDecimal.ZERO;
        }
        return new BigDecimal(text);
    }
}
