package io.earlisreal.ejournal.model;

import io.earlisreal.ejournal.dto.TradeLog;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public class TradeSummary {

    private final String stock;
    private final LocalDate openDate;
    private int shares;
    private LocalDate closeDate;

    private final List<TradeLog> logs;
    private double totalBuy;
    private double totalSell;

    private int remainingShares;
    private String imageUrl;

    public TradeSummary(TradeLog initialTrade) {
        this.stock = initialTrade.getStock();
        this.openDate = initialTrade.getDate();
        logs = new ArrayList<>();
        buy(initialTrade);
    }

    public void buy(TradeLog log) {
        shares += log.getShares();
        remainingShares += log.getShares();
        logs.add(log);
        totalBuy += log.getNetAmount();
    }

    public void sell(TradeLog log) {
        remainingShares -= log.getShares();
        logs.add(log);
        totalSell += log.getNetAmount();
    }

    public double getPosition() {
        return getAverageBuy() * shares;
    }

    public double getTotalSell() {
        return totalSell;
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
        return remainingShares == 0;
    }

    public int getRemainingShares() {
        return remainingShares;
    }

    public String getImageUrl() {
        return imageUrl;
    }

    public void setImageUrl(String imageUrl) {
        this.imageUrl = imageUrl;
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

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        TradeSummary summary = (TradeSummary) o;

        if (shares != summary.shares) return false;
        if (Double.compare(summary.totalBuy, totalBuy) != 0) return false;
        if (Double.compare(summary.totalSell, totalSell) != 0) return false;
        if (remainingShares != summary.remainingShares) return false;
        if (!stock.equals(summary.stock)) return false;
        if (!openDate.equals(summary.openDate)) return false;
        return Objects.equals(closeDate, summary.closeDate);
    }

    @Override
    public int hashCode() {
        int result;
        long temp;
        result = stock.hashCode();
        result = 31 * result + openDate.hashCode();
        result = 31 * result + shares;
        result = 31 * result + (closeDate != null ? closeDate.hashCode() : 0);
        temp = Double.doubleToLongBits(totalBuy);
        result = 31 * result + (int) (temp ^ (temp >>> 32));
        temp = Double.doubleToLongBits(totalSell);
        result = 31 * result + (int) (temp ^ (temp >>> 32));
        result = 31 * result + remainingShares;
        return result;
    }

}
