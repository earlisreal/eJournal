package io.earlisreal.ejournal.model;

public class VolumeData {

    public static final String GREEN = "rgba(0, 150, 136, 0.5)";
    public static final String RED = "rgba(255,82,82, 0.5)";

    private long time;
    private double value;
    private String color;

    public long getTime() {
        return time;
    }

    public void setTime(long time) {
        this.time = time;
    }

    public double getValue() {
        return value;
    }

    public void setValue(double value) {
        this.value = value;
    }

    public String getColor() {
        return color;
    }

    public void setColor(String color) {
        this.color = color;
    }

}
