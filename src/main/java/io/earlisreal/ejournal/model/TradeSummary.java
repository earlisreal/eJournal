package io.earlisreal.ejournal.model;

import io.earlisreal.ejournal.dto.TradeLog;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

public class TradeSummary {

    private final String stock;
    private final LocalDate openDate;
    private int shares;
    private int position;
    private LocalDate closeDate;

    private final List<TradeLog> logs;

    public TradeSummary(TradeLog initialTrade) {
        this.stock = initialTrade.getStock();
        this.openDate = initialTrade.getDate();
        logs = new ArrayList<>();
        buy(initialTrade);
    }

    public void buy(TradeLog log) {
        position += log.getShares();
        this.shares += log.getShares();
        logs.add(log);
    }

    public void sell(TradeLog log) {
        this.shares -= log.getShares();
        logs.add(log);
    }

    public double getAverageBuy() {
        double total = 0;
        for (TradeLog log : logs) {
            if (log.isBuy()) {
                total += log.getNetAmount();
            }
        }
        return total / position;
    }

    public double getAverageSell() {
        double total = 0;
        for (TradeLog log : logs) {
            if (!log.isBuy()) {
                total += log.getNetAmount();
            }
        }
        return total / position;
    }

    public double getProfit() {
        return (getAverageSell() - getAverageBuy()) * position;
    }

    public double getProfitPercentage() {
        return getAverageSell() / getAverageBuy();
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
                ", averageBuy=" + getAverageBuy() +
                ", averageSell=" + getAverageSell() +
                ", openDate=" + openDate +
                ", closeDate=" + closeDate +
                '}';
    }

}
