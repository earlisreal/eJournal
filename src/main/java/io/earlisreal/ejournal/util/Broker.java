package io.earlisreal.ejournal.util;

public enum Broker {

    UNKNOWN("Unknown", null, null, null),
    AAA(
            "AAA",
            System.lineSeparator() + "AAA" + System.lineSeparator(),
            "subject:(AAA EQUITIES) (PDFAPAR OR SAP) has:attachment",
            Country.PH
    ),
    YAPSTER(
            "2TradeAsia",
            "YAPSTER",
            "subject:(YAPSTER E-TRADE, INC. (2TRADE ASIA)) has:attachment",
            Country.PH
    ) {
        @Override
        public double getFees(double grossAmount, boolean isBuy) {
            double otherFees = grossAmount * 0.0002;
            return getCommission(grossAmount, isBuy) + otherFees;
        }
    },
    COL(
            "COL Financial",
            "COL Financial Group, Inc.",
            "from:(alert@colfinancial.com OR Helpdesk@colfinancial.com OR withdrawals@colfinancial.com) " +
                    "subject:(\"COL Trading Confirmation\" OR \"Notice of Cash Dividend\" " +
                    "OR \"Acknowledgement of Deposit\" OR \"Notice of Debit Adjustment\")",
            Country.PH
    ),
    ETORO("eToro", "eToro AUS", null, Country.US) {
        @Override
        public double getFees(double grossAmount, boolean isBuy) {
            return 0;
        }

        @Override
        protected double getCommission(double grossAmount, boolean isBuy) {
            return 0;
        }
    },
    TRADE_ZERO("TradeZero", "Account,T/D,S/D", null, Country.US);

    private final String name;
    private final String uniqueIdentifier;
    private final String emailFilter;
    private final Country country;

    Broker(String name, String uniqueIdentifier, String emailFilter, Country country) {
        this.name = name;
        this.uniqueIdentifier = uniqueIdentifier;
        this.emailFilter = emailFilter;
        this.country = country;
    }

    public double getFees(double grossAmount, boolean isBuy) {
        double otherFees = grossAmount * 0.00015;
        return getCommission(grossAmount, isBuy) + otherFees;
    }

    protected double getCommission(double grossAmount, boolean isBuy) {
        double commission = Math.max(20.0, grossAmount * 0.0025);
        commission += commission * 0.12;
        if (!isBuy) {
            commission += grossAmount * 0.006;
        }
        return commission;
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

    public Country getCountry() {
        return country;
    }

}
