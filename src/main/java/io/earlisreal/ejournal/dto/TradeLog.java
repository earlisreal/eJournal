package io.earlisreal.ejournal.dto;

import io.earlisreal.ejournal.util.Broker;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;

public class TradeLog {

    public static final int COLUMN_COUNT = 9;

    private int id;
    private LocalDate date;
    private String stock;
    private boolean isBuy;
    private double price;
    private int shares;
    private Integer strategyId;
    private String strategy;
    private boolean isShort;
    private String invoiceNo;
    private Broker broker;

    public TradeLog() {}

    public TradeLog(LocalDate date, String stock, boolean isBuy, double price, int shares, String strategy, boolean isShort) {
        this(date, stock, isBuy, price, shares);
        this.isShort = isShort;
        this.strategy = strategy;
    }

    public TradeLog(LocalDate date, String stock, boolean isBuy, double price, int shares, String invoiceNo, Broker broker) {
        this(date, stock, isBuy, price, shares);
        this.invoiceNo = invoiceNo;
        this.broker = broker;
    }

    public TradeLog(LocalDate date, String stock, boolean isBuy, double price, int shares) {
        this.date = date;
        this.stock = stock;
        this.isBuy = isBuy;
        this.price = price;
        this.shares = shares;
    }

    public double getNetAmount() {
        double gross = getGrossAmount();
        return gross + broker.getFees(gross, isBuy()) * (isBuy() ? 1 : -1);
    }

    public double getGrossAmount() {
        return getShares() * getPrice();
    }

    @Override
    public String toString() {
        return "TradeLog{" +
                "id=" + id +
                ", date=" + date +
                ", stock='" + stock + '\'' +
                ", isBuy=" + isBuy +
                ", price=" + price +
                ", shares=" + shares +
                ", strategyId=" + strategyId +
                ", strategy='" + strategy + '\'' +
                ", isShort=" + isShort +
                ", invoiceNo='" + invoiceNo + '\'' +
                ", broker='" + broker.getName() + '\'' +
                '}';
    }

    public Instant getDateInstant() {
        return date.atStartOfDay(ZoneId.systemDefault()).toInstant();
    }

    public int getId() {
        return id;
    }

    public void setId(int id) {
        this.id = id;
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

    public boolean isBuy() {
        return isBuy;
    }

    public void setBuy(boolean buy) {
        isBuy = buy;
    }

    public double getPrice() {
        return price;
    }

    public void setPrice(double price) {
        this.price = price;
    }

    public int getShares() {
        return shares;
    }

    public void setShares(int shares) {
        this.shares = shares;
    }

    public Integer getStrategyId() {
        return strategyId;
    }

    public void setStrategyId(Integer strategyId) {
        this.strategyId = strategyId;
    }

    public String getStrategy() {
        return strategy;
    }

    public void setStrategy(String strategy) {
        this.strategy = strategy;
    }

    public boolean isShort() {
        return isShort;
    }

    public void setShort(boolean aShort) {
        isShort = aShort;
    }

    public String getInvoiceNo() {
        return invoiceNo;
    }

    public void setInvoiceNo(String invoiceNo) {
        this.invoiceNo = invoiceNo;
    }

    public Broker getBroker() {
        if (broker == null) return Broker.UNKNOWN;
        return broker;
    }

    public void setBroker(Broker broker) {
        this.broker = broker;
    }

}
