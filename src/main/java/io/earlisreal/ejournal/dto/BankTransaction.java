package io.earlisreal.ejournal.dto;

import io.earlisreal.ejournal.util.Broker;

import java.time.LocalDate;

public class BankTransaction {

    private int id;
    private LocalDate date;
    private boolean isDividend;
    private double amount;
    private Broker broker;
    private String referenceNo;

    public BankTransaction() {}

    public BankTransaction(LocalDate date, boolean isDividend, double amount, Broker broker, String referenceNo) {
        this.date = date;
        this.isDividend = isDividend;
        this.amount = amount;
        this.broker = broker;
        this.referenceNo = referenceNo;
    }

    public String toCsv() {
        return date + ",," + getAction() + "," + amount + ",,," + broker.name() + "," + referenceNo;
    }

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

    public Broker getBroker() {
        return broker;
    }

    public void setBroker(Broker broker) {
        this.broker = broker;
    }

    public String getReferenceNo() {
        return referenceNo;
    }

    public void setReferenceNo(String referenceNo) {
        this.referenceNo = referenceNo;
    }

}
