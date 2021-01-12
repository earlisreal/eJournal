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

    public double getPosition() {
        return getAverageBuy() * position;
    }

    public double getAverageBuy() {
        return totalBuy / position;
    }

    public double getAverageSell() {
        return totalSell / position;
    }

    public double getProfit() {
        return (getAverageSell() - getAverageBuy()) * position;
    }

    public double getProfitPercentage() {
        return getProfit() / totalBuy * 100;
    }

    public int getTradeLength() {
        return getOpenDate().until(getCloseDate()).getDays();
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

    public LocalDate getCloseDate() {
        return closeDate;
    }

    public List<TradeLog> getLogs() {
        return logs;
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
