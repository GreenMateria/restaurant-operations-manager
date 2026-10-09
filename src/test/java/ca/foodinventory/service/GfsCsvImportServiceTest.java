package ca.foodinventory.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class GfsCsvImportServiceTest {
    @TempDir
    Path tempDir;

    private static final String HEADER = "Item Tax,Item Description,Current Quantity,Unit Price,Line Total,Unit of Measure,Split Item Indicator,Item Code,Item Category,Current Extended Price\n";

    @Test
    void importsBilledCostsAndDeliveredQuantitiesWithoutTax() throws Exception {
        var result = read(HEADER
                + "$0.00,PEPPERS,0,$0.00,$0.00,N,N,7216397,DRY,0\n"
                + "$0.00,BEEF,1,$25.86,$91.56,Y,N,7243163,MEAT,91.56\n"
                + "$7.01,SANITIZER,1,$53.91,$53.91,N,N,3852781,CHEMICAL,53.91\n"
                + "$0.00,PASTA,2,$23.65,$47.30,N,N,0186098,DRY,47.3\n");
        assertEquals(3, result.getLines().size());
        assertEquals(new BigDecimal("192.77"), result.getInvoiceTotal());
        var beef = result.getLines().getFirst();
        assertEquals(new BigDecimal("91.56"), beef.getExtendedCost());
        assertEquals(new BigDecimal("91.5600"), beef.getCaseCost());
        assertEquals(1.0, beef.getCaseQty());
        assertEquals("0186098", result.getLines().getLast().getSku());
        assertEquals("", beef.getPackSize());
        assertEquals("", result.getInvoiceNumber());
        assertEquals("", result.getInvoiceDate());
    }

    @Test
    void importsSplitPurchasesAndPreservesSupplierExtendedAmount() throws Exception {
        var result = read(HEADER + "$0.00,ITEM,3,$1.234,$3.71,N,Y,00123,DRY,3.71\n");
        var line = result.getLines().getFirst();
        assertEquals(0.0, line.getCaseQty());
        assertEquals(3.0, line.getSplitQty());
        assertEquals(new BigDecimal("1.2340"), line.getEachCost());
        assertEquals(new BigDecimal("3.71"), line.getExtendedCost());
    }

    @Test
    void rejectsInvalidCostsInsteadOfSilentlyUsingZero() {
        assertThrows(RuntimeException.class,
                () -> read(HEADER + "$0.00,ITEM,1,$5.00,INVALID,N,N,123,DRY,5\n"));
    }

    @Test
    void rejectsDeliveryOrderFormatWithHelpfulMessage() {
        var error = assertThrows(RuntimeException.class,
                () -> read("H,ORDER,4902.08\n"));
        assertTrue(error.getMessage().contains("finalized invoice LineItemList"));
    }

    private GfsCsvImportService.InvoiceImportResult read(String csv) throws Exception {
        Path file = tempDir.resolve("invoice.csv");
        Files.writeString(file, csv);
        return new GfsCsvImportService().readInvoice(file.toFile());
    }
}
