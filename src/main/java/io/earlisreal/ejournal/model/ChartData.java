package io.earlisreal.ejournal.model;

import com.jsoniter.output.JsonStream;

import java.util.List;

public class ChartData {

    private final String candleStick;
    private final String volume;
    private final String marker;
    private final String vwap;
    private final int scrollPosition;

    public ChartData(List<CandleStickSeriesData> candleStickList, List<VolumeData> volumeList, List<MarkerData> markerList, int scrollPosition) {
        candleStick = JsonStream.serialize(candleStickList);
        volume = JsonStream.serialize(volumeList);
        marker = JsonStream.serialize(markerList);
        this.vwap = null;
        this.scrollPosition = scrollPosition;
    }

    public ChartData(List<CandleStickSeriesData> candleStickList, List<VolumeData> volumeList, List<MarkerData> markerList, List<LineData> vwapList, int scrollPosition) {
        candleStick = JsonStream.serialize(candleStickList);
        volume = JsonStream.serialize(volumeList);
        marker = JsonStream.serialize(markerList);
        this.vwap = JsonStream.serialize(vwapList);
        this.scrollPosition = scrollPosition;
    }

    public String getCandleStick() {
        return candleStick;
    }

    public String getVolume() {
        return volume;
    }

    public String getMarker() {
        return marker;
    }

    public String getVwap() {
        return vwap;
    }

    public int getScrollPosition() {
        return scrollPosition;
    }

}
