package ca.foodinventory.service;

import ca.foodinventory.database.DatabaseManager;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

public class DatabaseBackupService {

    public File backupDatabase(File destinationFolder) throws IOException {

        File sourceDb = DatabaseManager.getDatabaseFile();

        String timestamp =
                LocalDateTime.now()
                        .format(DateTimeFormatter.ofPattern("yyyy-MM-dd_HHmmss"));

        File backupFile = new File(
                destinationFolder,
                "FoodInventory_Backup_" + timestamp + ".db"
        );

        Files.copy(
                sourceDb.toPath(),
                backupFile.toPath(),
                StandardCopyOption.REPLACE_EXISTING
        );

        return backupFile;
    }

    public void restoreDatabase(File backupFile) throws IOException {

        File destinationDb = DatabaseManager.getDatabaseFile();

        Files.copy(
                backupFile.toPath(),
                destinationDb.toPath(),
                StandardCopyOption.REPLACE_EXISTING
        );
    }
}