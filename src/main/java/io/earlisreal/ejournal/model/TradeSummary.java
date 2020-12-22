package io.earlisreal.ejournal.model;

import io.earlisreal.ejournal.dto.TradeLog;

import java.time.LocalDate;

public class TradeSummary {

    private final String stock;
    private final LocalDate openDate;
    private int shares;
    private int position;
    private double averageBuy;
    private double averageSell;
    private LocalDate closeDate;

    public TradeSummary(TradeLog initialTrade) {
        this.stock = initialTrade.getStock();
        this.openDate = initialTrade.getDate();
        buy(initialTrade.getShares(), initialTrade.getPrice());
    }

    public void buy(int shares, double price) {
        position += shares;
        this.shares += shares;
        averageBuy = (averageBuy + shares * price) / position;
    }

    public void sell(int shares, double price) {
        this.shares -= shares;
        averageSell = (averageSell + shares * price) / position;
    }

    public int getShares() {
        return shares;
    }

    public void setCloseDate(LocalDate date) {
        closeDate = date;
    }

    public String getStock() {
        return stock;
    }

    @Override
    public String toString() {
        return "TradeSummary{" +
                "stock='" + stock + '\'' +
                ", position=" + position +
                ", averageBuy=" + averageBuy +
                ", averageSell=" + averageSell +
                ", openDate=" + openDate +
                ", closeDate=" + closeDate +
                '}';
    }

}
