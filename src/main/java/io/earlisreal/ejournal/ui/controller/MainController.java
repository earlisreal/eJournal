package io.earlisreal.ejournal.ui.controller;

import io.earlisreal.ejournal.dto.TradeLog;
import io.earlisreal.ejournal.input.EmailParser;
import io.earlisreal.ejournal.parser.invoice.InvoiceParserFactory;
import io.earlisreal.ejournal.parser.ledger.LedgerParser;
import io.earlisreal.ejournal.parser.ledger.LedgerParserFactory;
import io.earlisreal.ejournal.service.AnalyticsService;
import io.earlisreal.ejournal.service.BankTransactionService;
import io.earlisreal.ejournal.service.ServiceProvider;
import io.earlisreal.ejournal.service.TradeLogService;
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
import javafx.scene.layout.GridPane;
import javafx.scene.layout.StackPane;
import javafx.stage.FileChooser;
import javafx.stage.Stage;

import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.ResourceBundle;

import static io.earlisreal.ejournal.util.CommonUtil.prettify;
import static io.earlisreal.ejournal.util.CommonUtil.round;

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

    private Parent log;
    private Parent analytics;
    private Parent strategy;
    private Parent bankTransaction;
    private ObservableList<Node> children;
    private BankTransactionService bankTransactionService;
    private TradeLogService tradeLogService;
    private AnalyticsService analyticsService;
    private AnalyticsController analyticsController;
    private LogsController logController;
    private BankTransactionController bankTransactionController;

    @Override
    public void initialize(URL location, ResourceBundle resources) {
        bankTransactionService = ServiceProvider.getBankTransactionService();
        tradeLogService = ServiceProvider.getTradeLogService();
        analyticsService = ServiceProvider.getAnalyticsService();

        try {
            FXMLLoader logLoader = new FXMLLoader(getClass().getResource("/fxml/log.fxml"));
            log = logLoader.load();
            logController = logLoader.getController();

            FXMLLoader analyticsLoader = new FXMLLoader(getClass().getResource("/fxml/analytics.fxml"));
            analytics = analyticsLoader.load();
            analyticsController = analyticsLoader.getController();

            strategy = FXMLLoader.load(getClass().getResource("/fxml/strategy.fxml"));

            FXMLLoader bankLoader = new FXMLLoader(getClass().getResource("/fxml/bank-transaction.fxml"));
            bankTransaction = bankLoader.load();
            bankTransactionController = bankLoader.getController();
        } catch (IOException e) {
            System.out.println(e.getMessage());
            e.printStackTrace();
        }

        children = stackPane.getChildren();
        children.add(analytics);

        initializeStatistics();
    }

    public void showAnalytics(ActionEvent event) {
        children.clear();
        children.add(analytics);
    }

    public void showLog(ActionEvent event) {
        children.clear();
        children.add(log);
    }

    public void showBankTransaction(ActionEvent event) {
        children.clear();
        children.add(bankTransaction);
    }

    public void showStrategy(ActionEvent event) {
        children.clear();
        children.add(strategy);
    }

    public void importInvoice(ActionEvent event) {
        FileChooser fileChooser = new FileChooser();
        fileChooser.setTitle("Open Invoice");
        Stage stage = (Stage) grid.getScene().getWindow();
        fileChooser.getExtensionFilters().addAll(new FileChooser.ExtensionFilter("PDF", "*.pdf"));
        var files = fileChooser.showOpenMultipleDialog(stage);
        if (files == null) return;

        int res = 0;
        for (File file : files) {
            String invoice = PDFParser.parse(file);
            Broker broker = CommonUtil.identifyBroker(invoice);
            TradeLog log = InvoiceParserFactory.getInvoiceParser(broker).parseAsObject(invoice);
            res += tradeLogService.insert(List.of(log));
        }

        if (res > 0) {
            refresh();
        }
    }

    public void importLedger(ActionEvent event) {
        FileChooser fileChooser = new FileChooser();
        fileChooser.setTitle("Open Invoice");
        Stage stage = (Stage) grid.getScene().getWindow();
        fileChooser.getExtensionFilters().addAll(new FileChooser.ExtensionFilter("Text File", "*.txt"),
                new FileChooser.ExtensionFilter("PDF", "*.pdf"));
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
                    broker = CommonUtil.identifyBroker(lines.get(0));
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

        if (res > 0) {
            refresh();
        }
    }

    public void filterDate(ActionEvent event) {
        tradeLogService.applyFilter(startDate.getValue(), endDate.getValue());
        analyticsController.reload();
        logController.reload();
        reload();
    }

    public void syncEmail(ActionEvent event) {
        Service<Integer> service = new Service<>() {
            @Override
            protected Task<Integer> createTask() {
                return new Task<>() {
                    @Override
                    protected Integer call() throws Exception {
                        return EmailParser.getInstance().parse();
                    }
                };
            }
        };

        statusProgressIndicator.setVisible(true);
        statusLabel.setText("Syncing");
        service.start();

        service.setOnSucceeded(workerStateEvent -> {
            statusLabel.setText("All is well");
            statusProgressIndicator.setVisible(false);
            if ((int) workerStateEvent.getSource().getValue() > 0) {
                refresh();
            }
        });
    }

    public void clearData(ActionEvent event) {
        Alert alert = new Alert(Alert.AlertType.CONFIRMATION, "Are you sure? All data will be cleared.", ButtonType.YES, ButtonType.NO);
        ButtonType result = alert.showAndWait().orElse(ButtonType.NO);
        if (ButtonType.NO.equals(result)) {
            event.consume();
        }

        refresh();
    }

    private void refresh() {
        reload();
        logController.reload();
        analyticsController.reload();
        bankTransactionController.reload();
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
        analytics.add(new Pair<>("Accuracy", analyticsService.getAccuracy() + "%"));
        analytics.add(new Pair<>("Profit Factor", String.valueOf(analyticsService.getProfitFactor())));
        analytics.add(new Pair<>("Average Length", prettify(analyticsService.getAverageHoldingDays()) + " Days"));
        analytics.add(new Pair<>("Trades Taken", prettify(analyticsService.getSummaries().size())));
        analytics.add(new Pair<>("Transactions", prettify(tradeLogService.getLogs().size())));
        analytics.add(new Pair<>("Losses", prettify(analyticsService.getLosses().size())));
        analytics.add(new Pair<>("Wins", prettify(analyticsService.getWins().size())));

        analyticsTable.setItems(FXCollections.observableList(analytics));
        analyticsColumn.setCellValueFactory(p -> new SimpleStringProperty(p.getValue().getT()));
        valueColumn.setCellValueFactory(p -> new SimpleStringProperty(p.getValue().getU()));

        double accuracy = analyticsService.getAccuracy();
        double lossAccuracy = 100 - analyticsService.getAccuracy();
        battingChart.setData(FXCollections.observableArrayList(new PieChart.Data("Win\n" + accuracy + "%", accuracy),
                new PieChart.Data("Loss\n" + lossAccuracy + "%", lossAccuracy)));
        accuracyLabel.setText(accuracy + "%");
    }

}
