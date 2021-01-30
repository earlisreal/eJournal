package io.earlisreal.ejournal.parser.invoice;

import io.earlisreal.ejournal.dto.TradeLog;
import io.earlisreal.ejournal.util.Broker;

import java.time.LocalDate;

public abstract class InvoiceParser {

    private LocalDate date;
    private String stock;
    private int shares;
    private boolean isBuy;
    private double price;
    private String invoiceNo;
    private final Broker broker;

    InvoiceParser(Broker broker) {
        this.broker = broker;
    }

    public TradeLog parseAsObject(String invoice) {
        parse(invoice);
        return new TradeLog(date, stock, isBuy, price, shares, invoiceNo, broker);
    }

    abstract void parse(String invoice);

    public final void setInvoiceNo(String invoiceNo) {
        this.invoiceNo = invoiceNo;
    }

    protected final void setDate(LocalDate date) {
        this.date = date;
    }

    protected final void setStock(String stock) {
        this.stock = stock;
    }

    protected final void setShares(int shares) {
        this.shares = shares;
    }

    protected final void setBuy(boolean buy) {
        isBuy = buy;
    }

    protected final void setPrice(double price) {
        this.price = price;
    }

    protected final LocalDate getDate() {
        return date;
    }

    protected final String getStock() {
        return stock;
    }

}
