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
    private static final String INVOICES_HEADER = "Invoice,Amount Due,Original Amount,Invoice Date,Invoice Due Date,Invoice Status,Ship To Name,Ship To Number,Bill To Name,Bill To Number,Line Item,Item Description,Original Quantity,Current Quantity,Unit Price,Tax Amount,Total Amount,Unit of Measure,Weight,Split Item Indicator\n";

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
    void importsNumericSplitAndWeightIndicators() throws Exception {
        var result = read(HEADER
                + "$0.00,SPLIT,3,$1.234,$3.71,0,1,00123,DRY,3.71\n"
                + "$0.00,CASE,1,$25.86,$91.56,1,0,7243163,MEAT,91.56\n");
        assertEquals(3.0, result.getLines().getFirst().getSplitQty());
        assertEquals(new BigDecimal("3.71"), result.getLines().getFirst().getExtendedCost());
        assertEquals(1.0, result.getLines().getLast().getCaseQty());
        assertEquals(new BigDecimal("91.5600"), result.getLines().getLast().getCaseCost());
    }

    @Test
    void reportsZeroReceivedQuantityWithBilledAmount() {
        var error = assertThrows(RuntimeException.class,
                () -> read(HEADER + "$0.00,SPLIT,0,$1.25,$1.25,0,1,00123,DRY,1.25\n"));
        assertTrue(error.getMessage().contains("Current Quantity on CSV row 2: 0"));
    }

    @Test
    void usesConfirmedQuantityForBilledZeroQuantitySplitWithoutChangingLineTotal() throws Exception {
        Path file = tempDir.resolve("invoice.csv");
        Files.writeString(file, HEADER + "$0.00,SPLIT,0,$5.04,$15.12,N,Y,00123,DRY,15.12\n");
        var result = new GfsCsvImportService().readInvoice(file.toFile(),
                (row, sku, description, total, split, suggested) -> {
                    assertEquals(2, row);
                    assertEquals("00123", sku);
                    assertEquals("SPLIT", description);
                    assertEquals(new BigDecimal("15.12"), total);
                    assertTrue(split);
                    assertNull(suggested);
                    return new BigDecimal("3");
                });
        var line = result.getLines().getFirst();
        assertEquals(3.0, line.getSplitQty());
        assertEquals(new BigDecimal("15.12"), line.getExtendedCost());
        assertEquals(new BigDecimal("15.12"), result.getInvoiceTotal());
    }

    @Test
    void cancellingQuantityCorrectionAbortsImport() throws Exception {
        Path file = tempDir.resolve("invoice.csv");
        Files.writeString(file, HEADER + "$0.00,SPLIT,0,$5.04,$15.12,N,Y,00123,DRY,15.12\n");
        assertThrows(GfsCsvImportService.ImportCancelledException.class,
                () -> new GfsCsvImportService().readInvoice(file.toFile(),
                        (row, sku, description, total, split, suggested) -> null));
    }

    @Test
    void rejectsInvalidCorrectedQuantity() throws Exception {
        Path file = tempDir.resolve("invoice.csv");
        Files.writeString(file, HEADER + "$0.00,SPLIT,0,$5.04,$15.12,N,Y,00123,DRY,15.12\n");
        assertThrows(RuntimeException.class,
                () -> new GfsCsvImportService().readInvoice(file.toFile(),
                        (row, sku, description, total, split, suggested) -> BigDecimal.ZERO));
    }

    @Test
    void importsInvoicesListWithInvoiceMetadataAndConfirmedSplitQuantity() throws Exception {
        Path file = tempDir.resolve("invoices.csv");
        Files.writeString(file, INVOICES_HEADER
                + "4425350068,$313.98,$313.98,2026-10-12,2026-11-06,Not Paid,STORE,123,STORE,123,0859132,SPICE,1,0,15.12,1.50,15.12,N,0.0,Y\n"
                + "4425350068,$313.98,$313.98,2026-10-12,2026-11-06,Not Paid,STORE,123,STORE,123,7243163,BEEF,3,3,25.863,0,271.56,Y,10.5,N\n");
        var result = new GfsCsvImportService().readInvoice(file.toFile(),
                (row, sku, description, total, split, suggested) -> {
                    assertEquals(2, row);
                    assertEquals("0859132", sku);
                    assertTrue(split);
                    assertEquals(new BigDecimal("1"), suggested);
                    return suggested;
                });
        assertEquals("4425350068", result.getInvoiceNumber());
        assertEquals("2026-10-12", result.getInvoiceDate());
        assertEquals(new BigDecimal("313.98"), result.getSupplierTotal());
        assertEquals(new BigDecimal("286.68"), result.getInvoiceTotal());
        assertEquals(new BigDecimal("1.50"), result.getTaxTotal());
        assertEquals(1.0, result.getLines().getFirst().getSplitQty());
        assertEquals(new BigDecimal("15.12"), result.getLines().getFirst().getExtendedCost());
        assertEquals(3.0, result.getLines().getLast().getCaseQty());
        assertEquals(new BigDecimal("271.56"), result.getLines().getLast().getExtendedCost());
        assertEquals(new BigDecimal("10.5"), result.getLines().getLast().getBilledWeight());
    }

    @Test
    void rejectsMultipleInvoicesInOneExport() {
        var error = assertThrows(RuntimeException.class,
                () -> read(INVOICES_HEADER
                        + "100,$10.00,$10.00,2026-10-12,,,,,,,123,ITEM,1,1,5,0,5,N,0,N\n"
                        + "101,$10.00,$10.00,2026-10-12,,,,,,,124,ITEM,1,1,5,0,5,N,0,N\n"));
        assertTrue(error.getMessage().contains("multiple invoices"));
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
        assertTrue(error.getMessage().contains("finalized LineItemList or InvoicesList"));
    }

    private GfsCsvImportService.InvoiceImportResult read(String csv) throws Exception {
        Path file = tempDir.resolve("invoice.csv");
        Files.writeString(file, csv);
        return new GfsCsvImportService().readInvoice(file.toFile());
    }
}
