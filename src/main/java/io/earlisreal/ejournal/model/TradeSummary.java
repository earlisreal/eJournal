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
        buy(initialTrade);
    }

    public void buy(TradeLog log) {
        position += log.getShares();
        this.shares += log.getShares();

        averageBuy = (averageBuy + log.getNetAmount()) / position;
    }

    public void sell(TradeLog log) {
        this.shares -= shares;
        averageSell = (averageSell + log.getNetAmount()) / position;
    }

    public double getProfit() {
        return (averageSell - averageBuy) * position;
    }

    public double getProfitPercentage() {
        return averageSell / averageBuy;
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
