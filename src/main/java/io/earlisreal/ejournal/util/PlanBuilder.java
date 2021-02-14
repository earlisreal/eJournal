package io.earlisreal.ejournal.util;

import io.earlisreal.ejournal.dto.Plan;

import java.time.LocalDate;

public class PlanBuilder {

    private LocalDate date;
    private String stock;
    private double entry;
    private double stop;
    private double risk;

    private long shares;
    private double amount;

    public Plan build() {
        return new Plan(date, stock, entry, stop, risk, getFees(amount), shares, getNetPosition());
    }

    public double getNetPosition() {
        return amount + getFees(amount);
    }

    public void reset(double entry, double stop, double risk) {
        this.entry = entry;
        this.stop = stop;
        this.risk = risk;

        init();
    }

    private void init() {
        shares = calculateShares();
        amount = shares * entry;
    }

    private long calculateShares() {
        double loss = (entry - stop) / entry;
        long high = Math.round(risk / loss / entry);
        long low = 0;
        while (low < high) {
            long mid = (low + high + 1) / 2;
            double position = mid * entry;
            double amount = getFees(position) + position * loss;
            if (amount > risk) {
                high = mid - 1;
            }
            else {
                low = mid;
            }
        }

        return low;
    }

    public double getFees() {
        return getFees(amount);
    }

    private double getFees(double amount) {
        Broker broker = Broker.UNKNOWN;
        return broker.getFees(amount, true) + broker.getFees(amount, false);
    }

    public double getShares() {
        return shares;
    }

    public void setDate(LocalDate date) {
        this.date = date;
    }

    public void setStock(String stock) {
        this.stock = stock;
    }

}
