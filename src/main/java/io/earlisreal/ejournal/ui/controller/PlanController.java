package io.earlisreal.ejournal.ui.controller;

import io.earlisreal.ejournal.dto.Plan;
import io.earlisreal.ejournal.service.PlanService;
import io.earlisreal.ejournal.service.ServiceProvider;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.scene.control.*;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.util.Callback;

import java.time.LocalDate;
import java.util.Comparator;

import static io.earlisreal.ejournal.util.CommonUtil.prettify;
import static io.earlisreal.ejournal.util.CommonUtil.round;

public class PlanController {

    private final PlanService planService;

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

    public PlanController() {
        planService = ServiceProvider.getPlanService();
    }

    public void addPlan() {
        double entry = Double.parseDouble(entryText.getText());
        double stop = Double.parseDouble(stopText.getText());
        double risk = Double.parseDouble(riskText.getText());
        Plan plan = new Plan(LocalDate.now(), stockText.getText(), entry, stop, risk);

        if (planService.insert(plan)) {
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
        positionColumn.setCellValueFactory(p -> new SimpleStringProperty(prettify(p.getValue().getNetPosition())));
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

}
