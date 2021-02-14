package io.earlisreal.ejournal.dto;

import java.time.LocalDate;

public class Plan {

    private int id;
    private final LocalDate date;
    private final String stock;
    private final double entry;
    private final double stop;
    private final double risk;
    private final double fees;
    private final long shares;
    private final double position;

    public Plan(LocalDate date, String stock, double entry, double stop, double risk, double fees, long shares, double position) {
        this.date = date;
        this.stock = stock;
        this.entry = entry;
        this.stop = stop;
        this.risk = risk;
        this.fees = fees;
        this.shares = shares;
        this.position = position;
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

    public double getFees() {
        return fees;
    }

    public double getShares() {
        return shares;
    }

    public double getPercent() {
        double loss = entry - stop;
        return loss / entry * 100;
    }

    public LocalDate getDate() {
        return date;
    }

    public String getStock() {
        return stock;
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

    public double getPosition() {
        return position;
    }

}
