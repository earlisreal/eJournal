package io.earlisreal.ejournal.dto;

import java.time.LocalDate;

public class BankTransaction {

    public static final int COLUMN_COUNT = 4;

    private int id;
    private LocalDate date;
    private boolean isDividend;
    private double amount;
    private String referenceNo;

    @Override
    public String toString() {
        return "BankTransaction{" +
                "id=" + id +
                ", date=" + date +
                ", isDividend=" + isDividend +
                ", amount=" + amount +
                ", referenceNo=" + referenceNo +
                '}';
    }

    public String getAction() {
        if (amount < 0) return "Withdraw";
        if (isDividend()) return "Dividend";
        return "Deposit";
    }

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

    public String getReferenceNo() {
        return referenceNo;
    }

    public void setReferenceNo(String referenceNo) {
        this.referenceNo = referenceNo;
    }

}
