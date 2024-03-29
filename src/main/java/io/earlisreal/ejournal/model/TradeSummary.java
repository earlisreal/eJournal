package io.earlisreal.ejournal.model;

import io.earlisreal.ejournal.dto.TradeLog;
import io.earlisreal.ejournal.util.Broker;
import io.earlisreal.ejournal.util.Country;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import static io.earlisreal.ejournal.util.CommonUtil.normalize;

public class TradeSummary {

    private final String stock;
    private final LocalDateTime openDate;
    private double shares;
    private LocalDateTime closeDate;
    private final boolean isShort;
    private final Country country;

    private final List<TradeLog> logs;
    private double totalBuy;
    private double totalSell;

    private double remainingShares;

    TradeSummary(TradeLog initialTrade) {
        this.stock = initialTrade.getStock();
        this.openDate = initialTrade.getDate();
        this.isShort = initialTrade.isShort();
        this.country = initialTrade.getBroker().getCountry();
        logs = new ArrayList<>();
        if (isShort) {
            sell(initialTrade);
        }
        else {
            buy(initialTrade);
        }
    }

    public Broker getBroker() {
        return logs.get(0).getBroker();
    }

    void buy(TradeLog log) {
        if (!isShort) {
            shares += log.getShares();
        }
        remainingShares += log.getShares();
        logs.add(log);
        totalBuy += log.getNetAmount();
    }

    void sell(TradeLog log) {
        if (isShort) {
            shares += log.getShares();
        }
        remainingShares -= log.getShares();
        logs.add(log);
        totalSell += log.getNetAmount();
    }

    public boolean isDayTrade() {
        if (closeDate == null) return false;
        return openDate.toLocalDate().equals(closeDate.toLocalDate());
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

    public String getHoldingPeriod() {
        return normalize(getTradeLength());
    }

    public long getTradeLength() {
        return getOpenDate().until(getCloseDate(), ChronoUnit.SECONDS);
    }

    public double getShares() {
        return shares;
    }

    void setCloseDate(LocalDateTime date) {
        closeDate = date;
    }

    public String getStock() {
        return stock;
    }

    public LocalDateTime getOpenDate() {
        return openDate;
    }

    public LocalDateTime getCloseDate() {
        return closeDate;
    }

    public List<TradeLog> getLogs() {
        return logs;
    }

    public boolean isClosed() {
        return Math.abs(remainingShares) <= 0.000001;
    }

    public double getRemainingShares() {
        return remainingShares;
    }

    public boolean isShort() {
        return isShort;
    }

    public String getTradeType() {
        return isShort ? "Short" : "Long";
    }

    public Country getCountry() {
        return country;
    }

    public String getId() {
        return openDate.format(DateTimeFormatter.ofPattern("uuMMdd")) + hashCode();
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
        if (!logs.equals(summary.logs)) return false;
        return Objects.equals(closeDate, summary.closeDate);
    }

    @Override
    public int hashCode() {
        int result;
        long temp;
        result = stock.hashCode();
        result = 31 * result + openDate.hashCode();
        temp = Double.doubleToLongBits(shares);
        result = 31 * result + (int) (temp ^ (temp >>> 32));
        result = 31 * result + (closeDate != null ? closeDate.hashCode() : 0);
        temp = Double.doubleToLongBits(totalBuy);
        result = 31 * result + (int) (temp ^ (temp >>> 32));
        temp = Double.doubleToLongBits(totalSell);
        result = 31 * result + (int) (temp ^ (temp >>> 32));
        temp = Double.doubleToLongBits(remainingShares);
        result = 31 * result + (int) (temp ^ (temp >>> 32));
        return result;
    }

}
