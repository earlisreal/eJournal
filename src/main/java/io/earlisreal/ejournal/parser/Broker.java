package io.earlisreal.ejournal.parser;

public enum Broker {

    AAA("AAA", "AAA");

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
