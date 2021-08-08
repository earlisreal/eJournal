package io.earlisreal.ejournal.dto;

import io.earlisreal.ejournal.util.Broker;

import java.time.LocalDateTime;

public class TradeLog {

    private int id;
    private LocalDateTime date;
    private String stock;
    private boolean isBuy;
    private double price;
    private double shares;
    private Integer strategyId;
    private String strategy;
    private boolean isShort;
    private String invoiceNo;
    private Broker broker;
    private String remarks;
    private Double fees;

    public TradeLog() {}

    public TradeLog(LocalDateTime date, String stock, boolean isBuy, double price, int shares, String invoiceNo, Broker broker) {
        this(date, stock, isBuy, price, shares);
        this.invoiceNo = invoiceNo;
        this.broker = broker;
    }

    public TradeLog(LocalDateTime date, String stock, boolean isBuy, double price, double shares) {
        this.date = date;
        this.stock = stock;
        this.isBuy = isBuy;
        this.price = price;
        this.shares = shares;
    }

    public double getNetAmount() {
        double gross = getGrossAmount();
        return gross + getFees() * (isBuy() ? 1 : -1);
    }

    public double getFees() {
        if (fees == null) {
            return broker.getFees(getGrossAmount(), isBuy());
        }

        return fees;
    }

    public double getGrossAmount() {
        return getShares() * getPrice();
    }

    public String toCsv() {
        String action = isBuy ? "BUY" : "SELL";
        String broker = this.broker.name();
        return date + "," + stock + "," + action + "," + price + "," + shares + "," + "long," + broker + "," + invoiceNo;
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
                ", broker='" + broker + '\'' +
                '}';
    }

    public int getId() {
        return id;
    }

    public void setId(int id) {
        this.id = id;
    }

    public LocalDateTime getDate() {
        return date;
    }

    public void setDate(LocalDateTime date) {
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

    public double getShares() {
        return shares;
    }

    public void setShares(double shares) {
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

    public void setFees(double fees) {
        this.fees = fees;
    }

}
