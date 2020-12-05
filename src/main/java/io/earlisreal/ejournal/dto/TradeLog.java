package io.earlisreal.ejournal.dto;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;

public class TradeLog {

    public static final int COLUMN_COUNT = 7;

    private int id;
    private LocalDate date;
    private String stock;
    private boolean isBuy;
    private double price;
    private int shares;
    private Integer strategyId;
    private boolean isShort;

    public TradeLog() {}

    public TradeLog(LocalDate date, String stock, boolean isBuy, double price, int shares, Integer strategyId, boolean isShort) {
        this.date = date;
        this.stock = stock;
        this.isBuy = isBuy;
        this.price = price;
        this.shares = shares;
        this.strategyId = strategyId;
        this.isShort = isShort;
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

    public boolean isShort() {
        return isShort;
    }

    public void setShort(boolean aShort) {
        isShort = aShort;
    }

}
