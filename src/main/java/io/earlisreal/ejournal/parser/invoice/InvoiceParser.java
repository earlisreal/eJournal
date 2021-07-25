package io.earlisreal.ejournal.parser.invoice;

import io.earlisreal.ejournal.dto.TradeLog;
import io.earlisreal.ejournal.util.Broker;

import java.time.LocalDate;
import java.time.LocalDateTime;

public abstract class InvoiceParser {

    private LocalDateTime date;
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

    protected final void setInvoiceNo(String invoiceNo) {
        this.invoiceNo = invoiceNo;
    }

    protected final void setDate(LocalDateTime date) {
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

    protected final LocalDateTime getDate() {
        return date;
    }

    protected final String getStock() {
        return stock;
    }

    public boolean isBuy() {
        return isBuy;
    }

}
