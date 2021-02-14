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
import java.time.LocalDate;
import java.util.Arrays;
import java.util.Comparator;
import java.util.ResourceBundle;

import static io.earlisreal.ejournal.util.CommonUtil.prettify;
import static io.earlisreal.ejournal.util.CommonUtil.round;

public class PlanController implements Initializable, StartupListener {

    private final PlanService planService;
    private final StockService stockService;
    private final AnalyticsService analyticsService;

    public TextField riskText;
    public TextField stopText;
    public TextField entryText;
    public TextField stockText;
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

    public PlanController() {
        planService = ServiceProvider.getPlanService();
        stockService = ServiceProvider.getStockService();
        analyticsService = ServiceProvider.getAnalyticsService();
        ServiceProvider.getStartupService().addStockPriceListener(this);

        planner = new PlanBuilder();
    }

    public void addPlan() {
        if (planService.insert(planner.build())) {
            System.out.println("Plan inserted");
            reload();
        }
    }

    public void reload() {
        var plans = planService.getAll();
        plans.sort(Comparator.comparing(Plan::getDate).reversed());
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
        stockText.clear();
        entryText.clear();
        stopText.clear();
        riskText.clear();
    }

    public void calculateShares() {
        String entryStr = entryText.getText();
        String stopStr = stopText.getText();
        String riskStr = riskText.getText();
        double entry, stop, risk;

        if (entryStopRadio.isSelected() && (entryStr.isBlank() || riskStr.isBlank())) {
            planShares.setText("0");
            planFees.setText("0");
            planPosition.setText("0");
            return;
        }
        else if (stopStr.isBlank()) return;

        try {
            entry = Double.parseDouble(entryStr);
            if (entryStopRadio.isSelected()) stop = Double.parseDouble(stopStr);
            else stop = 100 - entry;
            risk = Double.parseDouble(riskStr);

            if (riskPercentRadio.isSelected()) {
                risk = analyticsService.getTotalEquity() * (risk / 100);
            }
            planner.reset(entry, stop, risk);
            planShares.setText(prettify(planner.getShares()));
            planFees.setText(prettify(planner.getFees()));
            planPosition.setText(prettify(planner.getNetPosition()));
        }
        catch (NumberFormatException ignore) {
        }
    }

    public void showEntryStop() {
        stopLabel.setVisible(true);
        stopText.setVisible(true);
        entryLabel.setText("Entry Price");
    }

    public void showPercentage() {
        stopLabel.setVisible(false);
        stopText.setVisible(false);
        entryLabel.setText("Percent");
    }

    public void showValueRisk() {
        riskLabel.setText("Value at Risk");
    }

    public void showPercentRisk() {
        riskLabel.setText("Value at Risk %");
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
    }

}
