package io.earlisreal.ejournal.util;

public enum Broker {

    AAA(
            "AAA",
            System.lineSeparator() + "AAA" + System.lineSeparator(),
            "subject:(AAA EQUITIES) PDFAPAR has:attachment"
    ),
    YAPSTER(
            "2TradeAsia",
            "YAPSTER e-TRADE",
            "from:(accounting@2tradeasia.com) subject:(YAPSTER E-TRADE, INC. (2TRADE ASIA) Invoice) has:attachment"
    ),
    COL(
            "COL Financial",
            "COL FINANCIAL GROUP, INC.",
            "from:(alert@colfinancial.com) subject:(COL Trading Confirmation)"
    );

    private final String name;
    private final String uniqueIdentifier;
    private final String emailFilter;

    Broker(String name, String uniqueIdentifier, String emailFilter) {
        this.name = name;
        this.uniqueIdentifier = uniqueIdentifier;
        this.emailFilter = emailFilter;
    }

    public String getName() {
        return name;
    }

    public String getUniqueIdentifier() {
        return uniqueIdentifier;
    }

    public String getEmailFilter() {
        return emailFilter;
    }

}
