package io.earlisreal.ejournal.dto;

import io.earlisreal.ejournal.util.Broker;

import java.time.LocalDate;

public class Plan {

    private int id;
    private LocalDate date;
    private String stock;
    private final double entry;
    private final double stop;
    private final double risk;

    private final long shares;
    private final double amount;

    public Plan(LocalDate date, String stock, double entry, double stop, double risk) {
        this.date = date;
        this.stock = stock;
        this.entry = entry;
        this.stop = stop;
        this.risk = risk;

        shares = calculateShares();
        amount = shares * entry;
    }

    @Override
    public String toString() {
        return "Plan{" +
                "stock='" + stock + '\'' +
                ", entry=" + entry +
                ", stop=" + stop +
                ", risk=" + risk +
                '}';
    }

    public double getNetPosition() {
        return amount + getFees(amount);
    }

    public double getFees() {
        return getFees(amount);
    }

    public double getShares() {
        return shares;
    }

    private long calculateShares() {
        double loss = (entry - stop) / entry;
        long high = Math.round(risk / loss / entry);
        long low = 1;
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

    private double getFees(double amount) {
        Broker broker = Broker.UNKNOWN;
        return broker.getFees(amount, true) + broker.getFees(amount, false);
    }

    public double getPercent() {
        double loss = entry - stop;
        return loss / entry * 100;
    }

    public LocalDate getDate() {
        return date;
    }

    public void setDate(LocalDate date) {
        this.date = date;
    }

    public String getStock() {
        return stock;
    }

    public void setStock(String stock) {
        this.stock = stock;
    }

    public double getEntry() {
        return entry;
    }

    public double getStop() {
        return stop;
    }

    public double getRisk() {
        return risk;
    }

    public int getId() {
        return id;
    }

    public void setId(int id) {
        this.id = id;
    }

}
