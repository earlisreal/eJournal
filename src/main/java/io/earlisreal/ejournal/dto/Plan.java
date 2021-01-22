package io.earlisreal.ejournal.dto;

import java.time.LocalDate;

public class Plan {

    private int id;
    private LocalDate date;
    private String stock;
    private double entry;
    private double stop;
    private double risk;

    @Override
    public String toString() {
        return "Plan{" +
                "stock='" + stock + '\'' +
                ", entry=" + entry +
                ", stop=" + stop +
                ", risk=" + risk +
                '}';
    }

    public double getShares() {
        return getPosition() / entry;
    }

    public double getPosition() {
        double loss = (entry - stop) / entry;
        return risk / loss;
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

    public void setEntry(double entry) {
        this.entry = entry;
    }

    public double getStop() {
        return stop;
    }

    public void setStop(double stop) {
        this.stop = stop;
    }

    public double getRisk() {
        return risk;
    }

    public void setRisk(double risk) {
        this.risk = risk;
    }

    public int getId() {
        return id;
    }

    public void setId(int id) {
        this.id = id;
    }

}