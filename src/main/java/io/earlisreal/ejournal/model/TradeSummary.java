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
    private double totalBuy;
    private double totalSell;

    public TradeSummary(TradeLog initialTrade) {
        this.stock = initialTrade.getStock();
        this.openDate = initialTrade.getDate();
        logs = new ArrayList<>();
        buy(initialTrade);
    }

    public void buy(TradeLog log) {
        position += log.getShares();
        shares += log.getShares();
        logs.add(log);
        totalBuy += log.getNetAmount();
    }

    public void sell(TradeLog log) {
        shares -= log.getShares();
        logs.add(log);
        totalSell += log.getNetAmount();
    }

    public double getAverageBuy() {
        return totalBuy / position;
    }

    public double getAverageSell() {
        return totalSell / position;
    }

    public double getSimpleProfit() {
        return Math.round((getAverageSell() - getAverageBuy()) * position * 100) / 100.0;
    }

    public double getProfit() {
        return (getAverageSell() - getAverageBuy()) * position;
    }

    public String getProfitPercentage() {
        return Math.round(getProfit() / totalBuy * 10000) % 10000 / 100.0 + "%";
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

    public LocalDate getOpenDate() {
        return openDate;
    }

    public int getPosition() {
        return position;
    }

    public LocalDate getCloseDate() {
        return closeDate;
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
