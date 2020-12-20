package io.earlisreal.ejournal.util;

public enum Broker {

    UNKNOWN("Unknown", null, null),
    AAA(
            "AAA",
            System.lineSeparator() + "AAA" + System.lineSeparator(),
            "subject:(AAA EQUITIES) PDFAPAR has:attachment"
    ),
    YAPSTER(
            "2TradeAsia",
            "YAPSTER e-TRADE",
            "subject:(YAPSTER E-TRADE, INC. (2TRADE ASIA) Invoice) has:attachment"
    ) {
        @Override
        public double getFees(double grossAmount) {
            double otherFees = grossAmount * 0.0002;
            return getCommission(grossAmount) + otherFees;
        }
    },
    COL(
            "COL Financial",
            "COL FINANCIAL GROUP, INC.",
            null
    );

    private final String name;
    private final String uniqueIdentifier;
    private final String emailFilter;

    Broker(String name, String uniqueIdentifier, String emailFilter) {
        this.name = name;
        this.uniqueIdentifier = uniqueIdentifier;
        this.emailFilter = emailFilter;
    }

    public double getFees(double grossAmount) {
        double otherFees = grossAmount * 0.00015;
        return getCommission(grossAmount) + otherFees;
    }

    protected double getCommission(double grossAmount) {
        double commission = grossAmount * 0.0025;
        return commission + commission * 0.12;
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
