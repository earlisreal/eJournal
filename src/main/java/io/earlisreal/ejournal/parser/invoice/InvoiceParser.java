package io.earlisreal.ejournal.parser.invoice;

import io.earlisreal.ejournal.dto.TradeLog;

import java.time.LocalDate;

public abstract class InvoiceParser {

    private LocalDate date;
    private String stock;
    private int shares;
    private boolean isBuy;
    private double price;

    public String parseAsCsv(String invoice) {
        parse(invoice);
        return date + "," + stock + "," + (isBuy ? "BUY" : "SELL") + "," + price + "," + shares + "," + "long";
    }

    public TradeLog parseAsObject(String invoice) {
        parse(invoice);
        return new TradeLog(date, stock, isBuy, price, shares, null, false);
    }

    abstract void parse(String invoice);

    protected final void setDate(LocalDate date) {
        this.date = date;
    }

    public final void setStock(String stock) {
        this.stock = stock;
    }

    public final void setShares(int shares) {
        this.shares = shares;
    }

    public final void setBuy(boolean buy) {
        isBuy = buy;
    }

    public final void setPrice(double price) {
        this.price = price;
    }

}
