package ca.foodinventory.ui;

import ca.foodinventory.dao.InvoiceDao;
import ca.foodinventory.model.Invoice;
import ca.foodinventory.model.InvoiceLine;
import javafx.collections.FXCollections;
import javafx.scene.control.*;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;

import java.math.BigDecimal;
import java.util.Optional;

public class InvoiceHistoryView {

    private final InvoiceDao invoiceDao = new InvoiceDao();

    private final TableView<Invoice> invoiceTable = new TableView<>();
    private final TableView<InvoiceLine> lineTable = new TableView<>();

    public BorderPane getView() {
        BorderPane root = new BorderPane();
        root.getStyleClass().add("root-dark");

        Label title = new Label("Invoice History");
        title.getStyleClass().add("page-title");

        Button deleteButton = new Button("Delete Invoice");
        deleteButton.getStyleClass().add("danger-button");
        deleteButton.setOnAction(e -> deleteSelectedInvoice());

        HBox topBar = new HBox(15, title, deleteButton);
        topBar.getStyleClass().add("top-bar");

        setupInvoiceTable();
        setupLineTable();

        loadInvoices();

        invoiceTable.getSelectionModel()
                .selectedItemProperty()
                .addListener((obs, oldInvoice, selectedInvoice) -> {
                    if (selectedInvoice != null) {
                        lineTable.setItems(FXCollections.observableArrayList(
                                invoiceDao.findInvoiceLines(selectedInvoice.getId())
                        ));
                    }
                });

        Label linesTitle = new Label("Selected Invoice Lines");
        linesTitle.getStyleClass().add("section-title");

        VBox content = new VBox(10, topBar, invoiceTable, linesTitle, lineTable);
        content.getStyleClass().add("content-area");

        root.setCenter(content);

        return root;
    }

    private void loadInvoices() {
        invoiceTable.setItems(FXCollections.observableArrayList(invoiceDao.findAllInvoices()));
        lineTable.setItems(FXCollections.observableArrayList());
    }

    private void deleteSelectedInvoice() {
        Invoice selectedInvoice = invoiceTable.getSelectionModel().getSelectedItem();

        if (selectedInvoice == null) {
            showAlert(
                    Alert.AlertType.WARNING,
                    "No Invoice Selected",
                    "Please select an invoice to delete."
            );
            return;
        }

        Alert confirm = new Alert(Alert.AlertType.CONFIRMATION);
        confirm.setTitle("Delete Invoice");
        confirm.setHeaderText("Delete selected invoice?");
        confirm.setContentText(
                "Invoice Number: " + selectedInvoice.getInvoiceNumber() + "\n\n" +
                        "This will permanently delete the invoice and all invoice lines."
        );

        ButtonType deleteButton = new ButtonType("Delete Invoice", ButtonBar.ButtonData.OK_DONE);
        ButtonType cancelButton = new ButtonType("Cancel", ButtonBar.ButtonData.CANCEL_CLOSE);

        confirm.getButtonTypes().setAll(deleteButton, cancelButton);

        Optional<ButtonType> result = confirm.showAndWait();

        if (result.isEmpty() || result.get() != deleteButton) {
            return;
        }

        try {
            invoiceDao.deleteInvoice(selectedInvoice.getId());

            loadInvoices();

            showAlert(
                    Alert.AlertType.INFORMATION,
                    "Invoice Deleted",
                    "Invoice deleted successfully."
            );

        } catch (Exception ex) {
            ex.printStackTrace();

            showAlert(
                    Alert.AlertType.ERROR,
                    "Delete Failed",
                    ex.getMessage()
            );
        }
    }

    private void setupInvoiceTable() {
        TableColumn<Invoice, String> invoiceNumberColumn = new TableColumn<>("Invoice #");
        invoiceNumberColumn.setCellValueFactory(new PropertyValueFactory<>("invoiceNumber"));

        TableColumn<Invoice, String> dateColumn = new TableColumn<>("Date");
        dateColumn.setCellValueFactory(new PropertyValueFactory<>("invoiceDate"));

        TableColumn<Invoice, String> supplierColumn = new TableColumn<>("Supplier");
        supplierColumn.setCellValueFactory(new PropertyValueFactory<>("supplier"));

        TableColumn<Invoice, BigDecimal> totalColumn = new TableColumn<>("Total");
        totalColumn.setCellValueFactory(new PropertyValueFactory<>("invoiceTotal"));

        invoiceTable.getColumns().addAll(
                invoiceNumberColumn,
                dateColumn,
                supplierColumn,
                totalColumn
        );
    }

    private void setupLineTable() {
        TableColumn<InvoiceLine, String> skuColumn = new TableColumn<>("SKU");
        skuColumn.setCellValueFactory(new PropertyValueFactory<>("sku"));

        TableColumn<InvoiceLine, String> descriptionColumn = new TableColumn<>("Description");
        descriptionColumn.setCellValueFactory(new PropertyValueFactory<>("description"));

        TableColumn<InvoiceLine, Double> quantityColumn = new TableColumn<>("Qty");
        quantityColumn.setCellValueFactory(new PropertyValueFactory<>("caseQty"));

        TableColumn<InvoiceLine, String> packSizeColumn = new TableColumn<>("Pack Size");
        packSizeColumn.setCellValueFactory(new PropertyValueFactory<>("packSize"));

        TableColumn<InvoiceLine, BigDecimal> caseCostColumn = new TableColumn<>("Case Cost");
        caseCostColumn.setCellValueFactory(new PropertyValueFactory<>("caseCost"));

        TableColumn<InvoiceLine, BigDecimal> extendedCostColumn = new TableColumn<>("Extended Cost");
        extendedCostColumn.setCellValueFactory(new PropertyValueFactory<>("extendedCost"));

        lineTable.getColumns().addAll(
                skuColumn,
                descriptionColumn,
                quantityColumn,
                packSizeColumn,
                caseCostColumn,
                extendedCostColumn
        );
    }

    private void showAlert(
            Alert.AlertType type,
            String title,
            String message
    ) {
        Alert alert = new Alert(type);
        alert.setTitle(title);
        alert.setHeaderText(title);
        alert.setContentText(message);
        alert.showAndWait();
    }
}