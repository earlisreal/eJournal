package io.earlisreal.ejournal.model;

import java.util.Map;

public class IntradayPlotArgument {

    private String dataPath;
    private String outputPath;
    private String start;
    private String end;
    private Map<String, Double> buys;
    private Map<String, Double> sells;
    private Map<String, Double> shorts;

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

    public Map<String, Double> getBuys() {
        return buys;
    }

    public void setBuys(Map<String, Double> buys) {
        this.buys = buys;
    }

    public Map<String, Double> getSells() {
        return sells;
    }

    public void setSells(Map<String, Double> sells) {
        this.sells = sells;
    }

    public Map<String, Double> getShorts() {
        return shorts;
    }

    public void setShorts(Map<String, Double> shorts) {
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

}
