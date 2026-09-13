package ca.foodinventory.api;

import java.sql.SQLException;
import java.util.List;
import java.util.Map;

class ApiRoutes {

    private final ProductRepository productRepository;
    private final PosMenuItemRepository posMenuItemRepository;
    private final AlcoholSalesMappingRepository alcoholSalesMappingRepository;
    private final InventoryRepository inventoryRepository;
    private final InvoiceRepository invoiceRepository;
    private final AlcoholProductProfileRepository alcoholProductProfileRepository;
    private final ProductionRepository productionRepository;
    private final ReportingRepository reportingRepository;
    private final AdminSyncRepository adminSyncRepository;
    private final LabourRepository labourRepository;
    private final LocationAuthRepository locationAuthRepository;

    ApiRoutes() {
        this(
                new ProductRepository(),
                new PosMenuItemRepository(),
                new AlcoholSalesMappingRepository(),
                new InventoryRepository(),
                new InvoiceRepository(),
                new AlcoholProductProfileRepository(),
                new ProductionRepository(),
                new ReportingRepository(),
                new AdminSyncRepository(),
                new LabourRepository(),
                new LocationAuthRepository()
        );
    }

    ApiRoutes(
            ProductRepository productRepository,
            PosMenuItemRepository posMenuItemRepository,
            AlcoholSalesMappingRepository alcoholSalesMappingRepository,
            InventoryRepository inventoryRepository,
            InvoiceRepository invoiceRepository,
            AlcoholProductProfileRepository alcoholProductProfileRepository,
            ProductionRepository productionRepository,
            ReportingRepository reportingRepository,
            AdminSyncRepository adminSyncRepository,
            LabourRepository labourRepository,
            LocationAuthRepository locationAuthRepository
    ) {
        this.productRepository = productRepository;
        this.posMenuItemRepository = posMenuItemRepository;
        this.alcoholSalesMappingRepository = alcoholSalesMappingRepository;
        this.inventoryRepository = inventoryRepository;
        this.invoiceRepository = invoiceRepository;
        this.alcoholProductProfileRepository = alcoholProductProfileRepository;
        this.productionRepository = productionRepository;
        this.reportingRepository = reportingRepository;
        this.adminSyncRepository = adminSyncRepository;
        this.labourRepository = labourRepository;
        this.locationAuthRepository = locationAuthRepository;
    }

    ApiResult handle(
            String method,
            String path,
            Map<String, List<String>> headers,
            String body
    ) {
        String normalizedPath = normalizePath(path);

        if ("GET".equalsIgnoreCase(method) && "/health".equals(normalizedPath)) {
            return ApiResult.json(200, """
                    {"status":"ok","version":"%s"}
                    """.formatted(Json.escape(ApiConfig.VERSION)).trim());
        }

        if ("POST".equalsIgnoreCase(method) && "/auth/login".equals(normalizedPath)) {
            try {
                Map<String, Object> request = parseBody(body);
                String username = requiredBodyString(request, "username");
                String password = requiredBodyString(request, "password");
                LocationSession session = locationAuthRepository.login(username, password);
                if (session == null) {
                    return ApiResult.json(401, Json.object(
                            "error", "invalid_credentials",
                            "message", "The location username or password is incorrect."
                    ));
                }

                return ApiResult.json(200, locationSessionJson(session));
            } catch (IllegalArgumentException e) {
                return badRequest(e);
            } catch (IllegalStateException e) {
                return ApiResult.json(503, Json.object(
                        "error", "database_not_configured",
                        "message", e.getMessage()
                ));
            } catch (SQLException e) {
                e.printStackTrace();
                return databaseError("Failed to log in.");
            }
        }

        if ("GET".equalsIgnoreCase(method) && "/products".equals(normalizedPath)) {
            ApiResult unauthorized = requireApiKey(headers);
            if (unauthorized != null) {
                return unauthorized;
            }

            try {
                LocationContext locationContext = requireLocationContext(headers);
                if (locationContext == null) {
                    return locationUnauthorized();
                }
                return ApiResult.json(200, productRepository.findActiveProductsJson(locationContext.id()));
            } catch (IllegalStateException e) {
                return ApiResult.json(503, Json.object(
                        "error", "database_not_configured",
                        "message", e.getMessage()
                ));
            } catch (SQLException e) {
                e.printStackTrace();
                return ApiResult.json(500, databaseErrorJson(e));
            }
        }

        if ("POST".equalsIgnoreCase(method) && "/products".equals(normalizedPath)) {
            ApiResult unauthorized = requireApiKey(headers);
            if (unauthorized != null) {
                return unauthorized;
            }

            try {
                LocationContext locationContext = requireLocationContext(headers);
                if (locationContext == null) {
                    return locationUnauthorized();
                }
                productRepository.save(locationContext.id(), parseBody(body));
                return ApiResult.json(201, Json.object(
                        "status", "ok",
                        "message", "Product saved."
                ));
            } catch (IllegalArgumentException e) {
                return ApiResult.json(400, Json.object(
                        "error", "bad_request",
                        "message", e.getMessage()
                ));
            } catch (SQLException e) {
                e.printStackTrace();
                return ApiResult.json(500, Json.object(
                        "error", "database_error",
                        "message", "Failed to save product."
                ));
            }
        }

        if ("POST".equalsIgnoreCase(method) && "/products/import".equals(normalizedPath)) {
            ApiResult unauthorized = requireApiKey(headers);
            if (unauthorized != null) {
                return unauthorized;
            }

            try {
                LocationContext locationContext = requireLocationContext(headers);
                if (locationContext == null) {
                    return locationUnauthorized();
                }
                int importedCount = productRepository.upsertImportedProducts(
                        locationContext.id(),
                        parseBodyArray(body)
                );
                return ApiResult.json(200, Json.object(
                        "importedCount", String.valueOf(importedCount)
                ));
            } catch (IllegalArgumentException e) {
                return ApiResult.json(400, Json.object(
                        "error", "bad_request",
                        "message", e.getMessage()
                ));
            } catch (SQLException e) {
                e.printStackTrace();
                return ApiResult.json(500, Json.object(
                        "error", "database_error",
                        "message", "Failed to import products."
                ));
            }
        }

        Integer productId = pathId(normalizedPath, "/products/");
        if (productId != null) {
            ApiResult unauthorized = requireApiKey(headers);
            if (unauthorized != null) {
                return unauthorized;
            }

            try {
                LocationContext locationContext = requireLocationContext(headers);
                if (locationContext == null) {
                    return locationUnauthorized();
                }
                if ("PUT".equalsIgnoreCase(method)) {
                    productRepository.save(locationContext.id(), parseBody(body));
                    return ApiResult.json(200, Json.object(
                            "status", "ok",
                            "message", "Product saved."
                    ));
                }

                if ("POST".equalsIgnoreCase(method)
                        && normalizedPath.endsWith("/deactivate")) {
                    return productRepository.deactivate(locationContext.id(), productId)
                            ? ApiResult.json(200, Json.object(
                            "status", "ok",
                            "message", "Product deactivated."
                    ))
                            : ApiResult.json(404, Json.object(
                            "error", "not_found",
                            "message", "Product not found."
                    ));
                }

                if ("GET".equalsIgnoreCase(method)
                        && normalizedPath.endsWith("/purchase-history")) {
                    return ApiResult.json(
                            200,
                            productRepository.findPurchaseHistoryJson(locationContext.id(), productId)
                    );
                }
            } catch (IllegalArgumentException e) {
                return ApiResult.json(400, Json.object(
                        "error", "bad_request",
                        "message", e.getMessage()
                ));
            } catch (SQLException e) {
                e.printStackTrace();
                return ApiResult.json(500, Json.object(
                        "error", "database_error",
                        "message", "Failed to save product."
                ));
            }
        }

        if ("GET".equalsIgnoreCase(method) && "/pos-menu-items".equals(normalizedPath)) {
            ApiResult unauthorized = requireApiKey(headers);
            if (unauthorized != null) {
                return unauthorized;
            }

            try {
                LocationContext locationContext = requireLocationContext(headers);
                if (locationContext == null) {
                    return locationUnauthorized();
                }

                return ApiResult.json(
                        200,
                        productionRepository.findPosMenuItemsJson(locationContext.id(), false)
                );
            } catch (IllegalStateException e) {
                return ApiResult.json(503, Json.object(
                        "error", "database_not_configured",
                        "message", e.getMessage()
                ));
            } catch (SQLException e) {
                e.printStackTrace();
                return ApiResult.json(500, Json.object(
                        "error", "database_error",
                        "message", "Failed to load POS menu items."
                ));
            }
        }

        if ("POST".equalsIgnoreCase(method) && "/pos-menu-items".equals(normalizedPath)) {
            ApiResult unauthorized = requireApiKey(headers);
            if (unauthorized != null) {
                return unauthorized;
            }

            try {
                LocationContext locationContext = requireLocationContext(headers);
                if (locationContext == null) {
                    return locationUnauthorized();
                }

                return ApiResult.json(
                        201,
                        productionRepository.savePosMenuItemJson(locationContext.id(), parseBody(body))
                );
            } catch (IllegalArgumentException e) {
                return badRequest(e);
            } catch (SQLException e) {
                e.printStackTrace();
                return databaseError("Failed to save POS menu item.");
            }
        }

        if ("POST".equalsIgnoreCase(method) && "/invoices/exists".equals(normalizedPath)) {
            ApiResult unauthorized = requireApiKey(headers);
            if (unauthorized != null) {
                return unauthorized;
            }

            try {
                LocationContext locationContext = requireLocationContext(headers);
                if (locationContext == null) {
                    return locationUnauthorized();
                }
                boolean exists = invoiceRepository.invoiceExists(
                        locationContext.id(),
                        stringBodyField(body, "invoiceNumber")
                );
                return ApiResult.json(200, Json.object("exists", String.valueOf(exists)));
            } catch (IllegalArgumentException e) {
                return ApiResult.json(400, Json.object(
                        "error", "bad_request",
                        "message", e.getMessage()
                ));
            } catch (SQLException e) {
                e.printStackTrace();
                return ApiResult.json(500, Json.object(
                        "error", "database_error",
                        "message", "Failed to check invoice number."
                ));
            }
        }

        if ("POST".equalsIgnoreCase(method) && "/invoices/delete".equals(normalizedPath)) {
            ApiResult unauthorized = requireApiKey(headers);
            if (unauthorized != null) {
                return unauthorized;
            }

            try {
                LocationContext locationContext = requireLocationContext(headers);
                if (locationContext == null) {
                    return locationUnauthorized();
                }
                invoiceRepository.deleteInvoice(
                        locationContext.id(),
                        stringBodyField(body, "invoiceNumber")
                );
                return ApiResult.json(200, Json.object(
                        "status", "ok",
                        "message", "Invoice deleted."
                ));
            } catch (IllegalArgumentException e) {
                return ApiResult.json(400, Json.object(
                        "error", "bad_request",
                        "message", e.getMessage()
                ));
            } catch (SQLException e) {
                e.printStackTrace();
                return ApiResult.json(500, Json.object(
                        "error", "database_error",
                        "message", "Failed to delete invoice."
                ));
            }
        }

        if ("POST".equalsIgnoreCase(method) && "/food-invoices".equals(normalizedPath)) {
            ApiResult unauthorized = requireApiKey(headers);
            if (unauthorized != null) {
                return unauthorized;
            }

            try {
                LocationContext locationContext = requireLocationContext(headers);
                if (locationContext == null) {
                    return locationUnauthorized();
                }
                invoiceRepository.saveInvoice(locationContext.id(), parseBodyArray(body));
                return ApiResult.json(201, Json.object(
                        "status", "ok",
                        "message", "Invoice saved."
                ));
            } catch (IllegalArgumentException e) {
                return ApiResult.json(400, Json.object(
                        "error", "bad_request",
                        "message", e.getMessage()
                ));
            } catch (SQLException e) {
                e.printStackTrace();
                return ApiResult.json(500, Json.object(
                        "error", "database_error",
                        "message", "Failed to save invoice."
                ));
            }
        }

        if ("POST".equalsIgnoreCase(method) && "/supplies-invoices".equals(normalizedPath)) {
            ApiResult unauthorized = requireApiKey(headers);
            if (unauthorized != null) {
                return unauthorized;
            }

            try {
                LocationContext locationContext = requireLocationContext(headers);
                if (locationContext == null) {
                    return locationUnauthorized();
                }
                invoiceRepository.saveInvoice(locationContext.id(), parseBodyArray(body));
                return ApiResult.json(201, Json.object(
                        "status", "ok",
                        "message", "Invoice saved."
                ));
            } catch (IllegalArgumentException e) {
                return ApiResult.json(400, Json.object(
                        "error", "bad_request",
                        "message", e.getMessage()
                ));
            } catch (SQLException e) {
                e.printStackTrace();
                return ApiResult.json(500, Json.object(
                        "error", "database_error",
                        "message", "Failed to save invoice."
                ));
            }
        }

        if ("POST".equalsIgnoreCase(method) && "/alcohol-invoices".equals(normalizedPath)) {
            ApiResult unauthorized = requireApiKey(headers);
            if (unauthorized != null) {
                return unauthorized;
            }

            try {
                LocationContext locationContext = requireLocationContext(headers);
                if (locationContext == null) {
                    return locationUnauthorized();
                }
                invoiceRepository.saveInvoice(locationContext.id(), parseBodyArray(body));
                return ApiResult.json(201, Json.object(
                        "status", "ok",
                        "message", "Invoice saved."
                ));
            } catch (IllegalArgumentException e) {
                return ApiResult.json(400, Json.object(
                        "error", "bad_request",
                        "message", e.getMessage()
                ));
            } catch (SQLException e) {
                e.printStackTrace();
                return ApiResult.json(500, Json.object(
                        "error", "database_error",
                        "message", "Failed to save invoice."
                ));
            }
        }

        if ("GET".equalsIgnoreCase(method) && "/alcohol-product-profiles".equals(normalizedPath)) {
            ApiResult unauthorized = requireApiKey(headers);
            if (unauthorized != null) {
                return unauthorized;
            }

            try {
                LocationContext locationContext = requireLocationContext(headers);
                if (locationContext == null) {
                    return locationUnauthorized();
                }

                return ApiResult.json(
                        200,
                        alcoholProductProfileRepository.findAllActiveJson(locationContext.id())
                );
            } catch (SQLException e) {
                e.printStackTrace();
                return ApiResult.json(500, Json.object(
                        "error", "database_error",
                        "message", "Failed to load alcohol product profiles."
                ));
            }
        }

        ApiResult productionResult = handleProductionRoute(method, normalizedPath, headers, body);
        if (productionResult != null) {
            return productionResult;
        }

        ApiResult labourResult = handleLabourRoute(method, normalizedPath, headers, body);
        if (labourResult != null) {
            return labourResult;
        }

        DepartmentPath departmentPath = departmentPath(normalizedPath);
        if (departmentPath != null) {
            ApiResult unauthorized = requireApiKey(headers);
            if (unauthorized != null) {
                return unauthorized;
            }

            try {
                LocationContext locationContext = requireLocationContext(headers);
                if (locationContext == null) {
                    return locationUnauthorized();
                }
                if ("GET".equalsIgnoreCase(method)
                        && "inventory-count-templates".equals(departmentPath.resource())) {
                    return ApiResult.json(
                            200,
                            inventoryRepository.findActiveTemplatesJson(
                                    locationContext.id(),
                                    departmentPath.department()
                            )
                    );
                }

                if ("POST".equalsIgnoreCase(method)
                        && "inventory-count-templates".equals(departmentPath.resource())) {
                    return ApiResult.json(
                            201,
                            inventoryRepository.addTemplateJson(locationContext.id(), parseBody(body))
                    );
                }

                if ("GET".equalsIgnoreCase(method)
                        && "inventory-counts".equals(departmentPath.resource())) {
                    return ApiResult.json(
                            200,
                            inventoryRepository.findCountsJson(
                                    locationContext.id(),
                                    departmentPath.department(),
                                    false
                            )
                    );
                }

                if ("POST".equalsIgnoreCase(method)
                        && "inventory-counts".equals(departmentPath.resource())) {
                    return ApiResult.json(
                            201,
                            inventoryRepository.createCountJson(locationContext.id(), parseBody(body))
                    );
                }

                if ("GET".equalsIgnoreCase(method)
                        && "completed-inventory-counts".equals(departmentPath.resource())) {
                    return ApiResult.json(
                            200,
                            inventoryRepository.findCountsJson(
                                    locationContext.id(),
                                    departmentPath.department(),
                                    true
                            )
                    );
                }
            } catch (IllegalArgumentException e) {
                return ApiResult.json(400, Json.object(
                        "error", "bad_request",
                        "message", e.getMessage()
                ));
            } catch (IllegalStateException e) {
                return ApiResult.json(503, Json.object(
                        "error", "database_not_configured",
                        "message", e.getMessage()
                ));
            } catch (SQLException e) {
                e.printStackTrace();
                return ApiResult.json(500, Json.object(
                        "error", "database_error",
                        "message", "Failed to process inventory request."
                ));
            }
        }

        Integer templateId = pathId(normalizedPath, "/inventory-count-templates/");
        if (templateId != null) {
            ApiResult unauthorized = requireApiKey(headers);
            if (unauthorized != null) {
                return unauthorized;
            }

            try {
                LocationContext locationContext = requireLocationContext(headers);
                if (locationContext == null) {
                    return locationUnauthorized();
                }
                if ("POST".equalsIgnoreCase(method)
                        && normalizedPath.endsWith("/deactivate")) {
                    return inventoryRepository.deactivateTemplate(locationContext.id(), templateId)
                            ? ApiResult.json(200, Json.object(
                            "status", "ok",
                            "message", "Inventory count template deactivated."
                    ))
                            : ApiResult.json(404, Json.object(
                            "error", "not_found",
                            "message", "Inventory count template not found."
                    ));
                }

                if ("POST".equalsIgnoreCase(method)
                        && normalizedPath.endsWith("/duplicate")) {
                    return ApiResult.json(
                            201,
                            inventoryRepository.duplicateTemplateJson(
                                    locationContext.id(),
                                    templateId,
                                    parseBody(body)
                            )
                    );
                }
            } catch (IllegalArgumentException e) {
                return ApiResult.json(400, Json.object(
                        "error", "bad_request",
                        "message", e.getMessage()
                ));
            } catch (SQLException e) {
                e.printStackTrace();
                return ApiResult.json(500, Json.object(
                        "error", "database_error",
                        "message", "Failed to process inventory count template."
                ));
            }
        }

        TemplateLinesPath templateLinesPath = templateLinesPath(normalizedPath);
        if (templateLinesPath != null) {
            ApiResult unauthorized = requireApiKey(headers);
            if (unauthorized != null) {
                return unauthorized;
            }

            try {
                LocationContext locationContext = requireLocationContext(headers);
                if (locationContext == null) {
                    return locationUnauthorized();
                }
                if ("GET".equalsIgnoreCase(method) && templateLinesPath.linesRequest()) {
                    return ApiResult.json(
                            200,
                            inventoryRepository.findTemplateLinesJson(
                                    locationContext.id(),
                                    templateLinesPath.templateId()
                            )
                    );
                }

                if ("POST".equalsIgnoreCase(method) && templateLinesPath.linesRequest()) {
                    inventoryRepository.addTemplateLine(
                            locationContext.id(),
                            templateLinesPath.templateId(),
                            parseBody(body)
                    );
                    return ApiResult.json(201, Json.object(
                            "status", "ok",
                            "message", "Inventory count template line added."
                    ));
                }

                if ("PUT".equalsIgnoreCase(method) && templateLinesPath.sortRequest()) {
                    inventoryRepository.updateTemplateLineSortOrders(
                            locationContext.id(),
                            parseBodyArray(body)
                    );
                    return ApiResult.json(200, Json.object(
                            "status", "ok",
                            "message", "Inventory count template line order saved."
                    ));
                }
            } catch (IllegalArgumentException e) {
                return ApiResult.json(400, Json.object(
                        "error", "bad_request",
                        "message", e.getMessage()
                ));
            } catch (SQLException e) {
                e.printStackTrace();
                return ApiResult.json(500, Json.object(
                        "error", "database_error",
                        "message", "Failed to process inventory count template lines."
                ));
            }
        }

        Integer templateLineId = pathId(normalizedPath, "/inventory-count-template-lines/");
        if (templateLineId != null) {
            ApiResult unauthorized = requireApiKey(headers);
            if (unauthorized != null) {
                return unauthorized;
            }

            try {
                LocationContext locationContext = requireLocationContext(headers);
                if (locationContext == null) {
                    return locationUnauthorized();
                }
                if ("PUT".equalsIgnoreCase(method)) {
                    if (normalizedPath.endsWith("/order-guide-case-size")) {
                        return inventoryRepository.updateOrderGuideCaseSize(
                                locationContext.id(),
                                templateLineId,
                                parseBody(body)
                        )
                                ? ApiResult.json(200, Json.object(
                                "status", "ok",
                                "message", "Order guide case size saved."
                        ))
                                : ApiResult.json(404, Json.object(
                                "error", "not_found",
                                "message", "Inventory count template line not found."
                        ));
                    }

                    return inventoryRepository.updateTemplateLine(
                            locationContext.id(),
                            templateLineId,
                            parseBody(body)
                    )
                            ? ApiResult.json(200, Json.object(
                            "status", "ok",
                            "message", "Inventory count template line saved."
                    ))
                            : ApiResult.json(404, Json.object(
                            "error", "not_found",
                            "message", "Inventory count template line not found."
                    ));
                }

                if ("POST".equalsIgnoreCase(method)
                        && normalizedPath.endsWith("/deactivate")) {
                    return inventoryRepository.deactivateTemplateLine(locationContext.id(), templateLineId)
                            ? ApiResult.json(200, Json.object(
                            "status", "ok",
                            "message", "Inventory count template line deactivated."
                    ))
                            : ApiResult.json(404, Json.object(
                            "error", "not_found",
                            "message", "Inventory count template line not found."
                    ));
                }
            } catch (IllegalArgumentException e) {
                return ApiResult.json(400, Json.object(
                        "error", "bad_request",
                        "message", e.getMessage()
                ));
            } catch (SQLException e) {
                e.printStackTrace();
                return ApiResult.json(500, Json.object(
                        "error", "database_error",
                        "message", "Failed to process inventory count template line."
                ));
            }
        }

        OrderGuidePath orderGuidePath = orderGuidePath(normalizedPath);
        if (orderGuidePath != null) {
            ApiResult unauthorized = requireApiKey(headers);
            if (unauthorized != null) {
                return unauthorized;
            }

            try {
                LocationContext locationContext = requireLocationContext(headers);
                if (locationContext == null) {
                    return locationUnauthorized();
                }
                if ("GET".equalsIgnoreCase(method)) {
                    return ApiResult.json(
                            200,
                            inventoryRepository.generateOrderGuideJson(
                                    locationContext.id(),
                                    orderGuidePath.openingCountId(),
                                    orderGuidePath.closingCountId()
                            )
                    );
                }
            } catch (IllegalArgumentException e) {
                return ApiResult.json(400, Json.object(
                        "error", "bad_request",
                        "message", e.getMessage()
                ));
            } catch (SQLException e) {
                e.printStackTrace();
                return ApiResult.json(500, Json.object(
                        "error", "database_error",
                        "message", "Failed to generate order guide."
                ));
            }
        }

        CountLinesPath countLinesPath = countLinesPath(normalizedPath);
        if (countLinesPath != null) {
            ApiResult unauthorized = requireApiKey(headers);
            if (unauthorized != null) {
                return unauthorized;
            }

            try {
                LocationContext locationContext = requireLocationContext(headers);
                if (locationContext == null) {
                    return locationUnauthorized();
                }
                if ("GET".equalsIgnoreCase(method) && countLinesPath.linesRequest()) {
                    return ApiResult.json(
                            200,
                            inventoryRepository.findCountLinesJson(locationContext.id(), countLinesPath.countId())
                    );
                }

                if ("PUT".equalsIgnoreCase(method) && countLinesPath.linesRequest()) {
                    inventoryRepository.updateCountLines(locationContext.id(), parseBodyArray(body));
                    return ApiResult.json(200, Json.object(
                            "status", "ok",
                            "message", "Inventory count quantities saved."
                    ));
                }

                if ("POST".equalsIgnoreCase(method) && countLinesPath.completeRequest()) {
                    inventoryRepository.completeCount(
                            locationContext.id(),
                            countLinesPath.countId(),
                            parseBodyArray(body)
                    );
                    return ApiResult.json(200, Json.object(
                            "status", "ok",
                            "message", "Inventory count completed."
                    ));
                }

                if ("DELETE".equalsIgnoreCase(method) && countLinesPath.countRequest()) {
                    return inventoryRepository.deleteCount(locationContext.id(), countLinesPath.countId())
                            ? ApiResult.json(200, Json.object(
                            "status", "ok",
                            "message", "Inventory count deleted."
                    ))
                            : ApiResult.json(404, Json.object(
                            "error", "not_found",
                            "message", "Inventory count not found."
                    ));
                }
            } catch (IllegalArgumentException e) {
                return ApiResult.json(400, Json.object(
                        "error", "bad_request",
                        "message", e.getMessage()
                ));
            } catch (SQLException e) {
                e.printStackTrace();
                return ApiResult.json(500, Json.object(
                        "error", "database_error",
                        "message", "Failed to process inventory count."
                ));
            }
        }

        if ("GET".equalsIgnoreCase(method) && "/alcohol-sales-mappings".equals(normalizedPath)) {
            ApiResult unauthorized = requireApiKey(headers);
            if (unauthorized != null) {
                return unauthorized;
            }

            try {
                LocationContext locationContext = requireLocationContext(headers);
                if (locationContext == null) {
                    return locationUnauthorized();
                }

                return ApiResult.json(200, alcoholSalesMappingRepository.findAllJson(locationContext.id()));
            } catch (IllegalStateException e) {
                return ApiResult.json(503, Json.object(
                        "error", "database_not_configured",
                        "message", e.getMessage()
                ));
            } catch (SQLException e) {
                e.printStackTrace();
                return ApiResult.json(500, Json.object(
                        "error", "database_error",
                        "message", "Failed to load alcohol sales mappings."
                ));
            }
        }

        if ("POST".equalsIgnoreCase(method) && "/alcohol-sales-mappings".equals(normalizedPath)) {
            ApiResult unauthorized = requireApiKey(headers);
            if (unauthorized != null) {
                return unauthorized;
            }

            try {
                LocationContext locationContext = requireLocationContext(headers);
                if (locationContext == null) {
                    return locationUnauthorized();
                }

                return ApiResult.json(
                        201,
                        alcoholSalesMappingRepository.insertJson(locationContext.id(), parseBody(body))
                );
            } catch (IllegalArgumentException e) {
                return ApiResult.json(400, Json.object(
                        "error", "bad_request",
                        "message", e.getMessage()
                ));
            } catch (SQLException e) {
                e.printStackTrace();
                return ApiResult.json(500, Json.object(
                        "error", "database_error",
                        "message", "Failed to save alcohol sales mapping."
                ));
            }
        }

        Integer alcoholMappingId = pathId(normalizedPath, "/alcohol-sales-mappings/");
        if (alcoholMappingId != null) {
            ApiResult unauthorized = requireApiKey(headers);
            if (unauthorized != null) {
                return unauthorized;
            }

            if ("PUT".equalsIgnoreCase(method)) {
                try {
                    LocationContext locationContext = requireLocationContext(headers);
                    if (locationContext == null) {
                        return locationUnauthorized();
                    }

                    String json = alcoholSalesMappingRepository.updateJson(
                            locationContext.id(),
                            alcoholMappingId,
                            parseBody(body)
                    );
                    return json.isBlank()
                            ? ApiResult.json(404, Json.object(
                            "error", "not_found",
                            "message", "Alcohol sales mapping not found."
                    ))
                            : ApiResult.json(200, json);
                } catch (IllegalArgumentException e) {
                    return ApiResult.json(400, Json.object(
                            "error", "bad_request",
                            "message", e.getMessage()
                    ));
                } catch (SQLException e) {
                    e.printStackTrace();
                    return ApiResult.json(500, Json.object(
                            "error", "database_error",
                            "message", "Failed to save alcohol sales mapping."
                    ));
                }
            }

            if ("POST".equalsIgnoreCase(method)
                    && normalizedPath.endsWith("/deactivate")) {
                try {
                    LocationContext locationContext = requireLocationContext(headers);
                    if (locationContext == null) {
                        return locationUnauthorized();
                    }

                    return alcoholSalesMappingRepository.deactivate(locationContext.id(), alcoholMappingId)
                            ? ApiResult.json(200, Json.object(
                            "status", "ok",
                            "message", "Alcohol sales mapping deactivated."
                    ))
                            : ApiResult.json(404, Json.object(
                            "error", "not_found",
                            "message", "Alcohol sales mapping not found."
                    ));
                } catch (SQLException e) {
                    e.printStackTrace();
                    return ApiResult.json(500, Json.object(
                            "error", "database_error",
                            "message", "Failed to deactivate alcohol sales mapping."
                    ));
                }
            }
        }

        ApiResult reportingResult = handleReportingRoute(method, normalizedPath, headers, body);
        if (reportingResult != null) {
            return reportingResult;
        }

        ApiResult adminSyncResult = handleAdminSyncRoute(method, normalizedPath, headers, body);
        if (adminSyncResult != null) {
            return adminSyncResult;
        }

        return ApiResult.json(404, Json.object(
                "error", "not_found",
                "message", "Endpoint not found."
        ));
    }

    private ApiResult handleAdminSyncRoute(
            String method,
            String path,
            Map<String, List<String>> headers,
            String body
    ) {
        if (!path.startsWith("/admin/sync/")) {
            return null;
        }

        ApiResult unauthorized = requireApiKey(headers);
        if (unauthorized != null) {
            return unauthorized;
        }

        try {
            if ("GET".equalsIgnoreCase(method) && "/admin/sync/download".equals(path)) {
                return ApiResult.json(200, adminSyncRepository.downloadSnapshotJson());
            }

            if ("POST".equalsIgnoreCase(method) && "/admin/sync/upload".equals(path)) {
                return ApiResult.json(200, adminSyncRepository.uploadSnapshot(parseBody(body)));
            }

            return ApiResult.json(404, Json.object(
                    "error", "not_found",
                    "message", "Endpoint not found."
            ));
        } catch (IllegalArgumentException e) {
            return ApiResult.json(400, Json.object(
                    "error", "bad_request",
                    "message", e.getMessage()
            ));
        } catch (IllegalStateException e) {
            return ApiResult.json(503, Json.object(
                    "error", "database_not_configured",
                    "message", e.getMessage()
            ));
        } catch (SQLException e) {
            e.printStackTrace();
            return ApiResult.json(500, Json.object(
                    "error", "database_error",
                    "message", "Failed to process admin sync request."
            ));
        }
    }

    private ApiResult handleLabourRoute(
            String method,
            String path,
            Map<String, List<String>> headers,
            String body
    ) {
        if (!path.startsWith("/labour/")) {
            return null;
        }

        ApiResult unauthorized = requireApiKey(headers);
        if (unauthorized != null) {
            return unauthorized;
        }

        LocationContext locationContext;
        try {
            locationContext = requireLocationContext(headers);
        } catch (IllegalStateException e) {
            return ApiResult.json(503, Json.object(
                    "error", "database_not_configured",
                    "message", e.getMessage()
            ));
        } catch (SQLException e) {
            e.printStackTrace();
            return databaseError("Failed to validate location session.");
        }
        if (locationContext == null) {
            return ApiResult.json(401, Json.object(
                    "error", "location_unauthorized",
                    "message", "A valid location login is required."
            ));
        }

        try {
            if ("GET".equalsIgnoreCase(method) && "/labour/weeks".equals(path)) {
                return ApiResult.json(200, labourRepository.savedLabourWeeksJson(locationContext.id()));
            }

            String weeklyStart = pathDate(path, "/labour/weekly/");
            if ("GET".equalsIgnoreCase(method) && weeklyStart != null) {
                return ApiResult.json(200, labourRepository.weeklyLabourJson(locationContext.id(), weeklyStart));
            }
            if ("PUT".equalsIgnoreCase(method) && "/labour/weekly".equals(path)) {
                labourRepository.saveWeeklyLabour(locationContext.id(), parseBody(body));
                return ApiResult.json(200, Json.object("status", "ok", "message", "Weekly labour saved."));
            }
            String dailyDate = pathDate(path, "/labour/daily/");
            if ("GET".equalsIgnoreCase(method) && dailyDate != null) {
                return ApiResult.json(200, labourRepository.dailyLabourJson(locationContext.id(), dailyDate));
            }
            if ("PUT".equalsIgnoreCase(method) && "/labour/daily".equals(path)) {
                labourRepository.saveDailyLabour(locationContext.id(), parseBody(body));
                return ApiResult.json(200, Json.object("status", "ok", "message", "Daily labour saved."));
            }

            if ("GET".equalsIgnoreCase(method) && "/labour/positions".equals(path)) {
                return ApiResult.json(200, labourRepository.findPositionsJson(locationContext.id(), false));
            }
            if ("GET".equalsIgnoreCase(method) && "/labour/positions/active".equals(path)) {
                return ApiResult.json(200, labourRepository.findPositionsJson(locationContext.id(), true));
            }
            if ("POST".equalsIgnoreCase(method) && "/labour/positions".equals(path)) {
                return ApiResult.json(201, labourRepository.savePositionJson(locationContext.id(), parseBody(body)));
            }
            Integer positionId = pathId(path, "/labour/positions/");
            if (positionId != null) {
                if ("PUT".equalsIgnoreCase(method)) {
                    return ApiResult.json(200, labourRepository.savePositionJson(locationContext.id(), parseBody(body)));
                }
                if ("POST".equalsIgnoreCase(method) && path.endsWith("/deactivate")) {
                    return okOrNotFound(
                            labourRepository.deactivatePosition(locationContext.id(), positionId),
                            "Labour position deactivated.",
                            "Labour position not found."
                    );
                }
            }

            if ("GET".equalsIgnoreCase(method) && "/labour/employees".equals(path)) {
                return ApiResult.json(200, labourRepository.findEmployeesJson(locationContext.id()));
            }
            if ("POST".equalsIgnoreCase(method) && "/labour/employees".equals(path)) {
                return ApiResult.json(201, labourRepository.saveEmployeeJson(locationContext.id(), parseBody(body)));
            }
            Integer employeeId = pathId(path, "/labour/employees/");
            if (employeeId != null) {
                if ("PUT".equalsIgnoreCase(method)) {
                    return ApiResult.json(200, labourRepository.saveEmployeeJson(locationContext.id(), parseBody(body)));
                }
                if ("POST".equalsIgnoreCase(method) && path.endsWith("/deactivate")) {
                    return okOrNotFound(
                            labourRepository.deactivateEmployee(locationContext.id(), employeeId),
                            "Labour employee deactivated.",
                            "Labour employee not found."
                    );
                }
            }

            if ("GET".equalsIgnoreCase(method) && "/labour/settings".equals(path)) {
                return ApiResult.json(200, labourRepository.settingsJson());
            }
            if ("PUT".equalsIgnoreCase(method) && "/labour/settings".equals(path)) {
                labourRepository.saveSettings(parseBody(body));
                return ApiResult.json(200, Json.object("status", "ok", "message", "Labour settings saved."));
            }
        } catch (IllegalArgumentException e) {
            return badRequest(e);
        } catch (IllegalStateException e) {
            return ApiResult.json(503, Json.object(
                    "error", "database_not_configured",
                    "message", e.getMessage()
            ));
        } catch (SQLException e) {
            e.printStackTrace();
            return databaseError("Failed to process labour request.");
        }

        return null;
    }

    private ApiResult handleReportingRoute(
            String method,
            String path,
            Map<String, List<String>> headers,
            String body
    ) {
        if (!path.startsWith("/reporting/")
                && !path.startsWith("/sales-periods")) {
            return null;
        }

        ApiResult unauthorized = requireApiKey(headers);
        if (unauthorized != null) {
            return unauthorized;
        }

        try {
            LocationContext locationContext = requireLocationContext(headers);
            if (locationContext == null) {
                return locationUnauthorized();
            }
            if ("GET".equalsIgnoreCase(method) && "/reporting/invoices".equals(path)) {
                return ApiResult.json(200, reportingRepository.findInvoicesJson(locationContext.id()));
            }

            Integer invoiceId = pathId(path, "/reporting/invoices/");
            if (invoiceId != null) {
                if ("GET".equalsIgnoreCase(method) && path.endsWith("/lines")) {
                    return ApiResult.json(
                            200,
                            reportingRepository.findInvoiceLinesJson(locationContext.id(), invoiceId)
                    );
                }
                if ("GET".equalsIgnoreCase(method) && path.endsWith("/breakdown")) {
                    return ApiResult.json(
                            200,
                            reportingRepository.findInvoiceBreakdownJson(locationContext.id(), invoiceId)
                    );
                }
                if ("DELETE".equalsIgnoreCase(method)) {
                    return okOrNotFound(
                            reportingRepository.deleteInvoice(locationContext.id(), invoiceId),
                            "Invoice deleted.",
                            "Invoice not found."
                    );
                }
            }

            if ("GET".equalsIgnoreCase(method) && "/sales-periods".equals(path)) {
                return ApiResult.json(200, reportingRepository.findSalesPeriodsJson(locationContext.id()));
            }
            if ("POST".equalsIgnoreCase(method) && "/sales-periods".equals(path)) {
                reportingRepository.saveSalesPeriod(locationContext.id(), parseBody(body));
                return ApiResult.json(201, Json.object(
                        "status", "ok",
                        "message", "Sales period saved."
                ));
            }

            Integer valuationCountId = pathId(path, "/reporting/inventory-valuations/");
            if (valuationCountId != null && "GET".equalsIgnoreCase(method)) {
                return ApiResult.json(
                        200,
                        reportingRepository.calculateValuationJson(locationContext.id(), valuationCountId)
                );
            }

            int[] weeklyCostIds = twoPathIds(path, "/reporting/weekly-cost/");
            if (weeklyCostIds != null && "GET".equalsIgnoreCase(method)) {
                return ApiResult.json(
                        200,
                        reportingRepository.weeklyCostReportTextJson(
                                locationContext.id(),
                                weeklyCostIds[0],
                                weeklyCostIds[1]
                        )
                );
            }
        } catch (IllegalArgumentException e) {
            return badRequest(e);
        } catch (IllegalStateException e) {
            return ApiResult.json(503, Json.object(
                    "error", "database_not_configured",
                    "message", e.getMessage()
            ));
        } catch (SQLException e) {
            e.printStackTrace();
            return databaseError("Failed to process reporting request.");
        }

        return null;
    }

    private ApiResult handleProductionRoute(
            String method,
            String path,
            Map<String, List<String>> headers,
            String body
    ) {
        if (!path.startsWith("/production/")
                && !path.startsWith("/pos-menu-items/")) {
            return null;
        }

        ApiResult unauthorized = requireApiKey(headers);
        if (unauthorized != null) {
            return unauthorized;
        }

        try {
            LocationContext locationContext = requireLocationContext(headers);
            if (locationContext == null) {
                return locationUnauthorized();
            }
            int locationId = locationContext.id();

            if ("GET".equalsIgnoreCase(method) && "/production/stations".equals(path)) {
                return ApiResult.json(200, productionRepository.findStationsJson(locationId, false));
            }
            if ("GET".equalsIgnoreCase(method) && "/production/stations/active".equals(path)) {
                return ApiResult.json(200, productionRepository.findStationsJson(locationId, true));
            }
            if ("POST".equalsIgnoreCase(method) && "/production/stations".equals(path)) {
                return ApiResult.json(201, productionRepository.saveStationJson(locationId, parseBody(body)));
            }
            Integer stationId = pathId(path, "/production/stations/");
            if (stationId != null) {
                if ("PUT".equalsIgnoreCase(method)) {
                    return ApiResult.json(200, productionRepository.saveStationJson(locationId, parseBody(body)));
                }
                if ("POST".equalsIgnoreCase(method) && path.endsWith("/deactivate")) {
                    return okOrNotFound(
                            productionRepository.deactivateStation(locationId, stationId),
                            "Production station deactivated.",
                            "Production station not found."
                    );
                }
            }

            if ("GET".equalsIgnoreCase(method) && "/production/items".equals(path)) {
                return ApiResult.json(200, productionRepository.findProductionItemsJson(locationId, false));
            }
            if ("GET".equalsIgnoreCase(method) && "/production/items/active".equals(path)) {
                return ApiResult.json(200, productionRepository.findProductionItemsJson(locationId, true));
            }
            if ("POST".equalsIgnoreCase(method) && "/production/items".equals(path)) {
                return ApiResult.json(201, productionRepository.saveProductionItemJson(locationId, parseBody(body)));
            }
            Integer itemId = pathId(path, "/production/items/");
            if (itemId != null) {
                if ("PUT".equalsIgnoreCase(method) && path.endsWith("/permanent-override-par")) {
                    return okOrNotFound(
                            productionRepository.updatePermanentOverridePar(locationId, itemId, parseBody(body)),
                            "Permanent override saved.",
                            "Production item not found."
                    );
                }
                if ("PUT".equalsIgnoreCase(method)) {
                    return ApiResult.json(200, productionRepository.saveProductionItemJson(locationId, parseBody(body)));
                }
                if ("POST".equalsIgnoreCase(method) && path.endsWith("/deactivate")) {
                    return okOrNotFound(
                            productionRepository.deactivateProductionItem(locationId, itemId),
                            "Production item deactivated.",
                            "Production item not found."
                    );
                }
            }

            if ("GET".equalsIgnoreCase(method) && "/production/profiles".equals(path)) {
                return ApiResult.json(200, productionRepository.findProfilesJson(locationId, false));
            }
            if ("GET".equalsIgnoreCase(method) && "/production/profiles/active".equals(path)) {
                return ApiResult.json(200, productionRepository.findProfilesJson(locationId, true));
            }
            if ("POST".equalsIgnoreCase(method) && "/production/profiles".equals(path)) {
                return ApiResult.json(201, productionRepository.saveProfileJson(locationId, parseBody(body)));
            }
            Integer profileId = pathId(path, "/production/profiles/");
            if (profileId != null) {
                if ("GET".equalsIgnoreCase(method) && path.endsWith("/lines")) {
                    return ApiResult.json(200, productionRepository.findProfileLinesJson(locationId, profileId));
                }
                if ("PUT".equalsIgnoreCase(method) && path.endsWith("/lines")) {
                    productionRepository.replaceProfileLines(locationId, profileId, parseBodyArray(body));
                    return ApiResult.json(200, Json.object("status", "ok", "message", "Profile lines saved."));
                }
                if ("PUT".equalsIgnoreCase(method)) {
                    return ApiResult.json(200, productionRepository.saveProfileJson(locationId, parseBody(body)));
                }
                if ("POST".equalsIgnoreCase(method) && path.endsWith("/deactivate")) {
                    return okOrNotFound(
                            productionRepository.deactivateProfile(locationId, profileId),
                            "Production profile deactivated.",
                            "Production profile not found."
                    );
                }
            }

            if ("GET".equalsIgnoreCase(method) && "/production/pos-menu-items".equals(path)) {
                return ApiResult.json(200, productionRepository.findPosMenuItemsJson(locationId, false));
            }
            if ("GET".equalsIgnoreCase(method) && "/production/pos-menu-items/active".equals(path)) {
                return ApiResult.json(200, productionRepository.findPosMenuItemsJson(locationId, true));
            }
            if ("POST".equalsIgnoreCase(method) && "/production/pos-menu-items".equals(path)) {
                return ApiResult.json(201, productionRepository.savePosMenuItemJson(locationId, parseBody(body)));
            }
            if ("POST".equalsIgnoreCase(method) && "/production/pos-menu-items/import".equals(path)) {
                return ApiResult.json(200, productionRepository.upsertPosMenuItemsJson(locationId, parseBodyArray(body)));
            }
            if ("POST".equalsIgnoreCase(method) && "/production/pos-menu-items/delete-by-skus".equals(path)) {
                int deleted = productionRepository.deletePosMenuItemsBySkus(locationId, parseBodyArray(body));
                return ApiResult.json(200, "{\"deleted\":" + deleted + "}");
            }
            Integer posItemId = pathId(path, "/production/pos-menu-items/");
            if (posItemId != null) {
                if ("PUT".equalsIgnoreCase(method)) {
                    return ApiResult.json(200, productionRepository.savePosMenuItemJson(locationId, parseBody(body)));
                }
                if ("POST".equalsIgnoreCase(method) && path.endsWith("/deactivate")) {
                    return okOrNotFound(
                            productionRepository.deactivatePosMenuItem(locationId, posItemId),
                            "POS menu item deactivated.",
                            "POS menu item not found."
                    );
                }
            }
            Integer legacyPosItemId = pathId(path, "/pos-menu-items/");
            if (legacyPosItemId != null) {
                if ("PUT".equalsIgnoreCase(method)) {
                    return ApiResult.json(200, productionRepository.savePosMenuItemJson(locationId, parseBody(body)));
                }
                if ("POST".equalsIgnoreCase(method) && path.endsWith("/deactivate")) {
                    return okOrNotFound(
                            productionRepository.deactivatePosMenuItem(locationId, legacyPosItemId),
                            "POS menu item deactivated.",
                            "POS menu item not found."
                    );
                }
            }

            if ("GET".equalsIgnoreCase(method) && "/production/product-mappings".equals(path)) {
                return ApiResult.json(200, productionRepository.findProductMappingsJson(locationId));
            }
            if ("POST".equalsIgnoreCase(method) && "/production/product-mappings".equals(path)) {
                return ApiResult.json(201, productionRepository.saveProductMappingJson(locationId, parseBody(body)));
            }
            Integer productMappingId = pathId(path, "/production/product-mappings/");
            if (productMappingId != null) {
                if ("PUT".equalsIgnoreCase(method)) {
                    return ApiResult.json(200, productionRepository.saveProductMappingJson(locationId, parseBody(body)));
                }
                if ("POST".equalsIgnoreCase(method) && path.endsWith("/deactivate")) {
                    return okOrNotFound(
                            productionRepository.deactivateProductMapping(locationId, productMappingId),
                            "Product mapping deactivated.",
                            "Product mapping not found."
                    );
                }
            }

            if ("GET".equalsIgnoreCase(method) && "/production/freezer-pull/lines".equals(path)) {
                return ApiResult.json(200, productionRepository.findFreezerPullLinesJson(locationId));
            }
            if ("PUT".equalsIgnoreCase(method) && "/production/freezer-pull/par".equals(path)) {
                productionRepository.saveFreezerPullPar(locationId, parseBody(body));
                return ApiResult.json(200, Json.object("status", "ok", "message", "Freezer Pull par saved."));
            }

            if ("GET".equalsIgnoreCase(method) && "/production/weeks".equals(path)) {
                return ApiResult.json(200, productionRepository.findWeeksJson(locationId));
            }
            if ("POST".equalsIgnoreCase(method) && "/production/weeks/generate".equals(path)) {
                int weekId = productionRepository.saveGeneratedWeek(locationId, parseBody(body));
                return ApiResult.json(201, "{\"productionWeekId\":" + weekId + "}");
            }
            if ("PUT".equalsIgnoreCase(method) && path.endsWith("/refresh")) {
                productionRepository.refreshWeek(locationId, parseBody(body));
                return ApiResult.json(200, Json.object("status", "ok", "message", "Week refreshed."));
            }
            Integer weekId = pathId(path, "/production/weeks/");
            if (weekId != null) {
                if ("GET".equalsIgnoreCase(method) && path.endsWith("/days")) {
                    return ApiResult.json(200, productionRepository.findWeekDaysJson(locationId, weekId));
                }
                if ("GET".equalsIgnoreCase(method) && path.endsWith("/lines")) {
                    return ApiResult.json(200, productionRepository.findWeekLinesJson(locationId, weekId));
                }
            }
            if ("PUT".equalsIgnoreCase(method) && "/production/week-lines/overrides".equals(path)) {
                productionRepository.updateWeekLineOverrides(locationId, parseBodyArray(body));
                return ApiResult.json(200, Json.object("status", "ok", "message", "Overrides saved."));
            }
        } catch (IllegalArgumentException e) {
            return badRequest(e);
        } catch (IllegalStateException e) {
            return ApiResult.json(503, Json.object(
                    "error", "database_not_configured",
                    "message", e.getMessage()
            ));
        } catch (SQLException e) {
            e.printStackTrace();
            return databaseError("Failed to process production request.");
        }

        return null;
    }

    private ApiResult requireApiKey(Map<String, List<String>> headers) {
        String configuredApiKey = ApiConfig.apiKey();
        if (configuredApiKey.isBlank()) {
            return null;
        }

        String requestApiKey = headerValue(headers, "x-api-key");
        if (configuredApiKey.equals(requestApiKey)) {
            return null;
        }

        return ApiResult.json(401, Json.object(
                "error", "unauthorized",
                "message", "A valid API key is required."
        ));
    }

    private LocationContext requireLocationContext(
            Map<String, List<String>> headers
    ) throws SQLException {
        if (!ApiConfig.locationAuthRequired()) {
            return new LocationContext(1, "STORE", "Existing Store", "store");
        }

        return locationAuthRepository.resolveSession(headerValue(headers, "x-location-token"));
    }

    private ApiResult badRequest(IllegalArgumentException e) {
        return ApiResult.json(400, Json.object(
                "error", "bad_request",
                "message", e.getMessage()
        ));
    }

    private ApiResult databaseError(String message) {
        return ApiResult.json(500, Json.object(
                "error", "database_error",
                "message", message
        ));
    }

    private ApiResult locationUnauthorized() {
        return ApiResult.json(401, Json.object(
                "error", "location_unauthorized",
                "message", "A valid store login session is required."
        ));
    }

    private ApiResult okOrNotFound(
            boolean ok,
            String okMessage,
            String notFoundMessage
    ) {
        return ok
                ? ApiResult.json(200, Json.object(
                "status", "ok",
                "message", okMessage
        ))
                : ApiResult.json(404, Json.object(
                "error", "not_found",
                "message", notFoundMessage
        ));
    }

    private String headerValue(Map<String, List<String>> headers, String name) {
        if (headers == null || headers.isEmpty()) {
            return "";
        }

        for (Map.Entry<String, List<String>> entry : headers.entrySet()) {
            if (entry.getKey() != null && entry.getKey().equalsIgnoreCase(name)) {
                List<String> values = entry.getValue();
                return values == null || values.isEmpty() || values.getFirst() == null
                        ? ""
                        : values.getFirst();
            }
        }

        return "";
    }

    private String databaseErrorJson(SQLException e) {
        if (!ApiConfig.includeErrorDetails()) {
            return Json.object(
                    "error", "database_error",
                    "message", "Failed to load products."
            );
        }

        return Json.object(
                "error", "database_error",
                "message", "Failed to load products.",
                "detail", errorDetail(e)
        );
    }

    private String errorDetail(Throwable throwable) {
        StringBuilder detail = new StringBuilder();
        Throwable current = throwable;

        while (current != null) {
            if (!detail.isEmpty()) {
                detail.append(" | caused by ");
            }

            detail.append(current.getClass().getName())
                    .append(": ")
                    .append(current.getMessage());
            current = current.getCause();
        }

        return detail.toString();
    }

    private String normalizePath(String path) {
        if (path == null || path.isBlank()) {
            return "/";
        }

        String normalized = path.trim();
        if (normalized.length() > 1 && normalized.endsWith("/")) {
            return normalized.substring(0, normalized.length() - 1);
        }

        return normalized;
    }

    private Map<String, Object> parseBody(String body) {
        if (body == null || body.isBlank()) {
            throw new IllegalArgumentException("Request body is required.");
        }

        return new JsonObjectParser().parse(body);
    }

    private String requiredBodyString(Map<String, Object> body, String key) {
        Object value = body.get(key);
        if (value == null || value.toString().isBlank()) {
            throw new IllegalArgumentException(key + " is required.");
        }

        return value.toString().trim();
    }

    private String locationSessionJson(LocationSession session) {
        return "{"
                + "\"token\":" + Json.nullableString(session.token()) + ","
                + "\"location\":{"
                + "\"id\":" + session.locationId() + ","
                + "\"code\":" + Json.nullableString(session.locationCode()) + ","
                + "\"name\":" + Json.nullableString(session.locationName()) + ","
                + "\"username\":" + Json.nullableString(session.username())
                + "}"
                + "}";
    }

    private List<Map<String, Object>> parseBodyArray(String body) {
        if (body == null || body.isBlank()) {
            throw new IllegalArgumentException("Request body is required.");
        }

        return new JsonArrayParser().parseObjectArray(body);
    }

    private String stringBodyField(String body, String key) {
        Object value = parseBody(body).get(key);
        if (value == null || value.toString().isBlank()) {
            throw new IllegalArgumentException(key + " is required.");
        }

        return value.toString().trim();
    }

    private Integer pathId(String path, String prefix) {
        if (path == null || !path.startsWith(prefix)) {
            return null;
        }

        String remaining = path.substring(prefix.length());
        if (remaining.endsWith("/deactivate")) {
            remaining = remaining.substring(0, remaining.length() - "/deactivate".length());
        }
        if (remaining.endsWith("/purchase-history")) {
            remaining = remaining.substring(0, remaining.length() - "/purchase-history".length());
        }
        if (remaining.endsWith("/duplicate")) {
            remaining = remaining.substring(0, remaining.length() - "/duplicate".length());
        }
        if (remaining.endsWith("/order-guide-case-size")) {
            remaining = remaining.substring(
                    0,
                    remaining.length() - "/order-guide-case-size".length()
            );
        }
        if (remaining.endsWith("/permanent-override-par")) {
            remaining = remaining.substring(
                    0,
                    remaining.length() - "/permanent-override-par".length()
            );
        }
        if (remaining.endsWith("/lines")) {
            remaining = remaining.substring(0, remaining.length() - "/lines".length());
        }
        if (remaining.endsWith("/breakdown")) {
            remaining = remaining.substring(0, remaining.length() - "/breakdown".length());
        }
        if (remaining.endsWith("/days")) {
            remaining = remaining.substring(0, remaining.length() - "/days".length());
        }
        if (remaining.endsWith("/refresh")) {
            remaining = remaining.substring(0, remaining.length() - "/refresh".length());
        }
        if (remaining.endsWith("/deactivate")) {
            remaining = remaining.substring(0, remaining.length() - "/deactivate".length());
        }

        if (remaining.isBlank() || remaining.contains("/")) {
            return null;
        }

        try {
            return Integer.parseInt(remaining);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private String pathDate(String path, String prefix) {
        if (path == null || !path.startsWith(prefix)) {
            return null;
        }

        String remaining = path.substring(prefix.length());
        if (remaining.isBlank() || remaining.contains("/")) {
            return null;
        }

        return remaining;
    }

    private int[] twoPathIds(String path, String prefix) {
        if (path == null || !path.startsWith(prefix)) {
            return null;
        }

        String remaining = path.substring(prefix.length());
        String[] parts = remaining.split("/");
        if (parts.length != 2) {
            return null;
        }

        try {
            return new int[]{
                    Integer.parseInt(parts[0]),
                    Integer.parseInt(parts[1])
            };
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private DepartmentPath departmentPath(String path) {
        String prefix = "/departments/";
        if (path == null || !path.startsWith(prefix)) {
            return null;
        }

        String remaining = path.substring(prefix.length());
        String[] parts = remaining.split("/");
        if (parts.length != 2 || parts[0].isBlank() || parts[1].isBlank()) {
            return null;
        }

        return new DepartmentPath(parts[0].toUpperCase(), parts[1]);
    }

    private CountLinesPath countLinesPath(String path) {
        String prefix = "/inventory-counts/";
        if (path == null || !path.startsWith(prefix)) {
            return null;
        }

        String remaining = path.substring(prefix.length());
        String[] parts = remaining.split("/");
        if (parts.length < 1 || parts.length > 2 || parts[0].isBlank()) {
            return null;
        }

        try {
            int countId = Integer.parseInt(parts[0]);
            String action = parts.length == 2 ? parts[1] : "";
            return new CountLinesPath(countId, action);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private TemplateLinesPath templateLinesPath(String path) {
        String prefix = "/inventory-count-templates/";
        if (path == null || !path.startsWith(prefix)) {
            return null;
        }

        String remaining = path.substring(prefix.length());
        String[] parts = remaining.split("/");
        if (parts.length != 2 || parts[0].isBlank()) {
            return null;
        }

        try {
            return new TemplateLinesPath(Integer.parseInt(parts[0]), parts[1]);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private OrderGuidePath orderGuidePath(String path) {
        String prefix = "/order-guide/";
        if (path == null || !path.startsWith(prefix)) {
            return null;
        }

        String remaining = path.substring(prefix.length());
        String[] parts = remaining.split("/");
        if (parts.length != 2 || parts[0].isBlank() || parts[1].isBlank()) {
            return null;
        }

        try {
            return new OrderGuidePath(
                    Integer.parseInt(parts[0]),
                    Integer.parseInt(parts[1])
            );
        } catch (NumberFormatException e) {
            return null;
        }
    }

    record ApiResult(int statusCode, String contentType, String body) {

        static ApiResult json(int statusCode, String body) {
            return new ApiResult(statusCode, "application/json; charset=utf-8", body);
        }
    }

    record DepartmentPath(String department, String resource) {
    }

    record CountLinesPath(int countId, String action) {

        boolean countRequest() {
            return action == null || action.isBlank();
        }

        boolean linesRequest() {
            return "lines".equals(action);
        }

        boolean completeRequest() {
            return "complete".equals(action);
        }
    }

    record TemplateLinesPath(int templateId, String action) {

        boolean linesRequest() {
            return "lines".equals(action);
        }

        boolean sortRequest() {
            return "line-sort-orders".equals(action);
        }
    }

    record OrderGuidePath(int openingCountId, int closingCountId) {
    }
}
