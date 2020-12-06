package io.earlisreal.ejournal.dto;

import java.time.LocalDate;

public class BankTransaction {

    private int id;
    private LocalDate date;
    private boolean isDividend;
    private double amount;

    public LocalDate getDate() {
        return date;
    }

    public void setDate(LocalDate date) {
        this.date = date;
    }

    public boolean isDividend() {
        return isDividend;
    }

    public void setDividend(boolean dividend) {
        isDividend = dividend;
    }

    public double getAmount() {
        return amount;
    }

    public void setAmount(double amount) {
        this.amount = amount;
    }

    public int getId() {
        return id;
    }

    public void setId(int id) {
        this.id = id;
    }

}
