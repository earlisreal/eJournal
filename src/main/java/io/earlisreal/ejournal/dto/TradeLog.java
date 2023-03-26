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
    private boolean isShort;
    private String invoiceNo;
    private Broker broker;
    private Double fees;
    private double profit;
    private int portfolio;

    public TradeLog() {}

    public TradeLog(LocalDateTime date, String stock, boolean isBuy, double price, double shares, String invoiceNo, Broker broker) {
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
        String type = isShort() ? "SHORT" : "LONG";
        return date + "," + stock + "," + action + "," + price + "," + shares + "," + type + "," + broker + "," + invoiceNo;
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
                ", isShort=" + isShort +
                ", invoiceNo='" + invoiceNo + '\'' +
                ", broker='" + broker + '\'' +
                '}';
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        TradeLog log = (TradeLog) o;

        if (isBuy != log.isBuy) return false;
        if (Double.compare(log.price, price) != 0) return false;
        if (Double.compare(log.shares, shares) != 0) return false;
        if (isShort != log.isShort) return false;
        if (!date.equals(log.date)) return false;
        if (!stock.equals(log.stock)) return false;
        if (!invoiceNo.equals(log.invoiceNo)) return false;
        return broker == log.broker;
    }

    @Override
    public int hashCode() {
        int result;
        long temp;
        result = date.hashCode();
        result = 31 * result + stock.hashCode();
        result = 31 * result + (isBuy ? 1 : 0);
        temp = Double.doubleToLongBits(price);
        result = 31 * result + (int) (temp ^ (temp >>> 32));
        temp = Double.doubleToLongBits(shares);
        result = 31 * result + (int) (temp ^ (temp >>> 32));
        result = 31 * result + (isShort ? 1 : 0);
        result = 31 * result + invoiceNo.hashCode();
        result = 31 * result + broker.hashCode();
        return result;
    }

    public String getAction() {
        if (isShort) {
            if (isBuy) return "Cover Buy";
            return "Short Sell";
        }
        return isBuy ? "Buy" : "Sell";
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

    public double getProfit() {
        return profit;
    }

    public void setProfit(double profit) {
        this.profit = profit;
    }

}
