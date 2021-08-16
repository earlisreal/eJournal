package io.earlisreal.ejournal.model;

import java.util.List;
import java.util.Map;

public class IntradayPlotArgument {

    private String dataPath;
    private String outputPath;
    private String start;
    private String end;
    private int buysLength;
    private int sellsLength;
    private int shortsLength;
    private Map<String, List<Double>> buys;
    private Map<String, List<Double>> sells;
    private Map<String, List<Double>> shorts;

    public String getDataPath() {
        return dataPath;
    }

    public void setDataPath(String dataPath) {
        this.dataPath = dataPath;
    }

    public String getOutputPath() {
        return outputPath;
    }

    public void setOutputPath(String outputPath) {
        this.outputPath = outputPath;
    }

    public Map<String, List<Double>> getBuys() {
        return buys;
    }

    public void setBuys(Map<String, List<Double>> buys) {
        this.buys = buys;
    }

    public Map<String, List<Double>> getSells() {
        return sells;
    }

    public void setSells(Map<String, List<Double>> sells) {
        this.sells = sells;
    }

    public Map<String, List<Double>> getShorts() {
        return shorts;
    }

    public void setShorts(Map<String, List<Double>> shorts) {
        this.shorts = shorts;
    }

    public String getStart() {
        return start;
    }

    public void setStart(String start) {
        this.start = start;
    }

    public String getEnd() {
        return end;
    }

    public void setEnd(String end) {
        this.end = end;
    }

    public int getBuysLength() {
        return buysLength;
    }

    public void setBuysLength(int buysLength) {
        this.buysLength = buysLength;
    }

    public int getSellsLength() {
        return sellsLength;
    }

    public void setSellsLength(int sellsLength) {
        this.sellsLength = sellsLength;
    }

    public int getShortsLength() {
        return shortsLength;
    }

    public void setShortsLength(int shortsLength) {
        this.shortsLength = shortsLength;
    }

}
