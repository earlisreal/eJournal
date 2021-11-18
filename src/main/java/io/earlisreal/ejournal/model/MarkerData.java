package io.earlisreal.ejournal.model;

public class MarkerData {

    public static final String BUY_COLOR = "#a5d6a7";
    public static final String SELL_COLOR = "#f48fb1";
    public static final String SHORT_COLOR = "#2196f3";

    private long time;
    private double position;
    private String color;
    private String shape;
    private int borderWidth;

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

    public int getBorderWidth() {
        return borderWidth;
    }

    public void setBorderWidth(int borderWidth) {
        this.borderWidth = borderWidth;
    }

}
