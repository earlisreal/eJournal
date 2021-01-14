package io.earlisreal.ejournal.model;

import io.earlisreal.ejournal.dto.TradeLog;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

public class TradeSummary {

    private final String stock;
    private final LocalDate openDate;
    private int shares;
    private LocalDate closeDate;

    private final List<TradeLog> logs;
    private double totalBuy;
    private double totalSell;

    private int runningShares;

    public TradeSummary(TradeLog initialTrade) {
        this.stock = initialTrade.getStock();
        this.openDate = initialTrade.getDate();
        logs = new ArrayList<>();
        buy(initialTrade);
    }

    public void buy(TradeLog log) {
        shares += log.getShares();
        runningShares += log.getShares();
        logs.add(log);
        totalBuy += log.getNetAmount();
    }

    public void sell(TradeLog log) {
        runningShares -= log.getShares();
        logs.add(log);
        totalSell += log.getNetAmount();
    }

    public double getPosition() {
        return getAverageBuy() * shares;
    }

    public double getAverageBuy() {
        return totalBuy / shares;
    }

    public double getAverageSell() {
        return totalSell / shares;
    }

    public double getProfit() {
        return (getAverageSell() - getAverageBuy()) * shares;
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

    public boolean isClosed() {
        return runningShares == 0;
    }

    @Override
    public String toString() {
        return "TradeSummary{" +
                "stock='" + stock + '\'' +
                ", shares=" + getShares() +
                ", averageBuy=" + getAverageBuy() +
                ", averageSell=" + getAverageSell() +
                ", openDate=" + openDate +
                ", closeDate=" + closeDate +
                '}';
    }

}
