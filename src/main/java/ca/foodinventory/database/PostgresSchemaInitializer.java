package ca.foodinventory.database;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

public class PostgresSchemaInitializer {

    public static final int CURRENT_SCHEMA_VERSION = 23;

    private static final String[] STORE_OWNED_TABLES = {
            "products",
            "product_sku_aliases",
            "invoices",
            "invoice_lines",
            "invoice_adjustments",
            "inventory_count_templates",
            "inventory_count_template_lines",
            "inventory_counts",
            "inventory_count_lines",
            "sales_periods",
            "alcohol_product_profiles",
            "alcohol_sales_mappings",
            "production_stations",
            "production_items",
            "production_profiles",
            "production_profile_lines",
            "pos_menu_items",
            "production_item_product_mappings",
            "production_weeks",
            "production_week_days",
            "production_week_lines"
    };

    public void initialize(Connection connection) throws SQLException {
        try (Statement statement = connection.createStatement()) {
            createBaseTables(statement);
            createProductionTables(statement);
            repairSchemaCompatibility(connection, statement);
            createIndexes(statement);
            seedReferenceData(statement);
            setSchemaVersion(statement);
        }
    }

    public void validateRequiredSchema(Connection connection) throws SQLException {
        requireTable(connection, "products");
        requireTable(connection, "product_sku_aliases");
        requireTable(connection, "inventory_count_template_lines");
        requireTable(connection, "inventory_counts");
        requireTable(connection, "inventory_count_lines");
        requireTable(connection, "invoices");
        requireTable(connection, "invoice_lines");
        requireTable(connection, "invoice_adjustments");
        requireTable(connection, "sales_periods");
        requireTable(connection, "sales_category_mappings");
        requireTable(connection, "settings");
        requireTable(connection, "alcohol_product_profiles");
        requireTable(connection, "production_stations");
        requireTable(connection, "production_items");
        requireTable(connection, "production_profiles");
        requireTable(connection, "production_profile_lines");
        requireTable(connection, "pos_menu_items");
        requireTable(connection, "production_item_product_mappings");
        requireTable(connection, "production_weeks");
        requireTable(connection, "production_week_days");
        requireTable(connection, "production_week_lines");
        requireTable(connection, "labour_positions");
        requireTable(connection, "labour_employees");
        requireTable(connection, "labour_employee_pay_rates");
        requireTable(connection, "labour_daily_sales");
        requireTable(connection, "labour_daily_entries");
        requireTable(connection, "locations");
        requireTable(connection, "location_sessions");
        requireColumn(connection, "locations", "admin_password_hash");
        requireColumn(connection, "locations", "labour_setup_password_hash");
        requireTable(connection, "schema_version");
        for (String tableName : STORE_OWNED_TABLES) {
            requireColumn(connection, tableName, "location_id");
        }
        requireColumn(connection, "labour_positions", "location_id");
        requireColumn(connection, "labour_employees", "location_id");
        requireColumn(connection, "labour_daily_sales", "location_id");
        requireColumn(connection, "labour_daily_entries", "location_id");
        requireColumn(connection, "labour_daily_entries", "employee_name_snapshot");
        requireColumn(connection, "labour_daily_entries", "position_name_snapshot");
        requireColumn(connection, "labour_daily_entries", "labour_group_snapshot");
        requireColumn(connection, "inventory_count_template_lines", "order_guide_case_size");
        requireColumn(connection, "production_items", "yield_factor");
        requireColumn(connection, "production_profile_lines", "yield_factor");
        requireColumn(connection, "sales_periods", "food_net_sales");
        requireColumn(connection, "sales_periods", "beer_net_sales");
        requireColumn(connection, "sales_periods", "wine_net_sales");
        requireColumn(connection, "sales_periods", "draught_net_sales");
        requireColumn(connection, "sales_periods", "import_draught_net_sales");
        requireColumn(connection, "sales_periods", "liquor_net_sales");
    }

    private void createBaseTables(Statement statement) throws SQLException {
        statement.execute("""
                CREATE TABLE IF NOT EXISTS products (
                    id SERIAL PRIMARY KEY,
                    location_id INTEGER NOT NULL DEFAULT 1,
                    sku TEXT NOT NULL,
                    description TEXT NOT NULL,
                    category TEXT DEFAULT 'Uncategorized',
                    reporting_category TEXT DEFAULT 'OTHER',
                    unit TEXT DEFAULT 'EA',
                    conversion_factor DOUBLE PRECISION DEFAULT 1,
                    pack_size TEXT,
                    pack_count TEXT,
                    last_case_cost TEXT,
                    last_purchased_date TEXT,
                    active INTEGER DEFAULT 1,
                    CONSTRAINT products_location_sku_key UNIQUE(location_id, sku)
                )
                """);

        statement.execute("""
                CREATE TABLE IF NOT EXISTS product_sku_aliases (
                    id SERIAL PRIMARY KEY,
                    location_id INTEGER NOT NULL DEFAULT 1,
                    product_id INTEGER NOT NULL REFERENCES products(id),
                    supplier TEXT NOT NULL,
                    sku TEXT NOT NULL,
                    description TEXT,
                    pack_size TEXT,
                    active INTEGER DEFAULT 1,
                    CONSTRAINT product_sku_aliases_location_sku_key UNIQUE(location_id, sku)
                )
                """);

        statement.execute("""
                CREATE TABLE IF NOT EXISTS invoices (
                    id SERIAL PRIMARY KEY,
                    invoice_number TEXT NOT NULL,
                    supplier TEXT NOT NULL,
                    invoice_date TEXT NOT NULL,
                    imported_total NUMERIC NOT NULL DEFAULT 0,
                    merchandise_subtotal NUMERIC NOT NULL DEFAULT 0,
                    freight NUMERIC NOT NULL DEFAULT 0,
                    hst NUMERIC NOT NULL DEFAULT 0,
                    invoice_total TEXT DEFAULT '0.00'
                )
                """);

        statement.execute("""
                CREATE TABLE IF NOT EXISTS invoice_lines (
                    id SERIAL PRIMARY KEY,
                    invoice_id INTEGER NOT NULL REFERENCES invoices(id),
                    product_id INTEGER REFERENCES products(id),
                    quantity DOUBLE PRECISION,
                    base_quantity DOUBLE PRECISION DEFAULT 0,
                    pack_size TEXT,
                    case_cost DOUBLE PRECISION,
                    extended_cost DOUBLE PRECISION
                )
                """);

        statement.execute("""
                CREATE TABLE IF NOT EXISTS invoice_adjustments (
                    id SERIAL PRIMARY KEY,
                    invoice_id INTEGER NOT NULL REFERENCES invoices(id) ON DELETE CASCADE,
                    description TEXT NOT NULL,
                    amount NUMERIC NOT NULL DEFAULT 0,
                    display_order INTEGER NOT NULL DEFAULT 0
                )
                """);

        statement.execute("""
                CREATE TABLE IF NOT EXISTS inventory_count_templates (
                    id SERIAL PRIMARY KEY,
                    name TEXT NOT NULL,
                    active INTEGER DEFAULT 1
                )
                """);

        statement.execute("""
                CREATE TABLE IF NOT EXISTS inventory_count_template_lines (
                    id SERIAL PRIMARY KEY,
                    template_id INTEGER NOT NULL REFERENCES inventory_count_templates(id),
                    product_id INTEGER NOT NULL REFERENCES products(id),
                    section_name TEXT,
                    sort_order INTEGER DEFAULT 0,
                    count_unit TEXT DEFAULT 'EA',
                    conversion_factor_to_base DOUBLE PRECISION DEFAULT 1,
                    display_name TEXT,
                    order_guide_case_size TEXT,
                    active INTEGER DEFAULT 1
                )
                """);

        statement.execute("""
                CREATE TABLE IF NOT EXISTS inventory_counts (
                    id SERIAL PRIMARY KEY,
                    template_id INTEGER NOT NULL REFERENCES inventory_count_templates(id),
                    count_date TEXT NOT NULL,
                    period_start_date TEXT,
                    period_end_date TEXT,
                    notes TEXT,
                    completed INTEGER DEFAULT 0
                )
                """);

        statement.execute("""
                CREATE TABLE IF NOT EXISTS inventory_count_lines (
                    id SERIAL PRIMARY KEY,
                    count_id INTEGER NOT NULL REFERENCES inventory_counts(id),
                    product_id INTEGER NOT NULL REFERENCES products(id),
                    quantity DOUBLE PRECISION NOT NULL DEFAULT 0,
                    count_unit TEXT,
                    converted_quantity DOUBLE PRECISION DEFAULT 0,
                    conversion_factor DOUBLE PRECISION DEFAULT 1
                )
                """);

        statement.execute("""
                CREATE TABLE IF NOT EXISTS sales_periods (
                    id SERIAL PRIMARY KEY,
                    location_id INTEGER NOT NULL DEFAULT 1,
                    period_start_date TEXT NOT NULL,
                    period_end_date TEXT NOT NULL,
                    food_sales TEXT DEFAULT '0.00',
                    beer_sales TEXT DEFAULT '0.00',
                    wine_sales TEXT DEFAULT '0.00',
                    draught_sales TEXT DEFAULT '0.00',
                    import_draught_sales TEXT DEFAULT '0.00',
                    liquor_sales TEXT DEFAULT '0.00',
                    food_net_sales TEXT DEFAULT '0.00',
                    beer_net_sales TEXT DEFAULT '0.00',
                    wine_net_sales TEXT DEFAULT '0.00',
                    draught_net_sales TEXT DEFAULT '0.00',
                    import_draught_net_sales TEXT DEFAULT '0.00',
                    liquor_net_sales TEXT DEFAULT '0.00',
                    created_at TEXT DEFAULT CURRENT_TIMESTAMP,
                    CONSTRAINT sales_periods_location_dates_key
                        UNIQUE(location_id, period_start_date, period_end_date)
                )
                """);

        statement.execute("""
                CREATE TABLE IF NOT EXISTS sales_category_mappings (
                    id SERIAL PRIMARY KEY,
                    pos_category TEXT NOT NULL UNIQUE,
                    reporting_category TEXT NOT NULL,
                    active INTEGER NOT NULL DEFAULT 1
                )
                """);

        statement.execute("""
                CREATE TABLE IF NOT EXISTS settings (
                    setting_key TEXT PRIMARY KEY,
                    setting_value TEXT NOT NULL
                )
                """);

        statement.execute("""
                CREATE TABLE IF NOT EXISTS alcohol_product_profiles (
                    id SERIAL PRIMARY KEY,
                    location_id INTEGER NOT NULL DEFAULT 1,
                    product_id INTEGER NOT NULL UNIQUE REFERENCES products(id),
                    count_method TEXT NOT NULL,
                    container_type TEXT,
                    measurement_unit TEXT NOT NULL,
                    tare_weight DOUBLE PRECISION DEFAULT 0,
                    full_content_weight DOUBLE PRECISION DEFAULT 0,
                    active INTEGER DEFAULT 1
                )
                """);

        statement.execute("""
                CREATE TABLE IF NOT EXISTS alcohol_sales_mappings (
                    id SERIAL PRIMARY KEY,
                    location_id INTEGER NOT NULL DEFAULT 1,
                    pos_sku TEXT NOT NULL,
                    pos_item_name TEXT,
                    reporting_category TEXT NOT NULL,
                    product_id INTEGER NOT NULL REFERENCES products(id),
                    quantity_per_sale DOUBLE PRECISION NOT NULL DEFAULT 0,
                    unit TEXT NOT NULL,
                    active INTEGER NOT NULL DEFAULT 1,
                    CONSTRAINT alcohol_sales_mappings_location_pos_sku_key UNIQUE(location_id, pos_sku)
                )
                """);

        statement.execute("""
                CREATE TABLE IF NOT EXISTS locations (
                    id SERIAL PRIMARY KEY,
                    code TEXT NOT NULL UNIQUE,
                    name TEXT NOT NULL,
                    username TEXT NOT NULL UNIQUE,
                    password_hash TEXT,
                    password_salt TEXT,
                    password_iterations INTEGER NOT NULL DEFAULT 600000,
                    admin_password_hash TEXT,
                    admin_password_salt TEXT,
                    admin_password_iterations INTEGER NOT NULL DEFAULT 600000,
                    labour_setup_password_hash TEXT,
                    labour_setup_password_salt TEXT,
                    labour_setup_password_iterations INTEGER NOT NULL DEFAULT 600000,
                    active INTEGER NOT NULL DEFAULT 1,
                    created_at TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP
                )
                """);

        statement.execute("""
                CREATE TABLE IF NOT EXISTS labour_positions (
                    id SERIAL PRIMARY KEY,
                    location_id INTEGER NOT NULL DEFAULT 1 REFERENCES locations(id),
                    name TEXT NOT NULL,
                    labour_group TEXT NOT NULL,
                    sort_order INTEGER NOT NULL DEFAULT 0,
                    target_labour_percentage NUMERIC,
                    active INTEGER NOT NULL DEFAULT 1,
                    CONSTRAINT production_stations_location_name_key UNIQUE(location_id, name)
                )
                """);

        statement.execute("""
                CREATE TABLE IF NOT EXISTS labour_employees (
                    id SERIAL PRIMARY KEY,
                    location_id INTEGER NOT NULL DEFAULT 1 REFERENCES locations(id),
                    name TEXT NOT NULL,
                    position_id INTEGER NOT NULL REFERENCES labour_positions(id),
                    hourly_wage NUMERIC NOT NULL DEFAULT 0,
                    tip_pool_eligible INTEGER NOT NULL DEFAULT 0,
                    uniform_deduction_applicable INTEGER NOT NULL DEFAULT 0,
                    active INTEGER NOT NULL DEFAULT 1
                )
                """);

        statement.execute("""
                CREATE TABLE IF NOT EXISTS labour_employee_pay_rates (
                    id SERIAL PRIMARY KEY,
                    location_id INTEGER NOT NULL DEFAULT 1 REFERENCES locations(id),
                    employee_id INTEGER NOT NULL REFERENCES labour_employees(id),
                    hourly_wage NUMERIC NOT NULL DEFAULT 0,
                    effective_date TEXT NOT NULL,
                    created_at TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP,
                    UNIQUE(location_id, employee_id, effective_date)
                )
                """);

        statement.execute("""
                CREATE TABLE IF NOT EXISTS labour_daily_sales (
                    id SERIAL PRIMARY KEY,
                    location_id INTEGER NOT NULL DEFAULT 1 REFERENCES locations(id),
                    sales_date TEXT NOT NULL,
                    net_sales NUMERIC NOT NULL DEFAULT 0,
                    tip_out_pool NUMERIC NOT NULL DEFAULT 0,
                    finalized INTEGER NOT NULL DEFAULT 0,
                    UNIQUE(location_id, sales_date)
                )
                """);

        statement.execute("""
                CREATE TABLE IF NOT EXISTS labour_daily_entries (
                    id SERIAL PRIMARY KEY,
                    location_id INTEGER NOT NULL DEFAULT 1 REFERENCES locations(id),
                    work_date TEXT NOT NULL,
                    employee_id INTEGER NOT NULL REFERENCES labour_employees(id),
                    position_id INTEGER NOT NULL REFERENCES labour_positions(id),
                    hourly_wage NUMERIC NOT NULL DEFAULT 0,
                    shift_1_hours NUMERIC NOT NULL DEFAULT 0,
                    shift_2_hours NUMERIC NOT NULL DEFAULT 0,
                    employee_name_snapshot TEXT,
                    position_name_snapshot TEXT,
                    labour_group_snapshot TEXT,
                    finalized INTEGER NOT NULL DEFAULT 0,
                    UNIQUE(location_id, work_date, employee_id)
                )
                """);

        statement.execute("""
                CREATE TABLE IF NOT EXISTS location_sessions (
                    id SERIAL PRIMARY KEY,
                    location_id INTEGER NOT NULL REFERENCES locations(id),
                    token_hash TEXT NOT NULL UNIQUE,
                    created_at TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP,
                    expires_at TEXT NOT NULL,
                    revoked INTEGER NOT NULL DEFAULT 0
                )
                """);

        statement.execute("""
                CREATE TABLE IF NOT EXISTS schema_version (
                    version INTEGER NOT NULL
                )
                """);
    }

    private void createProductionTables(Statement statement) throws SQLException {
        statement.execute("""
                CREATE TABLE IF NOT EXISTS production_stations (
                    id SERIAL PRIMARY KEY,
                    location_id INTEGER NOT NULL DEFAULT 1,
                    name TEXT NOT NULL,
                    prep_sheet TEXT NOT NULL DEFAULT 'Main Line',
                    sort_order INTEGER NOT NULL DEFAULT 0,
                    active INTEGER NOT NULL DEFAULT 1,
                    CONSTRAINT production_items_location_name_key UNIQUE(location_id, name)
                )
                """);

        statement.execute("""
                CREATE TABLE IF NOT EXISTS production_items (
                    id SERIAL PRIMARY KEY,
                    location_id INTEGER NOT NULL DEFAULT 1,
                    name TEXT NOT NULL,
                    unit TEXT NOT NULL,
                    shelf_life TEXT,
                    yield_factor DOUBLE PRECISION NOT NULL DEFAULT 1.0,
                    station_id INTEGER REFERENCES production_stations(id),
                    print_order INTEGER NOT NULL DEFAULT 0,
                    permanent_override_par INTEGER,
                    active INTEGER NOT NULL DEFAULT 1,
                    CONSTRAINT production_profiles_location_name_key UNIQUE(location_id, name)
                )
                """);

        statement.execute("""
                CREATE TABLE IF NOT EXISTS production_profiles (
                    id SERIAL PRIMARY KEY,
                    location_id INTEGER NOT NULL DEFAULT 1,
                    name TEXT NOT NULL,
                    category TEXT,
                    active INTEGER NOT NULL DEFAULT 1,
                    UNIQUE(location_id, name)
                )
                """);

        statement.execute("""
                CREATE TABLE IF NOT EXISTS production_profile_lines (
                    id SERIAL PRIMARY KEY,
                    location_id INTEGER NOT NULL DEFAULT 1,
                    profile_id INTEGER NOT NULL REFERENCES production_profiles(id),
                    production_item_id INTEGER NOT NULL REFERENCES production_items(id),
                    quantity_per_sale DOUBLE PRECISION NOT NULL DEFAULT 0,
                    unit TEXT NOT NULL,
                    sort_order INTEGER NOT NULL DEFAULT 0,
                    active INTEGER NOT NULL DEFAULT 1,
                    yield_factor DOUBLE PRECISION NOT NULL DEFAULT 1.0
                )
                """);

        statement.execute("""
                CREATE TABLE IF NOT EXISTS pos_menu_items (
                    id SERIAL PRIMARY KEY,
                    location_id INTEGER NOT NULL DEFAULT 1,
                    pos_sku TEXT NOT NULL,
                    name TEXT NOT NULL,
                    category TEXT,
                    production_profile_id INTEGER REFERENCES production_profiles(id),
                    active INTEGER NOT NULL DEFAULT 1,
                    CONSTRAINT pos_menu_items_location_pos_sku_key UNIQUE(location_id, pos_sku)
                )
                """);

        statement.execute("""
                CREATE TABLE IF NOT EXISTS production_item_product_mappings (
                    id SERIAL PRIMARY KEY,
                    location_id INTEGER NOT NULL DEFAULT 1,
                    production_item_id INTEGER NOT NULL REFERENCES production_items(id),
                    product_id INTEGER NOT NULL REFERENCES products(id),
                    quantity_per_unit DOUBLE PRECISION NOT NULL DEFAULT 0,
                    unit TEXT NOT NULL,
                    active INTEGER NOT NULL DEFAULT 1
                )
                """);

        statement.execute("""
                CREATE TABLE IF NOT EXISTS production_weeks (
                    id SERIAL PRIMARY KEY,
                    location_id INTEGER NOT NULL DEFAULT 1,
                    week_start_date TEXT NOT NULL,
                    week_end_date TEXT NOT NULL,
                    source_sales_start_date TEXT,
                    source_sales_end_date TEXT,
                    par_multiplier DOUBLE PRECISION NOT NULL DEFAULT 1.25,
                    finalized INTEGER NOT NULL DEFAULT 0,
                    created_at TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP,
                    CONSTRAINT production_weeks_location_dates_key
                        UNIQUE(location_id, week_start_date, week_end_date)
                )
                """);

        statement.execute("""
                CREATE TABLE IF NOT EXISTS production_week_days (
                    id SERIAL PRIMARY KEY,
                    location_id INTEGER NOT NULL DEFAULT 1,
                    production_week_id INTEGER NOT NULL REFERENCES production_weeks(id),
                    prep_date TEXT NOT NULL,
                    day_name TEXT NOT NULL,
                    sort_order INTEGER NOT NULL DEFAULT 0
                )
                """);

        statement.execute("""
                CREATE TABLE IF NOT EXISTS production_week_lines (
                    id SERIAL PRIMARY KEY,
                    location_id INTEGER NOT NULL DEFAULT 1,
                    production_week_day_id INTEGER NOT NULL REFERENCES production_week_days(id),
                    production_item_id INTEGER NOT NULL REFERENCES production_items(id),
                    previous_sales_quantity DOUBLE PRECISION NOT NULL DEFAULT 0,
                    generated_par INTEGER NOT NULL DEFAULT 0,
                    override_par INTEGER,
                    final_par INTEGER NOT NULL DEFAULT 0,
                    unit TEXT NOT NULL,
                    station_id INTEGER REFERENCES production_stations(id),
                    print_order INTEGER NOT NULL DEFAULT 0
                )
                """);
    }

    private void createIndexes(Statement statement) throws SQLException {
        statement.execute("""
                CREATE INDEX IF NOT EXISTS idx_invoice_adjustments_invoice_id
                ON invoice_adjustments(invoice_id)
                """);
        statement.execute("""
                CREATE INDEX IF NOT EXISTS idx_invoice_lines_invoice_id
                ON invoice_lines(invoice_id)
                """);
        statement.execute("""
                CREATE INDEX IF NOT EXISTS idx_invoice_lines_product_id
                ON invoice_lines(product_id)
                """);
        statement.execute("""
                CREATE INDEX IF NOT EXISTS idx_invoices_invoice_date
                ON invoices(invoice_date)
                """);
        statement.execute("""
                CREATE INDEX IF NOT EXISTS idx_inventory_count_lines_count_product
                ON inventory_count_lines(count_id, product_id)
                """);
        statement.execute("""
                CREATE INDEX IF NOT EXISTS idx_inventory_counts_completed
                ON inventory_counts(completed)
                """);
        statement.execute("""
                CREATE INDEX IF NOT EXISTS idx_production_week_days_week_sort
                ON production_week_days(production_week_id, sort_order)
                """);
        statement.execute("""
                CREATE INDEX IF NOT EXISTS idx_production_week_lines_day
                ON production_week_lines(production_week_day_id)
                """);
        statement.execute("""
                CREATE INDEX IF NOT EXISTS idx_production_items_station_active
                ON production_items(station_id, active)
                """);
        statement.execute("""
                CREATE INDEX IF NOT EXISTS idx_alcohol_sales_mappings_product
                ON alcohol_sales_mappings(product_id)
                """);
        statement.execute("""
                CREATE INDEX IF NOT EXISTS idx_alcohol_sales_mappings_category_active
                ON alcohol_sales_mappings(reporting_category, active)
                """);
        statement.execute("""
                CREATE INDEX IF NOT EXISTS idx_labour_positions_group_active
                ON labour_positions(location_id, labour_group, active)
                """);
        statement.execute("""
                CREATE INDEX IF NOT EXISTS idx_labour_employees_position_active
                ON labour_employees(location_id, position_id, active)
                """);
        statement.execute("""
                CREATE INDEX IF NOT EXISTS idx_labour_employee_pay_rates_lookup
                ON labour_employee_pay_rates(location_id, employee_id, effective_date)
                """);
        statement.execute("""
                CREATE INDEX IF NOT EXISTS idx_labour_daily_entries_date
                ON labour_daily_entries(location_id, work_date)
                """);
        statement.execute("""
                CREATE INDEX IF NOT EXISTS idx_labour_daily_sales_location_date
                ON labour_daily_sales(location_id, sales_date)
                """);
        statement.execute("""
                CREATE INDEX IF NOT EXISTS idx_location_sessions_location
                ON location_sessions(location_id)
                """);
        statement.execute("""
                CREATE INDEX IF NOT EXISTS idx_location_sessions_token_active
                ON location_sessions(token_hash, revoked, expires_at)
                """);
        for (String tableName : STORE_OWNED_TABLES) {
            statement.execute("""
                    CREATE INDEX IF NOT EXISTS idx_%s_location_id
                    ON %s(location_id)
                    """.formatted(tableName, tableName));
        }
    }

    private void repairSchemaCompatibility(
            Connection connection,
            Statement statement
    ) throws SQLException {
        addColumnIfMissing(
                connection,
                statement,
                "production_profile_lines",
                "yield_factor",
                "DOUBLE PRECISION NOT NULL DEFAULT 1.0"
        );
        addColumnIfMissing(
                connection,
                statement,
                "inventory_count_template_lines",
                "order_guide_case_size",
                "TEXT"
        );
        addColumnIfMissing(connection, statement, "sales_periods", "food_net_sales", "TEXT DEFAULT '0.00'");
        addColumnIfMissing(connection, statement, "sales_periods", "beer_net_sales", "TEXT DEFAULT '0.00'");
        addColumnIfMissing(connection, statement, "sales_periods", "wine_net_sales", "TEXT DEFAULT '0.00'");
        addColumnIfMissing(connection, statement, "sales_periods", "draught_net_sales", "TEXT DEFAULT '0.00'");
        addColumnIfMissing(connection, statement, "sales_periods", "import_draught_net_sales", "TEXT DEFAULT '0.00'");
        addColumnIfMissing(connection, statement, "sales_periods", "liquor_net_sales", "TEXT DEFAULT '0.00'");
        addColumnIfMissing(connection, statement, "labour_daily_entries", "employee_name_snapshot", "TEXT");
        addColumnIfMissing(connection, statement, "labour_daily_entries", "position_name_snapshot", "TEXT");
        addColumnIfMissing(connection, statement, "labour_daily_entries", "labour_group_snapshot", "TEXT");
        addColumnIfMissing(connection, statement, "labour_positions", "location_id", "INTEGER NOT NULL DEFAULT 1");
        addColumnIfMissing(connection, statement, "labour_employees", "location_id", "INTEGER NOT NULL DEFAULT 1");
        addColumnIfMissing(connection, statement, "labour_daily_sales", "location_id", "INTEGER NOT NULL DEFAULT 1");
        addColumnIfMissing(connection, statement, "labour_daily_entries", "location_id", "INTEGER NOT NULL DEFAULT 1");
        addColumnIfMissing(connection, statement, "locations", "admin_password_hash", "TEXT");
        addColumnIfMissing(connection, statement, "locations", "admin_password_salt", "TEXT");
        addColumnIfMissing(connection, statement, "locations", "admin_password_iterations", "INTEGER NOT NULL DEFAULT 600000");
        addColumnIfMissing(connection, statement, "locations", "labour_setup_password_hash", "TEXT");
        addColumnIfMissing(connection, statement, "locations", "labour_setup_password_salt", "TEXT");
        addColumnIfMissing(connection, statement, "locations", "labour_setup_password_iterations", "INTEGER NOT NULL DEFAULT 600000");
        statement.execute("""
                INSERT INTO labour_employee_pay_rates (
                    location_id, employee_id, hourly_wage, effective_date
                )
                SELECT location_id, id, hourly_wage, '1900-01-01'
                FROM labour_employees
                ON CONFLICT(location_id, employee_id, effective_date) DO NOTHING
                """);
        for (String tableName : STORE_OWNED_TABLES) {
            addColumnIfMissing(connection, statement, tableName, "location_id", "INTEGER NOT NULL DEFAULT 1");
        }
        repairLocationUniqueConstraints(connection, statement);

        statement.executeUpdate("""
                UPDATE sales_periods
                SET food_net_sales = CASE WHEN food_net_sales IS NULL OR food_net_sales = '0.00' THEN food_sales ELSE food_net_sales END,
                    beer_net_sales = CASE WHEN beer_net_sales IS NULL OR beer_net_sales = '0.00' THEN beer_sales ELSE beer_net_sales END,
                    wine_net_sales = CASE WHEN wine_net_sales IS NULL OR wine_net_sales = '0.00' THEN wine_sales ELSE wine_net_sales END,
                    draught_net_sales = CASE WHEN draught_net_sales IS NULL OR draught_net_sales = '0.00' THEN draught_sales ELSE draught_net_sales END,
                    import_draught_net_sales = CASE WHEN import_draught_net_sales IS NULL OR import_draught_net_sales = '0.00' THEN import_draught_sales ELSE import_draught_net_sales END,
                    liquor_net_sales = CASE WHEN liquor_net_sales IS NULL OR liquor_net_sales = '0.00' THEN liquor_sales ELSE liquor_net_sales END
                """);
    }

    private void repairLocationUniqueConstraints(Connection connection, Statement statement)
            throws SQLException {
        replaceUniqueConstraint(
                connection,
                statement,
                "products",
                "UNIQUE (sku)",
                "products_location_sku_key",
                "UNIQUE (location_id, sku)"
        );
        replaceUniqueConstraint(
                connection,
                statement,
                "product_sku_aliases",
                "UNIQUE (sku)",
                "product_sku_aliases_location_sku_key",
                "UNIQUE (location_id, sku)"
        );
        replaceUniqueConstraint(
                connection,
                statement,
                "sales_periods",
                "UNIQUE (period_start_date, period_end_date)",
                "sales_periods_location_dates_key",
                "UNIQUE (location_id, period_start_date, period_end_date)"
        );
        replaceUniqueConstraint(
                connection,
                statement,
                "alcohol_sales_mappings",
                "UNIQUE (pos_sku)",
                "alcohol_sales_mappings_location_pos_sku_key",
                "UNIQUE (location_id, pos_sku)"
        );
        replaceUniqueConstraint(
                connection,
                statement,
                "production_stations",
                "UNIQUE (name)",
                "production_stations_location_name_key",
                "UNIQUE (location_id, name)"
        );
        replaceUniqueConstraint(
                connection,
                statement,
                "production_items",
                "UNIQUE (name)",
                "production_items_location_name_key",
                "UNIQUE (location_id, name)"
        );
        replaceUniqueConstraint(
                connection,
                statement,
                "production_profiles",
                "UNIQUE (name)",
                "production_profiles_location_name_key",
                "UNIQUE (location_id, name)"
        );
        replaceUniqueConstraint(
                connection,
                statement,
                "pos_menu_items",
                "UNIQUE (pos_sku)",
                "pos_menu_items_location_pos_sku_key",
                "UNIQUE (location_id, pos_sku)"
        );
        replaceUniqueConstraint(
                connection,
                statement,
                "production_weeks",
                "UNIQUE (week_start_date, week_end_date)",
                "production_weeks_location_dates_key",
                "UNIQUE (location_id, week_start_date, week_end_date)"
        );
    }

    private void replaceUniqueConstraint(
            Connection connection,
            Statement statement,
            String tableName,
            String oldConstraintDefinition,
            String newConstraintName,
            String newConstraintDefinition
    ) throws SQLException {
        String sql = """
                SELECT conname
                FROM pg_constraint
                WHERE conrelid = ?::regclass
                  AND contype = 'u'
                  AND pg_get_constraintdef(oid) = ?
                """;

        try (PreparedStatement preparedStatement = connection.prepareStatement(sql)) {
            preparedStatement.setString(1, tableName);
            preparedStatement.setString(2, oldConstraintDefinition);

            try (ResultSet resultSet = preparedStatement.executeQuery()) {
                while (resultSet.next()) {
                    statement.execute(
                            "ALTER TABLE " + quoteIdentifier(tableName)
                                    + " DROP CONSTRAINT " + quoteIdentifier(resultSet.getString("conname"))
                    );
                }
            }
        }

        if (!constraintExists(connection, tableName, newConstraintName)) {
            statement.execute(
                    "ALTER TABLE " + quoteIdentifier(tableName)
                            + " ADD CONSTRAINT " + quoteIdentifier(newConstraintName)
                            + " " + newConstraintDefinition
            );
        }
    }

    private boolean constraintExists(
            Connection connection,
            String tableName,
            String constraintName
    ) throws SQLException {
        String sql = """
                SELECT 1
                FROM pg_constraint
                WHERE conrelid = ?::regclass
                  AND conname = ?
                """;

        try (PreparedStatement preparedStatement = connection.prepareStatement(sql)) {
            preparedStatement.setString(1, tableName);
            preparedStatement.setString(2, constraintName);

            try (ResultSet resultSet = preparedStatement.executeQuery()) {
                return resultSet.next();
            }
        }
    }

    private void addColumnIfMissing(
            Connection connection,
            Statement statement,
            String tableName,
            String columnName,
            String columnType
    ) throws SQLException {
        String sql = """
                SELECT 1
                FROM information_schema.columns
                WHERE table_schema = current_schema()
                  AND table_name = ?
                  AND column_name = ?
                """;

        try (PreparedStatement preparedStatement = connection.prepareStatement(sql)) {
            preparedStatement.setString(1, tableName);
            preparedStatement.setString(2, columnName);

            try (ResultSet resultSet = preparedStatement.executeQuery()) {
                if (resultSet.next()) {
                    return;
                }
            }
        }

        statement.execute(
                "ALTER TABLE " + quoteIdentifier(tableName)
                        + " ADD COLUMN " + quoteIdentifier(columnName)
                        + " " + columnType
        );
    }

    private void requireTable(Connection connection, String tableName) throws SQLException {
        String sql = """
                SELECT 1
                FROM information_schema.tables
                WHERE table_schema = current_schema()
                  AND table_name = ?
                """;

        try (PreparedStatement preparedStatement = connection.prepareStatement(sql)) {
            preparedStatement.setString(1, tableName);

            try (ResultSet resultSet = preparedStatement.executeQuery()) {
                if (!resultSet.next()) {
                    throw new SQLException("Missing PostgreSQL table: " + tableName);
                }
            }
        }
    }

    private void requireColumn(
            Connection connection,
            String tableName,
            String columnName
    ) throws SQLException {
        String sql = """
                SELECT 1
                FROM information_schema.columns
                WHERE table_schema = current_schema()
                  AND table_name = ?
                  AND column_name = ?
                """;

        try (PreparedStatement preparedStatement = connection.prepareStatement(sql)) {
            preparedStatement.setString(1, tableName);
            preparedStatement.setString(2, columnName);

            try (ResultSet resultSet = preparedStatement.executeQuery()) {
                if (!resultSet.next()) {
                    throw new SQLException("Missing PostgreSQL column: " + tableName + "." + columnName);
                }
            }
        }
    }

    private void seedReferenceData(Statement statement) throws SQLException {
        statement.execute("""
                INSERT INTO sales_category_mappings
                (pos_category, reporting_category, active)
                VALUES
                ('Entrees', 'FOOD', 1),
                ('Appetizers', 'FOOD', 1),
                ('Desserts', 'FOOD', 1),
                ('Add Ons', 'FOOD', 1),
                ('Bottled Beer', 'BEER', 1),
                ('beer dom', 'BEER', 1),
                ('beer imp', 'BEER', 1),
                ('beer prem', 'BEER', 1),
                ('Draft Beer', 'DRAUGHT', 1),
                ('draft dom', 'DRAUGHT', 1),
                ('draft prem', 'DRAUGHT', 1),
                ('draft imp', 'IMPORT DRAUGHT', 1),
                ('Wine', 'WINE', 1),
                ('wine dom', 'WINE', 1),
                ('wine imp', 'WINE', 1),
                ('Liquor', 'LIQUOR', 1)
                ON CONFLICT(pos_category) DO NOTHING
                """);

        statement.execute("""
                INSERT INTO production_stations (location_id, name, prep_sheet, sort_order, active)
                VALUES
                (1, 'Line', 'Main Line', 10, 1),
                (1, 'Pasta', 'Main Line', 20, 1),
                (1, 'Prep', 'Main Line', 30, 1),
                (1, 'Pizza', 'Pizza Salad', 40, 1),
                (1, 'Salad', 'Pizza Salad', 50, 1),
                (1, 'Dessert', 'Main Line', 60, 1),
                (1, 'Freezer Pull', 'Main Line', 70, 1)
                ON CONFLICT(location_id, name) DO NOTHING
                """);

        statement.execute("""
                INSERT INTO settings (setting_key, setting_value)
                VALUES
                ('admin_password', ''),
                ('password_initialized', 'false')
                ON CONFLICT(setting_key) DO NOTHING
                """);

        statement.execute("""
                INSERT INTO settings (setting_key, setting_value)
                VALUES ('labour.default_uniform_deduction', '0.00')
                ON CONFLICT(setting_key) DO NOTHING
                """);

        statement.execute("""
                INSERT INTO locations (id, code, name, username, active)
                VALUES (1, 'STORE', 'Existing Store', 'store', 1)
                ON CONFLICT(id) DO NOTHING
                """);

        statement.execute("""
                SELECT setval(
                    pg_get_serial_sequence('locations', 'id'),
                    COALESCE((SELECT MAX(id) FROM locations), 1),
                    true
                )
                """);
    }

    private void setSchemaVersion(Statement statement) throws SQLException {
        statement.execute("DELETE FROM schema_version");
        statement.execute(
                "INSERT INTO schema_version (version) VALUES ("
                        + CURRENT_SCHEMA_VERSION + ")"
        );
    }

    private String quoteIdentifier(String identifier) {
        return "\"" + identifier.replace("\"", "\"\"") + "\"";
    }
}
