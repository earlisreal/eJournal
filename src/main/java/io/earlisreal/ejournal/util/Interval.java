package io.earlisreal.ejournal.util;

public enum Interval {

    ONE_MINUTE(1, true),
    FIVE_MINUTE(5, true),
    FIFTEEN_MINUTE(15, true),
    DAILY(1, false),
    WEEKLY(7, false);

    private final int value;
    private final boolean intraDay;

    Interval(int value, boolean intrayDay) {
        this.value = value;
        this.intraDay = intrayDay;
    }

    public int getValue() {
        return value;
    }

    public boolean isIntraDay() {
        return intraDay;
    }

}
