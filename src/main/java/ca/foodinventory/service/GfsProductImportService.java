package ca.foodinventory.service;

import ca.foodinventory.dao.ProductDao;
import ca.foodinventory.database.DatabaseManager;
import ca.foodinventory.model.Product;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

public class GfsProductImportService {

    private final ProductDao productDao = new ProductDao();
    private final ProductApiClient productApiClient = new ProductApiClient();

    public int importProducts(File file) {
        List<Product> parsedProducts = new ArrayList<>();

        try (BufferedReader reader = new BufferedReader(new FileReader(file))) {
            String line;

            while ((line = reader.readLine()) != null) {
                List<String> columns = parseCsvLine(line);

                if (columns.size() < 15) {
                    continue;
                }

                String rowType = clean(columns.get(0));

                if (!rowType.equalsIgnoreCase("P")) {
                    continue;
                }

                String sku = clean(columns.get(1));
                String packCount = clean(columns.get(7));
                String packSize = clean(columns.get(8));
                String unit = clean(columns.get(9));
                String description = clean(columns.get(12));
                String category = normalizeCategory(clean(columns.get(13)));
                BigDecimal caseCost = parseMoney(clean(columns.get(14)));

                if (sku.isBlank() || description.isBlank()) {
                    continue;
                }

                Product product = new Product(
                        0,
                        sku,
                        description,
                        category,
                        "OTHER",
                        unit.isBlank() ? "EA" : unit,
                        1,
                        packSize,
                        packCount,
                        caseCost,
                        null,
                        true
                );

                parsedProducts.add(product);
            }

            if (DatabaseManager.isApiDatabase()) {
                return productApiClient.upsertImportedProducts(parsedProducts);
            }

            for (Product product : parsedProducts) {
                productDao.upsertFromImport(product);
            }
            return parsedProducts.size();

        } catch (Exception ex) {
            throw new RuntimeException("Failed to import GFS product guide", ex);
        }
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
                values.add(current.toString());
                current.setLength(0);
            } else {
                current.append(c);
            }
        }

        values.add(current.toString());

        return values;
    }

    private String clean(String value) {
        if (value == null) {
            return "";
        }

        return value.trim()
                .replace("\"", "")
                .replace("\uFEFF", "")
                .replace("🍁", "")
                .replace("🌱", "")
                .trim();
    }

    private BigDecimal parseMoney(String value) {
        if (value == null || value.isBlank()) {
            return BigDecimal.ZERO;
        }

        try {
            return new BigDecimal(
                    value.replace("$", "")
                            .replace(",", "")
                            .trim()
            );
        } catch (NumberFormatException ex) {
            return BigDecimal.ZERO;
        }
    }

    private String normalizeCategory(String gfsCategory) {
        if (gfsCategory == null || gfsCategory.isBlank()) {
            return "Uncategorized";
        }

        return switch (gfsCategory.trim().toUpperCase()) {
            case "MEATS" -> "MEAT";
            case "DAIRY" -> "DAIRY/CHEESE";
            default -> gfsCategory.trim().toUpperCase();
        };
    }
}
