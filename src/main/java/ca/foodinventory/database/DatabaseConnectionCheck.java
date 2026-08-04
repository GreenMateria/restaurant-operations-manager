package ca.foodinventory.database;

import java.sql.Connection;
import java.sql.DatabaseMetaData;

public class DatabaseConnectionCheck {

    public static void main(String[] args) throws Exception {
        try (Connection connection = DatabaseManager.getConnection()) {
            DatabaseMetaData metaData = connection.getMetaData();

            System.out.println("Database connection successful.");
            System.out.println("Product: " + metaData.getDatabaseProductName());
            System.out.println("Version: " + metaData.getDatabaseProductVersion());
            System.out.println("URL: " + metaData.getURL());
            System.out.println("User: " + metaData.getUserName());
        }
    }
}
