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
import javafx.collections.ObservableList;
import javafx.concurrent.Service;
import javafx.concurrent.Task;
import javafx.event.ActionEvent;
import javafx.fxml.FXMLLoader;
import javafx.fxml.Initializable;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.chart.PieChart;
import javafx.scene.control.Button;
import javafx.scene.control.DatePicker;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressIndicator;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.StackPane;
import javafx.stage.FileChooser;
import javafx.stage.Stage;

import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.List;
import java.util.ResourceBundle;

public class MainController implements Initializable {

    public GridPane grid;
    public StackPane stackPane;
    public DatePicker startDate;
    public DatePicker endDate;
    public Label statusLabel;
    public Label statisticsLabel;
    public PieChart battingChart;
    public ProgressIndicator statusProgressIndicator;

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

    private void initializeStatistics() {
        String separator = System.lineSeparator();
        String trades = "Total Trades: " + tradeLogService.getAllTradeSummaries().size() + separator;
        String wins = "Total Wins: " + analyticsService.getAllWins().size() + separator;
        String losses = "Total Losses: " + analyticsService.getAllLosses().size() + separator;
        statisticsLabel.setText(trades + wins + losses);
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

    public void importLogs(ActionEvent event) {
        FileChooser fileChooser = new FileChooser();
        fileChooser.setTitle("Open Ledger / Invoice");
        Stage stage = (Stage) ((Button) event.getSource()).getScene().getWindow();
        fileChooser.getExtensionFilters().addAll(new FileChooser.ExtensionFilter("Text File", "*.txt"),
                new FileChooser.ExtensionFilter("PDF", "*.pdf"));
        var files = fileChooser.showOpenMultipleDialog(stage);
        if (files == null) return;

        int res = 0;
        for (File file : files) {
            String filename = file.getName();
            if (filename.endsWith(".txt")) {
                try {
                    var lines = Files.readAllLines(Paths.get(file.toURI()));
                    Broker broker = CommonUtil.identifyBroker(lines.get(0));
                    LedgerParser parser = LedgerParserFactory.getLedgerParser(broker);
                    parser.parse(lines);
                    res += tradeLogService.insert(parser.getTradeLogs());
                    res += bankTransactionService.insert(parser.getBankTransactions());
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
            if (filename.toLowerCase().endsWith(".pdf")) {
                String invoice = PDFParser.parse(file);
                Broker broker = CommonUtil.identifyBroker(invoice);
                TradeLog log = InvoiceParserFactory.getInvoiceParser(broker).parseAsObject(invoice);
                res += tradeLogService.insert(List.of(log));
            }
        }

        if (res > 0) {
            refresh();
        }
    }

    public void filterDate(ActionEvent event) {
        tradeLogService.applyFilter(startDate.getValue(), endDate.getValue());
        analyticsController.reload();
        logController.reload();
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

    private void refresh() {
        logController.reload();
        analyticsController.reload();
        bankTransactionController.reload();
    }

}
