package io.earlisreal.ejournal.ui.controller;

import de.jensd.fx.glyphs.GlyphsDude;
import de.jensd.fx.glyphs.fontawesome.FontAwesomeIcon;
import io.earlisreal.ejournal.dto.BankTransaction;
import io.earlisreal.ejournal.dto.TradeLog;
import io.earlisreal.ejournal.input.EmailFetcher;
import io.earlisreal.ejournal.parser.invoice.InvoiceParserFactory;
import io.earlisreal.ejournal.parser.ledger.LedgerParser;
import io.earlisreal.ejournal.parser.ledger.LedgerParserFactory;
import io.earlisreal.ejournal.service.*;
import io.earlisreal.ejournal.util.Broker;
import io.earlisreal.ejournal.util.CommonUtil;
import io.earlisreal.ejournal.util.PDFParser;
import io.earlisreal.ejournal.util.Pair;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.concurrent.Service;
import javafx.concurrent.Task;
import javafx.event.ActionEvent;
import javafx.fxml.FXMLLoader;
import javafx.fxml.Initializable;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.chart.PieChart;
import javafx.scene.control.*;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.StackPane;
import javafx.scene.text.Text;
import javafx.stage.FileChooser;
import javafx.stage.Stage;

import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.ResourceBundle;

import static io.earlisreal.ejournal.util.CommonUtil.*;
import static java.time.LocalDate.now;

public class MainController implements Initializable {

    public GridPane grid;
    public StackPane stackPane;
    public DatePicker startDate;
    public DatePicker endDate;

    public Label statusLabel;
    public PieChart battingChart;
    public ProgressIndicator statusProgressIndicator;
    public TableView<Pair<String, String>> analyticsTable;
    public TableColumn<Pair<String, String>, String> analyticsColumn;
    public TableColumn<Pair<String, String>, String> valueColumn;
    public Label accuracyLabel;
    public Button emailSyncButton;

    public BorderPane dashboardBorder;
    public BorderPane analyticsBorder;
    public BorderPane logBorder;
    public BorderPane bankBorder;
    public BorderPane planBorder;
    public Label riskRewardLabel;

    private Parent log;
    private Parent analytics;
    private Parent bankTransaction;
    private Parent dashboard;
    private Parent plan;

    private ObservableList<Node> children;
    private BankTransactionService bankTransactionService;
    private TradeLogService tradeLogService;
    private AnalyticsService analyticsService;
    private AnalyticsController analyticsController;
    private LogsController logController;
    private BankTransactionController bankTransactionController;
    private DashboardController dashboardController;
    private PlanController planController;
    private final CacheService cacheService;

    private BorderPane selectedPane;
    private final FileChooser.ExtensionFilter pdfFilter;
    private final FileChooser.ExtensionFilter txtFilter;
    private final FileChooser.ExtensionFilter csvFilter;

    public MainController() {
        cacheService = ServiceProvider.getCacheService();

        txtFilter = new FileChooser.ExtensionFilter("Plain text", "*.txt");
        pdfFilter = new FileChooser.ExtensionFilter("PDF", "*.pdf");
        csvFilter = new FileChooser.ExtensionFilter("CSV", "*.csv");
    }

    @Override
    public void initialize(URL location, ResourceBundle resources) {
        initializeIcons();

        bankTransactionService = ServiceProvider.getBankTransactionService();
        tradeLogService = ServiceProvider.getTradeLogService();
        analyticsService = ServiceProvider.getAnalyticsService();

        statusLabel.setVisible(false);
        statusProgressIndicator.setVisible(false);

        try {
            FXMLLoader logLoader = new FXMLLoader(getClass().getResource("/fxml/log.fxml"));
            log = logLoader.load();
            logController = logLoader.getController();

            FXMLLoader analyticsLoader = new FXMLLoader(getClass().getResource("/fxml/analytics.fxml"));
            analytics = analyticsLoader.load();
            analyticsController = analyticsLoader.getController();

            FXMLLoader bankLoader = new FXMLLoader(getClass().getResource("/fxml/bank-transaction.fxml"));
            bankTransaction = bankLoader.load();
            bankTransactionController = bankLoader.getController();

            FXMLLoader dashboardLoader = new FXMLLoader(getClass().getResource("/fxml/dashboard.fxml"));
            dashboard = dashboardLoader.load();
            dashboardController = dashboardLoader.getController();

            FXMLLoader planLoader = new FXMLLoader(getClass().getResource("/fxml/plan.fxml"));
            plan = planLoader.load();
            planController = planLoader.getController();
        } catch (IOException e) {
            System.out.println(e.getMessage());
            e.printStackTrace();
        }

        applyFilterCache();

        children = stackPane.getChildren();
        children.add(dashboard);
        selectedPane = dashboardBorder;
        selectPane(dashboardBorder);

        initializeStatistics();
    }

    private void applyFilterCache() {
        startDate.setValue(cacheService.getStartFilter());
        endDate.setValue(cacheService.getEndFilter());
    }

    private void initializeIcons() {
        Text dashboard = GlyphsDude.createIcon(FontAwesomeIcon.DASHBOARD, "40px");
        dashboardBorder.setCenter(dashboard);

        Text analytics = GlyphsDude.createIcon(FontAwesomeIcon.LINE_CHART, "40px");
        analyticsBorder.setCenter(analytics);

        Text log = GlyphsDude.createIcon(FontAwesomeIcon.EXCHANGE, "40px");
        logBorder.setCenter(log);

        Text bank = GlyphsDude.createIcon(FontAwesomeIcon.BANK, "40px");
        bankBorder.setCenter(bank);

        Text plan = GlyphsDude.createIcon(FontAwesomeIcon.PENCIL, "40px");
        planBorder.setCenter(plan);
    }

    public void showAnalytics() {
        analyticsController.reload();

        children.clear();
        children.add(analytics);
        selectPane(analyticsBorder);
    }

    public void showLog() {
        logController.reload();

        children.clear();
        children.add(log);
        selectPane(logBorder);
    }

    public void showBankTransaction() {
        bankTransactionController.reload();

        children.clear();
        children.add(bankTransaction);
        selectPane(bankBorder);
    }

    public void showDashboard() {
        children.clear();
        children.add(dashboard);
        selectPane(dashboardBorder);
    }

    public void showPlan() {
        planController.reload();

        children.clear();
        children.add(plan);
        selectPane(planBorder);
    }

    private void selectPane(BorderPane pane) {
        if (pane != null) {
            selectedPane.setStyle(null);
        }
        selectedPane = pane;
        selectedPane.setStyle("-fx-background-color: WHITE;");
    }

    public void importInvoice() {
        FileChooser fileChooser = new FileChooser();
        fileChooser.setTitle("Open Invoice");
        setInitialDirectory(fileChooser, "import-invoice-path");
        Stage stage = (Stage) grid.getScene().getWindow();
        fileChooser.getExtensionFilters().addAll();
        var files = fileChooser.showOpenMultipleDialog(stage);
        if (files == null) return;

        int res = 0;
        for (File file : files) {
            String invoice = PDFParser.parse(file);
            Broker broker = CommonUtil.identifyBroker(invoice);
            TradeLog log = InvoiceParserFactory.getInvoiceParser(broker).parseAsObject(invoice);
            res += tradeLogService.insert(List.of(log));
        }

        showInfo("Ledger successfully imported", res + " Records Added");
        if (res > 0) {
            refresh();
        }
    }

    public void importLedger() {
        FileChooser fileChooser = new FileChooser();
        fileChooser.setTitle("Open Invoice");
        Stage stage = (Stage) grid.getScene().getWindow();
        fileChooser.getExtensionFilters().addAll(pdfFilter, txtFilter);
        setInitialDirectory(fileChooser, "import-ledger-path");
        var files = fileChooser.showOpenMultipleDialog(stage);
        if (files == null) return;

        int res = 0;
        for (File file : files) {
            String filename = file.getName();
            List<String> lines;
            Broker broker;
            if (filename.endsWith(".txt")) {
                try {
                    lines = Files.readAllLines(Paths.get(file.toURI()));
                    broker = CommonUtil.identifyBrokerLenient(lines.get(0));
                } catch (IOException e) {
                    CommonUtil.handleException(e);
                    continue;
                }
            }
            else {
                String ledger = PDFParser.parse(file);
                if (ledger == null) continue;
                lines = Arrays.asList(ledger.split(System.lineSeparator()));
                broker = CommonUtil.identifyBroker(ledger);
            }

            LedgerParser parser = LedgerParserFactory.getLedgerParser(broker);
            parser.parse(lines);
            res += tradeLogService.insert(parser.getTradeLogs());
            res += bankTransactionService.insert(parser.getBankTransactions());
        }

        showInfo("Ledger successfully imported", res + " Records Added");
        if (res > 0) {
            refresh();
        }
    }

    public void importCsv() {
        FileChooser fileChooser = new FileChooser();
        fileChooser.setTitle("Save CSV");
        fileChooser.getExtensionFilters().add(csvFilter);
        setInitialDirectory(fileChooser, "import-csv-path");
        Stage stage = (Stage) grid.getScene().getWindow();
        File file = fileChooser.showOpenDialog(stage);
        if (file == null) return;

        int res = 0;
        try {
            List<String> csv = Files.readAllLines(file.toPath());
            res += tradeLogService.insertCsv(csv);
            res += bankTransactionService.insertCsv(csv);
            String newPath = file.toPath().getParent().toAbsolutePath().toString();
            cacheService.save("import-csv-path", newPath);
        } catch (IOException e) {
            handleException(e);
        }

        showInfo("CSV successfully imported", res + " Records Added");
        if (res > 0) {
            refresh();
        }
    }

    public void filterDate() {
        filterDate(startDate.getValue(), endDate.getValue());
    }

    public void syncEmail() {
        emailSyncButton.setDisable(true);
        Service<Integer> service = new Service<>() {
            @Override
            protected Task<Integer> createTask() {
                return new Task<>() {
                    @Override
                    protected Integer call() throws Exception {
                        return EmailFetcher.getInstance().parse();
                    }
                };
            }
        };

        statusProgressIndicator.setVisible(true);
        statusLabel.setVisible(true);
        statusLabel.setText("Syncing");
        service.start();

        service.setOnSucceeded(workerStateEvent -> {
            syncingDone();
            int added = (int) workerStateEvent.getSource().getValue();
            showInfo("Email Sync Success", added + " Records Added");
            if (added > 0) {
                refresh();
            }
        });

        service.setOnFailed(event -> {
            String message;
            Throwable throwable = event.getSource().getException();
            if (throwable != null) {
                message = throwable.getMessage();
                throwable.printStackTrace();
            }
            else {
                message = "Unknown Error.";
            }

            showError("Email Sync Failed", message);
            syncingDone();
        });

        service.setOnCancelled(event -> {
            System.out.println("Email Sync Cancelled");
            syncingDone();
        });
    }

    private void syncingDone() {
        statusLabel.setText("All is well");
        statusProgressIndicator.setVisible(false);
        statusLabel.setVisible(false);
        emailSyncButton.setDisable(false);
    }

    public void clearData(ActionEvent event) {
        Alert alert = new Alert(Alert.AlertType.CONFIRMATION, "Are you sure? All data will be cleared.", ButtonType.YES, ButtonType.NO);
        ButtonType result = alert.showAndWait().orElse(ButtonType.NO);
        if (ButtonType.NO.equals(result)) {
            event.consume();
        }
        else {
            for (TradeLog tradeLog : tradeLogService.getLogs()) {
                tradeLogService.delete(tradeLog.getId());
            }
            for (BankTransaction bankTransaction : bankTransactionService.getAll()) {
                bankTransactionService.delete(bankTransaction.getId());
            }
            cacheService.clear(1);
            refresh();
        }
    }

    private void refresh() {
        tradeLogService.initialize();
        analyticsService.initialize();

        reload();
        logController.reload();
        analyticsController.reload();
        bankTransactionController.reload();
        dashboardController.reload();
    }

    public void reload() {
        initializeStatistics();
    }

    private void initializeStatistics() {
        List<Pair<String, String>> analytics = new ArrayList<>();
        analytics.add(new Pair<>("Equity", prettify(analyticsService.getTotalEquity())));
        analytics.add(new Pair<>("Profit", prettify((analyticsService.getTotalProfit()))));
        analytics.add(new Pair<>("Profit %", analyticsService.getTotalProfitPercentage() + "%"));
        analytics.add(new Pair<>("Edge Ratio", String.valueOf(round(analyticsService.getEdgeRatio()))));
        analytics.add(new Pair<>("Average Profit", prettify(analyticsService.getAverageProfit())));
        analytics.add(new Pair<>("Average Profit %", analyticsService.getAverageProfitPercentage() + "%"));
        analytics.add(new Pair<>("Average Loss", prettify(analyticsService.getAverageLoss())));
        analytics.add(new Pair<>("Average Loss %", analyticsService.getAverageLossPercentage() + "%"));
        analytics.add(new Pair<>("Average Position", prettify(analyticsService.getAveragePosition())));
        analytics.add(new Pair<>("Accuracy", analyticsService.getAccuracy() + "%"));
        analytics.add(new Pair<>("Profit Factor", String.valueOf(analyticsService.getProfitFactor())));
        analytics.add(new Pair<>("Average Length", prettify(analyticsService.getAverageHoldingDays()) + " Days"));
        analytics.add(new Pair<>("Trades Taken", prettify(analyticsService.getSummaries().size())));
        analytics.add(new Pair<>("Transactions", prettify(tradeLogService.getLogs().size())));
        analytics.add(new Pair<>("Losses", prettify(analyticsService.getLosses().size())));
        analytics.add(new Pair<>("Wins", prettify(analyticsService.getWins().size())));
        analytics.add(new Pair<>("Trading Age", analyticsService.getTradingAge()));

        analyticsTable.setItems(FXCollections.observableList(analytics));
        analyticsColumn.setCellValueFactory(p -> new SimpleStringProperty(p.getValue().getT()));
        valueColumn.setCellValueFactory(p -> new SimpleStringProperty(p.getValue().getU()));

        double accuracy = analyticsService.getAccuracy();
        double lossAccuracy = 100 - analyticsService.getAccuracy();
        battingChart.setData(FXCollections.observableArrayList(new PieChart.Data("Win\n" + accuracy + "%", accuracy),
                new PieChart.Data("Loss\n" + lossAccuracy + "%", lossAccuracy)));
        accuracyLabel.setText(accuracy + "%");

        riskRewardLabel.setText("1:" + analyticsService.getProfitFactor());
    }

    public void exportToCsv() {
        List<String> csv = new ArrayList<>();
        tradeLogService.getLogs().forEach(log -> csv.add(log.toCsv()));
        bankTransactionService.getAll().forEach(transaction -> csv.add(transaction.toCsv()));

        FileChooser fileChooser = new FileChooser();
        fileChooser.setTitle("Save CSV");
        fileChooser.getExtensionFilters().add(csvFilter);
        setInitialDirectory(fileChooser, "export-path");
        Stage stage = (Stage) grid.getScene().getWindow();
        File file = fileChooser.showSaveDialog(stage);
        if (file != null) {
            try {
                Files.write(file.toPath(), csv);
                String newPath = file.toPath().getParent().toAbsolutePath().toString();
                cacheService.save("export-path", newPath);
                showInfo("Export Success", "Trade Logs and Bank Transactions successfully exported");
            } catch (IOException e) {
                showError("Fail to Save CSV File", e.getMessage());
            }
        }
    }

    private void setInitialDirectory(FileChooser fileChooser, String key) {
        String lastPath = cacheService.get(key);
        if (lastPath != null) {
            fileChooser.setInitialDirectory(new File(lastPath));
        }
    }

    public void filterAll() {
        filterDate(null, null);
    }

    public void filterYearToDate() {
        filterDate(LocalDate.of(now().getYear(), 1, 1), now());
    }

    public void filter12Months() {
        filterDate(now().minusDays(360), now());
    }

    public void filterLastMonth() {
        filterDate(now().minusDays(30), now());
    }

    public void filterLastWeek() {
        filterDate(now().minusDays(7), now());
    }

    private void filterDate(LocalDate start, LocalDate end) {
        startDate.setValue(start);
        endDate.setValue(end);
        tradeLogService.applyFilter(start, end);
        refresh();

        cacheService.updateStartFilter(start);
        cacheService.updateEndFilter(end);
    }

    public void showAbout() {
        String content = "Developed by: Earl Savadera\n" +
                "Email: earl.stock.trader@gmail.com\n" +
                "GitHub Page: https://github.com/earlisreal/eJournal";
        showInfo("About", content);
    }

    private void showError(String title, String message) {
        Alert alert = new Alert(Alert.AlertType.ERROR);
        showAlert(alert, title, message);
    }

    private void showInfo(String title, String message) {
        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        showAlert(alert, title, message);
    }

    private void showAlert(Alert alert, String title, String message) {
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(message);
        alert.initOwner(grid.getScene().getWindow());
        alert.show();
    }

}
