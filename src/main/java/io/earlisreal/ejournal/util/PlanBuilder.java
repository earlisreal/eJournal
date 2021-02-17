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

    private Broker broker;

    public PlanBuilder() {
        broker = Broker.UNKNOWN;
    }

    public Plan build() {
        return new Plan(date, stock, entry, stop, risk, getFees(amount), shares, getNetPosition(), broker);
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
        shares -= shares % getBoardLot(entry);
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

    public static int getBoardLot(double price) {
        if (price < 0.01) return 1_000_000;
        if (price < 0.05) return 100_000;
        if (price < 0.5) return 10_000;
        if (price < 5) return 1_000;
        if (price < 50) return 100;
        if (price < 1000) return 10;

        return 5;
    }

    public double getFees() {
        return getFees(amount);
    }

    private double getFees(double amount) {
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

    public void setBroker(Broker broker) {
        this.broker = broker;
    }

}
