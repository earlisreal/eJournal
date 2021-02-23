package io.earlisreal.ejournal.ui.controller;

import io.earlisreal.ejournal.dto.Plan;
import io.earlisreal.ejournal.service.*;
import io.earlisreal.ejournal.util.Broker;
import io.earlisreal.ejournal.util.PlanBuilder;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.fxml.Initializable;
import javafx.scene.control.*;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.util.Callback;

import java.net.URL;
import java.text.ParseException;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.Comparator;
import java.util.ResourceBundle;

import static io.earlisreal.ejournal.util.CommonUtil.*;

public class PlanController implements Initializable, StartupListener {

    private final PlanService planService;
    private final StockService stockService;
    private final AnalyticsService analyticsService;

    public TextField riskText;
    public TextField stopText;
    public TextField entryText;
    public TableView<Plan> planTable;
    public TableColumn<Plan, String> stockColumn;
    public TableColumn<Plan, String> entryColumn;
    public TableColumn<Plan, String> stopColumn;
    public TableColumn<Plan, String> riskColumn;
    public TableColumn<Plan, String> percentColumn;
    public TableColumn<Plan, String> sharesColumn;
    public TableColumn<Plan, String> positionColumn;
    public TableColumn<Plan, String> feesColumn;
    public TableColumn<Plan, Void> deleteColumn;
    public TableColumn<Plan, LocalDate> dateColumn;
    public TextField planShares;
    public TextField planFees;
    public TextField planPosition;

    private final PlanBuilder planner;
    public Label riskLabel;
    public Label stopLabel;
    public ChoiceBox<Broker> brokerChoice;
    public ComboBox<String> stockCombo;
    public RadioButton entryStopRadio;
    public ToggleGroup entryGroup;
    public RadioButton percentRadio;
    public RadioButton riskValueRadio;
    public ToggleGroup riskGroup;
    public RadioButton riskPercentRadio;
    public Label entryLabel;

    private double oneVar;
    private String percentLoss;
    private String entryPrice;

    public PlanController() {
        planService = ServiceProvider.getPlanService();
        stockService = ServiceProvider.getStockService();
        analyticsService = ServiceProvider.getAnalyticsService();
        ServiceProvider.getStartupService().addStockPriceListener(this);

        planner = new PlanBuilder();
    }

    public void addPlan() {
        planner.setDate(LocalDate.now());
        planner.setStock(stockCombo.getValue());
        if (planService.insert(planner.build())) {
            System.out.println("Plan inserted");
            reload();
        }
    }

    public void reload() {
        oneVar = analyticsService.getTotalEquity() * 0.01;
        var plans = planService.getAll();
        plans.sort(Comparator.comparing(Plan::getDate).thenComparing(Plan::getId).reversed());
        planTable.setItems(FXCollections.observableArrayList(plans));
        dateColumn.setCellValueFactory(new PropertyValueFactory<>("date"));
        stockColumn.setCellValueFactory(p -> new SimpleStringProperty(p.getValue().getStock()));
        entryColumn.setCellValueFactory(p -> new SimpleStringProperty(prettify(p.getValue().getEntry())));
        stopColumn.setCellValueFactory(p -> new SimpleStringProperty(prettify(p.getValue().getStop())));
        riskColumn.setCellValueFactory(p -> new SimpleStringProperty(prettify(p.getValue().getRisk())));
        percentColumn.setCellValueFactory(p -> new SimpleStringProperty(round(p.getValue().getPercent()) + "%"));
        sharesColumn.setCellValueFactory(p -> new SimpleStringProperty(prettify(p.getValue().getShares())));
        positionColumn.setCellValueFactory(p -> new SimpleStringProperty(prettify(p.getValue().getPosition())));
        feesColumn.setCellValueFactory(p -> new SimpleStringProperty(prettify(p.getValue().getFees())));
        deleteColumn.setCellFactory(new Callback<>() {
            @Override
            public TableCell<Plan, Void> call(TableColumn<Plan, Void> param) {
                return new TableCell<>() {
                    @Override
                    protected void updateItem(Void item, boolean empty) {
                        final Button button = new Button("Delete");
                        button.setOnAction(event -> {
                            boolean res = planService.delete(getTableView().getItems().get(getIndex()).getId());
                            if (res) {
                                reload();
                            }
                        });
                        super.updateItem(item, empty);
                        if (!empty) setGraphic(button);
                    }
                };
            }
        });
    }

    public void clearFields() {
        entryText.clear();
        stopText.clear();
        riskText.clear();
    }

    public void showEntryStop() {
        percentLoss = entryText.getText();
        entryText.setText(entryPrice);

        stopLabel.setVisible(true);
        stopText.setVisible(true);
        entryLabel.setText("Entry Price");
        calculateShares();
    }

    public void showPercentage() {
        entryPrice = entryText.getText();
        entryText.setText(percentLoss);

        stopLabel.setVisible(false);
        stopText.setVisible(false);
        entryLabel.setText("Percent");
        calculateShares();
    }

    public void calculateShares() {
        if (entryText.getText() == null || entryText.getText().isBlank()) {
            planShares.setText("0");
            planFees.setText("0");
            planPosition.setText("0");
            return;
        }

        double entry, stop, risk;

        entry = getEntry();
        if (entryStopRadio.isSelected()) {
            stop = getStop();
        }
        else {
            String stock = stockCombo.getValue();
            Double lastPrice = stockService.getPrice(stock);
            if (stock != null && lastPrice != null) {
                entry = lastPrice;
                stop = entry - ((getEntry() / 100) * entry);
            }
            else {
                stop = 100 - entry;
                entry = 100;
            }
        }
        risk = getRisk();

        if (riskPercentRadio.isSelected()) {
            risk = risk * oneVar;
        }
        planner.reset(entry, stop, risk);
        planShares.setText(prettify(planner.getShares()));
        planFees.setText(prettify(planner.getFees()));
        planPosition.setText(prettify(planner.getNetPosition()));
    }

    public void showValueRisk() {
        riskLabel.setText("Value at Risk");
        riskText.setText(prettify(getRisk() * oneVar));
    }

    public void showPercentRisk() {
        riskLabel.setText("Value at Risk %");
        riskText.setText(prettify(getRisk() /  oneVar));
    }

    @Override
    public void onFinish() {
        var list = FXCollections.observableArrayList(stockService.getStockList());
        stockCombo.setItems(list.sorted());
    }

    @Override
    public void initialize(URL location, ResourceBundle resources) {
        var brokers = Broker.values();
        brokerChoice.setItems(FXCollections.observableList(Arrays.asList(brokers).subList(1, brokers.length)));
        brokerChoice.setValue(Broker.YAPSTER);
        planner.setBroker(brokerChoice.getValue());
        brokerChoice.setOnAction(event -> planner.setBroker(brokerChoice.getValue()));
    }

    private double getRisk() {
        return getValue(riskText);
    }

    private double getEntry() {
        return getValue(entryText);
    }

    private double getStop() {
        return getValue(stopText);
    }

    private double getValue(TextField textField) {
        try {
            return parseDouble(textField.getText());
        }
        catch (ParseException ignore) {
            return 0;
        }
    }

}
