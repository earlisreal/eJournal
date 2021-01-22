package io.earlisreal.ejournal.ui.controller;

import io.earlisreal.ejournal.dto.Plan;
import io.earlisreal.ejournal.service.PlanService;
import io.earlisreal.ejournal.service.ServiceProvider;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.event.ActionEvent;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import javafx.scene.control.cell.PropertyValueFactory;

import java.time.LocalDate;

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
    public TableColumn<Plan, String> strategyColumn;
    public TableColumn<Plan, String> deleteColumn;
    public TableColumn<Plan, LocalDate> dateColumn;

    public PlanController() {
        planService = ServiceProvider.getPlanService();
    }

    public void addPlan(ActionEvent event) {
        Plan plan = new Plan();
        plan.setStock(stockText.getText());
        plan.setEntry(Double.parseDouble(entryText.getText()));
        plan.setStop(Double.parseDouble(stopText.getText()));
        plan.setRisk(Double.parseDouble(riskText.getText()));

        if (planService.insert(plan)) {
            System.out.println("Plan inserted");
            reload();
        }
    }

    public void reload() {
        planTable.setItems(FXCollections.observableArrayList(planService.getAll()));
        dateColumn.setCellValueFactory(new PropertyValueFactory<>("date"));
        stockColumn.setCellValueFactory(p -> new SimpleStringProperty(p.getValue().getStock()));
        entryColumn.setCellValueFactory(p -> new SimpleStringProperty(prettify(p.getValue().getEntry())));
        stopColumn.setCellValueFactory(p -> new SimpleStringProperty(prettify(p.getValue().getStop())));
        riskColumn.setCellValueFactory(p -> new SimpleStringProperty(prettify(p.getValue().getRisk())));
        percentColumn.setCellValueFactory(p -> new SimpleStringProperty(round(p.getValue().getPercent()) + "%"));
        sharesColumn.setCellValueFactory(p -> new SimpleStringProperty(prettify(p.getValue().getShares())));
        positionColumn.setCellValueFactory(p -> new SimpleStringProperty(prettify(p.getValue().getPosition())));
    }

}
