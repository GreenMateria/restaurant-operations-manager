package ca.foodinventory.service;

import ca.foodinventory.database.DatabaseManager;
import ca.foodinventory.model.DailyLabourData;
import ca.foodinventory.model.LabourDailyEntry;
import ca.foodinventory.model.LabourDailySales;
import ca.foodinventory.model.LabourEmployee;
import ca.foodinventory.model.LabourPosition;
import ca.foodinventory.model.LabourSettings;
import ca.foodinventory.model.WeeklyLabourData;
import ca.foodinventory.model.WeeklyLabourRow;

import java.io.IOException;
import java.math.BigDecimal;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

public class LabourApiClient {

    private final HttpClient httpClient;

    public LabourApiClient() {
        this(HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .build());
    }

    LabourApiClient(HttpClient httpClient) {
        this.httpClient = httpClient;
    }

    public List<LabourPosition> findPositions() {
        return parsePositions(get("/labour/positions", "load labour positions"));
    }

    public List<LabourPosition> findActivePositions() {
        return parsePositions(get("/labour/positions/active", "load active labour positions"));
    }

    public void savePosition(LabourPosition position) {
        save(
                "/labour/positions",
                "/labour/positions/" + position.getId(),
                positionJson(position),
                "save labour position"
        );
    }

    public void deactivatePosition(int id) {
        postNoBody("/labour/positions/" + id + "/deactivate", "deactivate labour position");
    }

    public List<LabourEmployee> findEmployees() {
        return parseEmployees(get("/labour/employees", "load labour employees"));
    }

    public void saveEmployee(LabourEmployee employee) {
        save(
                "/labour/employees",
                "/labour/employees/" + employee.getId(),
                employeeJson(employee),
                "save labour employee"
        );
    }

    public void deactivateEmployee(int id) {
        postNoBody("/labour/employees/" + id + "/deactivate", "deactivate labour employee");
    }

    public LabourSettings loadSettings() {
        Map<String, Object> object = parseObject(get("/labour/settings", "load labour settings"));
        return new LabourSettings(moneyValue(object.get("defaultUniformDeduction")));
    }

    public void saveSettings(LabourSettings settings) {
        put(
                "/labour/settings",
                "{\"defaultUniformDeduction\":"
                        + money(settings.getDefaultUniformDeduction())
                        + "}",
                "save labour settings"
        );
    }

    public WeeklyLabourData loadWeeklyLabour(LocalDate weekStartDate) {
        Map<String, Object> object = parseObject(get(
                "/labour/weekly/" + weekStartDate,
                "load weekly labour"
        ));
        return parseWeeklyLabour(object);
    }

    public List<LocalDate> findSavedLabourWeeks() {
        List<LocalDate> weeks = new ArrayList<>();
        for (Map<String, Object> object : parseArray(get("/labour/weeks", "load saved labour weeks"))) {
            String weekStartDate = stringValue(object.get("weekStartDate"));
            if (weekStartDate != null && !weekStartDate.isBlank()) {
                weeks.add(LocalDate.parse(weekStartDate));
            }
        }
        return weeks;
    }

    public DailyLabourData loadDailyLabour(LocalDate workDate) {
        Map<String, Object> object = parseObject(get(
                "/labour/daily/" + workDate,
                "load daily labour"
        ));
        return parseDailyLabour(object);
    }

    public void saveWeeklyLabour(WeeklyLabourData weeklyLabourData) {
        put(
                "/labour/weekly",
                weeklyLabourJson(weeklyLabourData),
                "save weekly labour"
        );
    }

    public void saveDailyLabour(DailyLabourData dailyLabourData) {
        put(
                "/labour/daily",
                dailyLabourJson(dailyLabourData),
                "save daily labour"
        );
    }

    private String get(String path, String action) {
        HttpResponse<String> response = send(requestBuilder(path).GET().build(), action);
        requireStatus(response, 200, "Labour API");
        return response.body();
    }

    private String post(String path, String body, String action, int expectedStatus) {
        HttpResponse<String> response = send(
                requestBuilder(path)
                        .header("Content-Type", "application/json")
                        .POST(HttpRequest.BodyPublishers.ofString(body))
                        .build(),
                action
        );
        requireStatus(response, expectedStatus, "Labour API");
        return response.body();
    }

    private String put(String path, String body, String action) {
        HttpResponse<String> response = send(
                requestBuilder(path)
                        .header("Content-Type", "application/json")
                        .PUT(HttpRequest.BodyPublishers.ofString(body))
                        .build(),
                action
        );
        requireStatus(response, 200, "Labour API");
        return response.body();
    }

    private void save(
            String createPath,
            String updatePath,
            String body,
            String action
    ) {
        if (body.contains("\"id\":0")) {
            post(createPath, body, action, 201);
        } else {
            put(updatePath, body, action);
        }
    }

    private void postNoBody(String path, String action) {
        HttpResponse<String> response = send(
                requestBuilder(path).POST(HttpRequest.BodyPublishers.noBody()).build(),
                action
        );
        requireStatus(response, 200, "Labour API");
    }

    private HttpRequest.Builder requestBuilder(String path) {
        String baseUrl = DatabaseManager.getConfiguredApiUrl();
        String apiKey = DatabaseManager.getConfiguredApiKey();
        if (baseUrl.isBlank() || apiKey.isBlank()) {
            throw new IllegalStateException("API mode requires api.url and api.key configuration.");
        }
        HttpRequest.Builder builder = HttpRequest.newBuilder(endpoint(baseUrl, path))
                .timeout(Duration.ofSeconds(30))
                .header("Accept", "application/json")
                .header("x-api-key", apiKey);
        String locationToken = DatabaseManager.getLocationSessionToken();
        if (locationToken != null && !locationToken.isBlank()) {
            builder.header("x-location-token", locationToken);
        }
        return builder;
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

    private URI endpoint(String baseUrl, String path) {
        String normalized = baseUrl.trim();
        if (normalized.endsWith("/")) {
            normalized = normalized.substring(0, normalized.length() - 1);
        }
        return URI.create(normalized + path);
    }

    private List<LabourPosition> parsePositions(String json) {
        List<LabourPosition> positions = new ArrayList<>();
        for (Map<String, Object> object : parseArray(json)) {
            positions.add(new LabourPosition(
                    intValue(object.get("id")),
                    stringValue(object.get("name")),
                    stringValue(object.get("labourGroup")),
                    intValue(object.get("sortOrder")),
                    nullableMoneyValue(object.get("targetLabourPercentage")),
                    booleanValue(object.get("active"))
            ));
        }
        return positions;
    }

    private List<LabourEmployee> parseEmployees(String json) {
        List<LabourEmployee> employees = new ArrayList<>();
        for (Map<String, Object> object : parseArray(json)) {
            employees.add(new LabourEmployee(
                    intValue(object.get("id")),
                    stringValue(object.get("name")),
                    intValue(object.get("positionId")),
                    stringValue(object.get("positionName")),
                    stringValue(object.get("labourGroup")),
                    moneyValue(object.get("hourlyWage")),
                    booleanValue(object.get("tipPoolEligible")),
                    booleanValue(object.get("uniformDeductionApplicable")),
                    booleanValue(object.get("active"))
            ));
        }
        return employees;
    }

    @SuppressWarnings("unchecked")
    private WeeklyLabourData parseWeeklyLabour(Map<String, Object> object) {
        LocalDate weekStartDate = LocalDate.parse(stringValue(object.get("weekStartDate")));
        List<WeeklyLabourRow> rows = new ArrayList<>();
        Object rowsValue = object.get("rows");
        if (rowsValue instanceof List<?> rowObjects) {
            for (Object rowValue : rowObjects) {
                if (rowValue instanceof Map<?, ?> rawRow) {
                    rows.add(parseWeeklyRow((Map<String, Object>) rawRow));
                }
            }
        }
        rows.sort(Comparator
                .comparingInt(WeeklyLabourRow::getPositionSortOrder)
                .thenComparing(row -> nullSafe(row.getPositionName()))
                .thenComparing(row -> nullSafe(row.getEmployeeName())));
        return new WeeklyLabourData(weekStartDate, rows);
    }

    @SuppressWarnings("unchecked")
    private WeeklyLabourRow parseWeeklyRow(Map<String, Object> object) {
        WeeklyLabourRow row = new WeeklyLabourRow();
        row.setEmployeeId(intValue(object.get("employeeId")));
        row.setEmployeeName(stringValue(object.get("employeeName")));
        row.setPositionId(intValue(object.get("positionId")));
        row.setPositionName(stringValue(object.get("positionName")));
        row.setLabourGroup(stringValue(object.get("labourGroup")));
        row.setPositionSortOrder(intValue(object.get("positionSortOrder")));
        row.setPositionTargetLabourPercentage(nullableMoneyValue(object.get("positionTargetLabourPercentage")));
        row.setHourlyWage(moneyValue(object.get("hourlyWage")));
        row.setActiveEmployee(booleanValue(object.get("activeEmployee")));
        row.setTipPoolEligible(booleanValue(object.get("tipPoolEligible")));
        row.setUniformDeductionApplicable(booleanValue(object.get("uniformDeductionApplicable")));

        Object entriesValue = object.get("entries");
        if (entriesValue instanceof List<?> entryObjects) {
            for (Object entryValue : entryObjects) {
                if (entryValue instanceof Map<?, ?> rawEntry) {
                    LabourDailyEntry entry = parseDailyEntry((Map<String, Object>) rawEntry);
                    row.getEntriesByDate().put(entry.getWorkDate(), entry);
                }
            }
        }
        return row;
    }

    @SuppressWarnings("unchecked")
    private DailyLabourData parseDailyLabour(Map<String, Object> object) {
        LocalDate workDate = LocalDate.parse(stringValue(object.get("workDate")));
        LabourDailySales sales = parseDailySales((Map<String, Object>) object.get("sales"));
        List<WeeklyLabourRow> rows = new ArrayList<>();
        Object rowsValue = object.get("rows");
        if (rowsValue instanceof List<?> rowObjects) {
            for (Object rowValue : rowObjects) {
                if (rowValue instanceof Map<?, ?> rawRow) {
                    rows.add(parseWeeklyRow((Map<String, Object>) rawRow));
                }
            }
        }
        rows.sort(Comparator
                .comparingInt(WeeklyLabourRow::getPositionSortOrder)
                .thenComparing(row -> nullSafe(row.getPositionName()))
                .thenComparing(row -> nullSafe(row.getEmployeeName())));
        return new DailyLabourData(workDate, sales, rows);
    }

    private LabourDailySales parseDailySales(Map<String, Object> object) {
        LabourDailySales sales = new LabourDailySales();
        if (object == null) {
            return sales;
        }
        sales.setId(intValue(object.get("id")));
        String salesDate = stringValue(object.get("salesDate"));
        if (salesDate != null && !salesDate.isBlank()) {
            sales.setSalesDate(LocalDate.parse(salesDate));
        }
        sales.setNetSales(moneyValue(object.get("netSales")));
        sales.setTipOutPool(moneyValue(object.get("tipOutPool")));
        sales.setFinalized(booleanValue(object.get("finalized")));
        return sales;
    }

    private LabourDailyEntry parseDailyEntry(Map<String, Object> object) {
        LabourDailyEntry entry = new LabourDailyEntry();
        entry.setId(intValue(object.get("id")));
        entry.setWorkDate(LocalDate.parse(stringValue(object.get("workDate"))));
        entry.setEmployeeId(intValue(object.get("employeeId")));
        entry.setPositionId(intValue(object.get("positionId")));
        entry.setHourlyWage(moneyValue(object.get("hourlyWage")));
        entry.setShift1Hours(moneyValue(object.get("shift1Hours")));
        entry.setShift2Hours(moneyValue(object.get("shift2Hours")));
        entry.setEmployeeNameSnapshot(stringValue(object.get("employeeNameSnapshot")));
        entry.setPositionNameSnapshot(stringValue(object.get("positionNameSnapshot")));
        entry.setLabourGroupSnapshot(stringValue(object.get("labourGroupSnapshot")));
        entry.setFinalized(booleanValue(object.get("finalized")));
        return entry;
    }

    private String positionJson(LabourPosition position) {
        return "{"
                + "\"id\":" + position.getId() + ","
                + "\"name\":" + jsonString(position.getName()) + ","
                + "\"labourGroup\":" + jsonString(position.getLabourGroup()) + ","
                + "\"sortOrder\":" + position.getSortOrder() + ","
                + "\"targetLabourPercentage\":"
                + nullableMoney(position.getTargetLabourPercentage()) + ","
                + "\"active\":" + position.isActive()
                + "}";
    }

    private String employeeJson(LabourEmployee employee) {
        return "{"
                + "\"id\":" + employee.getId() + ","
                + "\"name\":" + jsonString(employee.getName()) + ","
                + "\"positionId\":" + employee.getPositionId() + ","
                + "\"hourlyWage\":" + money(employee.getHourlyWage()) + ","
                + "\"tipPoolEligible\":" + employee.isTipPoolEligible() + ","
                + "\"uniformDeductionApplicable\":"
                + employee.isUniformDeductionApplicable() + ","
                + "\"active\":" + employee.isActive()
                + "}";
    }

    private String weeklyLabourJson(WeeklyLabourData data) {
        StringBuilder json = new StringBuilder();
        json.append('{')
                .append("\"weekStartDate\":")
                .append(jsonString(data.weekStartDate().toString()))
                .append(",\"rows\":[");

        boolean firstRow = true;
        for (WeeklyLabourRow row : data.rows()) {
            if (!firstRow) {
                json.append(',');
            }
            json.append(weeklyRowJson(row));
            firstRow = false;
        }

        return json.append("]}").toString();
    }

    private String weeklyRowJson(WeeklyLabourRow row) {
        StringBuilder json = new StringBuilder();
        json.append('{')
                .append("\"employeeId\":").append(row.getEmployeeId()).append(',')
                .append("\"employeeName\":").append(jsonString(row.getEmployeeName())).append(',')
                .append("\"positionId\":").append(row.getPositionId()).append(',')
                .append("\"positionName\":").append(jsonString(row.getPositionName())).append(',')
                .append("\"labourGroup\":").append(jsonString(row.getLabourGroup())).append(',')
                .append("\"positionSortOrder\":").append(row.getPositionSortOrder()).append(',')
                .append("\"positionTargetLabourPercentage\":")
                .append(nullableMoney(row.getPositionTargetLabourPercentage())).append(',')
                .append("\"hourlyWage\":").append(money(row.getHourlyWage())).append(',')
                .append("\"activeEmployee\":").append(row.isActiveEmployee()).append(',')
                .append("\"tipPoolEligible\":").append(row.isTipPoolEligible()).append(',')
                .append("\"uniformDeductionApplicable\":").append(row.isUniformDeductionApplicable()).append(',')
                .append("\"entries\":[");

        boolean firstEntry = true;
        for (LabourDailyEntry entry : row.getEntriesByDate().values()) {
            if (!firstEntry) {
                json.append(',');
            }
            json.append(dailyEntryJson(entry));
            firstEntry = false;
        }

        return json.append("]}").toString();
    }

    private String dailyLabourJson(DailyLabourData data) {
        StringBuilder json = new StringBuilder();
        json.append('{')
                .append("\"workDate\":")
                .append(jsonString(data.workDate().toString()))
                .append(",\"sales\":")
                .append(dailySalesJson(data.sales(), data.workDate()))
                .append(",\"rows\":[");

        boolean firstRow = true;
        for (WeeklyLabourRow row : data.rows()) {
            if (!firstRow) {
                json.append(',');
            }
            json.append(weeklyRowJson(row));
            firstRow = false;
        }

        return json.append("]}").toString();
    }

    private String dailySalesJson(LabourDailySales sales, LocalDate workDate) {
        LabourDailySales value = sales == null ? new LabourDailySales() : sales;
        LocalDate salesDate = value.getSalesDate() == null ? workDate : value.getSalesDate();
        return "{"
                + "\"id\":" + value.getId() + ","
                + "\"salesDate\":" + jsonString(salesDate.toString()) + ","
                + "\"netSales\":" + money(value.getNetSales()) + ","
                + "\"tipOutPool\":" + money(value.getTipOutPool()) + ","
                + "\"finalized\":" + value.isFinalized()
                + "}";
    }

    private String dailyEntryJson(LabourDailyEntry entry) {
        return "{"
                + "\"id\":" + entry.getId() + ","
                + "\"workDate\":" + jsonString(entry.getWorkDate().toString()) + ","
                + "\"employeeId\":" + entry.getEmployeeId() + ","
                + "\"positionId\":" + entry.getPositionId() + ","
                + "\"hourlyWage\":" + money(entry.getHourlyWage()) + ","
                + "\"shift1Hours\":" + money(entry.getShift1Hours()) + ","
                + "\"shift2Hours\":" + money(entry.getShift2Hours()) + ","
                + "\"employeeNameSnapshot\":"
                + jsonString(entry.getEmployeeNameSnapshot()) + ","
                + "\"positionNameSnapshot\":"
                + jsonString(entry.getPositionNameSnapshot()) + ","
                + "\"labourGroupSnapshot\":"
                + jsonString(entry.getLabourGroupSnapshot()) + ","
                + "\"finalized\":" + entry.isFinalized()
                + "}";
    }

    private List<Map<String, Object>> parseArray(String json) {
        return new JsonObjectArrayParser().parse(json);
    }

    private Map<String, Object> parseObject(String json) {
        return new ApiJsonParser().parseObject(json);
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

    private String money(BigDecimal value) {
        return value == null ? "0" : value.toPlainString();
    }

    private String nullableMoney(BigDecimal value) {
        return value == null ? "null" : value.toPlainString();
    }

    private BigDecimal moneyValue(Object value) {
        BigDecimal money = nullableMoneyValue(value);
        return money == null ? BigDecimal.ZERO : money;
    }

    private BigDecimal nullableMoneyValue(Object value) {
        if (value == null) {
            return null;
        }
        String text = value.toString();
        return text.isBlank() ? null : new BigDecimal(text);
    }

    private int intValue(Object value) {
        return value instanceof Number number ? number.intValue() : 0;
    }

    private boolean booleanValue(Object value) {
        return value instanceof Boolean bool && bool;
    }

    private String stringValue(Object value) {
        return value == null ? null : value.toString();
    }

    private String nullSafe(String value) {
        return value == null ? "" : value;
    }
}
