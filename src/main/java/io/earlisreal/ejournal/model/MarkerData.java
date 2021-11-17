package io.earlisreal.ejournal.model;

public class MarkerData {

    public static final String BUY_COLOR = "#4caf50";
    public static final String SELL_COLOR = "#e91e63";
    public static final String SHORT_COLOR = "#2196f3";

    private long time;
    private double position;
    private String color;
    private String shape;

    public long getTime() {
        return time;
    }

    public void setTime(long time) {
        this.time = time;
    }

    public double getPosition() {
        return position;
    }

    public void setPosition(double position) {
        this.position = position;
    }

    public String getColor() {
        return color;
    }

    public void setColor(String color) {
        this.color = color;
    }

    public String getShape() {
        return shape;
    }

    public void setShape(String shape) {
        this.shape = shape;
    }

}
