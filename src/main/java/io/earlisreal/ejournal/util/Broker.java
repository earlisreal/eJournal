package io.earlisreal.ejournal.parser;

public enum Broker {

    AAA("AAA", System.lineSeparator() + "AAA" + System.lineSeparator()),
    YAPSTER("2TradeAsia", "YAPSTER e-TRADE");

    private final String name;
    private final String uniqueIdentifier;

    Broker(String name, String uniqueIdentifier) {
        this.name = name;
        this.uniqueIdentifier = uniqueIdentifier;
    }

    public String getName() {
        return name;
    }

    public String getUniqueIdentifier() {
        return uniqueIdentifier;
    }

}
