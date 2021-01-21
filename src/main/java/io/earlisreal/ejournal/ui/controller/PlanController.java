package io.earlisreal.ejournal.ui.controller;

import io.earlisreal.ejournal.dto.Plan;
import javafx.event.ActionEvent;
import javafx.scene.control.TextField;

public class PlanController {

    public TextField riskText;
    public TextField stopText;
    public TextField entryText;
    public TextField stockText;

    public void addPlan(ActionEvent event) {
        Plan plan = new Plan();
        plan.setStock(stockText.getText());
        plan.setEntry(Double.parseDouble(entryText.getText()));
        plan.setStop(Double.parseDouble(stopText.getText()));
        plan.setRisk(Double.parseDouble(riskText.getText()));

    }

}
