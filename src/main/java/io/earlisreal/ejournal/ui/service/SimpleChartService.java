package io.earlisreal.ejournal.ui.service;

import io.earlisreal.ejournal.dto.TradeLog;
import io.earlisreal.ejournal.model.CandleStickSeriesData;
import io.earlisreal.ejournal.model.ChartData;
import io.earlisreal.ejournal.model.LineData;
import io.earlisreal.ejournal.model.MarkerData;
import io.earlisreal.ejournal.model.TradeSummary;
import io.earlisreal.ejournal.model.HistogramData;
import io.earlisreal.ejournal.util.Interval;
import javafx.scene.web.WebEngine;

import java.io.IOException;
import java.nio.file.Files;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import static io.earlisreal.ejournal.util.Configs.STOCKS_DIRECTORY;

public class SimpleChartService implements ChartService {

    public static final DateTimeFormatter AV_FORMATTER = DateTimeFormatter.ofPattern("uuuu-MM-dd HH:mm:ss");

    private final WebEngine webEngine;

    private TradeSummary summary;
    private Map<Interval, ChartData> dataMap;
    private boolean intradayAvailable;
    private boolean dailyAvailable;
    private List<String> lines;
    private List<String> dailyLines;

    SimpleChartService(WebEngine webEngine) {
        this.webEngine = webEngine;
    }

    @Override
    public void setSummary(TradeSummary summary) {
        if (this.summary == summary) {
            return;
        }
        this.summary = summary;
        dataMap = new HashMap<>();

        if (summary.isDayTrade()) {
            intraday();
        }
        daily();
    }

    @Override
    public void setInterval(Interval interval) {
        if (!dataMap.containsKey(interval)) {
            generateData(interval);
        }

        ChartData chartData = dataMap.get(interval);
        webEngine.executeScript(String.format("setData(%s, %s, %s)", chartData.getCandleStick(), chartData.getVolume(), chartData.getVwap()));
        webEngine.executeScript(String.format("series.setMarkers(%s)", chartData.getMarker()));
        webEngine.executeScript(String.format("chart.timeScale().scrollToPosition(%d, false)", chartData.getScrollPosition()));
        String titlePrefix = " Day";
        if (interval.isIntraDay()) {
            titlePrefix = " Minute";
        }
        webEngine.executeScript(String.format("updateTitle('%s', '" + interval.getValue() + titlePrefix + "')", summary.getStock()));
    }

    private void generateData(Interval interval) {
        if (interval.isIntraDay() && isIntradayAvailable()) {
            generateIntradayData(interval);
        }
        else {
            generateDailyData();
        }
    }

    @Override
    public boolean isIntradayAvailable() {
        return intradayAvailable;
    }

    @Override
    public boolean isDailyAvailable() {
        return dailyAvailable;
    }

    private void intraday() {
        var dataPath = STOCKS_DIRECTORY.resolve(summary.getCountry().name()).resolve(summary.getStock() + ".csv");
        try {
            lines = Files.readAllLines(dataPath);
            String last = lines.get(lines.size() - 1);
            LocalDate lastDate = LocalDate.parse(last.substring(0, last.indexOf(' ')));
            intradayAvailable = !lastDate.isBefore(summary.getCloseDate().toLocalDate());
        } catch (IOException | DateTimeParseException | StringIndexOutOfBoundsException e) {
            System.out.println("Error while processing intraday data of " + summary.getStock() + ": " + e.getMessage());
            intradayAvailable = false;
        }
    }

    private void generateIntradayData(Interval interval) {
        List<CandleStickSeriesData> seriesDataList = new ArrayList<>();
        List<LineData> vwapList = new ArrayList<>();
        List<HistogramData> volumeDataList = new ArrayList<>();
        double runningVolume = 0;
        double runningTpv = 0;
        LocalDate previousDate = null;
        CandleStickSeriesData actualData = null;
        HistogramData actualVolumeData = null;
        LineData vwapData = null;
        for (var line : lines) {
            String[] tokens = line.split(",");
            LocalDateTime localDateTime = LocalDateTime.parse(tokens[0], AV_FORMATTER);
            localDateTime = localDateTime.minusMinutes(1);
            LocalDate localDate = localDateTime.toLocalDate();
            if (summary.getCloseDate().toLocalDate().minusDays(6).isAfter(localDate)) {
                continue;
            }

            if (!localDate.equals(previousDate)) {
                previousDate = localDate;
                runningTpv = 0;
                runningVolume = 0;
            }

            long epochSecond = localDateTime.toEpochSecond(ZoneOffset.UTC);
            CandleStickSeriesData data = toSeriesData(tokens, epochSecond);
            HistogramData volumeData = toVolumeData(tokens, data, epochSecond);
            runningVolume += volumeData.getValue();

            if (actualData == null) {
                actualData = new CandleStickSeriesData();
                actualVolumeData = new HistogramData();
                actualVolumeData.setTime(epochSecond);
                actualData.setTime(epochSecond);
                actualData.setOpen(data.getOpen());
                actualData.setLow(data.getLow());
                vwapData = new LineData();
                vwapData.setTime(epochSecond);
            }

            actualData.setClose(data.getClose());
            actualData.setHigh(Math.max(actualData.getHigh(), data.getHigh()));
            actualData.setLow(Math.min(actualData.getLow(), data.getLow()));
            actualVolumeData.setValue(actualVolumeData.getValue() + volumeData.getValue());

            if ((localDateTime.getMinute() + 1) % interval.getValue() == 0) {
                double tpv = (actualData.getHigh() + actualData.getLow() + actualData.getClose()) / 3 * actualVolumeData.getValue();
                runningTpv += tpv;
                vwapData.setValue(runningTpv / runningVolume);
                vwapList.add(vwapData);

                actualVolumeData.setColor(getVolumeColor(actualData));
                volumeDataList.add(actualVolumeData);
                seriesDataList.add(actualData);
                actualData = null;
                actualVolumeData = null;
                vwapData = null;
            }
        }

        List<MarkerData> markerDataList = generateMarkers(summary, interval);
        int scrollPosition = calculateScrollPosition(summary, seriesDataList);
        dataMap.put(interval, new ChartData(seriesDataList, volumeDataList, markerDataList, vwapList, scrollPosition));
    }

    private void daily() {
        var dataPath = STOCKS_DIRECTORY.resolve(summary.getCountry().name()).resolve("daily").resolve(summary.getStock() + ".csv");
        try {
            dailyLines = Files.readAllLines(dataPath);
            if (dailyLines.isEmpty()) {
                dailyAvailable = false;
                return;
            }
            String last = dailyLines.get(dailyLines.size() - 1);
            LocalDate lastDate = LocalDate.parse(last.substring(0, last.indexOf(',')));
            dailyAvailable = !lastDate.isBefore(summary.getCloseDate().toLocalDate());
        } catch (IOException | DateTimeParseException | StringIndexOutOfBoundsException e) {
            System.out.println("Error while processing intraday data of " + summary.getStock() + ": " + e.getMessage());
            dailyAvailable = false;
        }
    }

    private void generateDailyData() {
        List<CandleStickSeriesData> seriesDataList = new ArrayList<>();
        List<HistogramData> volumeDataList = new ArrayList<>();
        List<CandleStickSeriesData> weeklySeriesDataList = new ArrayList<>();
        List<HistogramData> weeklyVolumeDataList = new ArrayList<>();
        CandleStickSeriesData weeklyData = null;
        HistogramData weeklyVolumeData = null;
        for (var line : dailyLines) {
            String[] tokens = line.split(",");
            // TODO: What todo when file is tampered or data is broken here?
            LocalDate localDate = LocalDate.parse(tokens[0]);

            if (localDate.getDayOfWeek() == DayOfWeek.MONDAY && weeklyData != null) {
                weeklyVolumeData.setColor(getVolumeColor(weeklyData));
                weeklySeriesDataList.add(weeklyData);
                weeklyVolumeDataList.add(weeklyVolumeData);
                weeklyData = null;
            }

            long epochSecond = localDate.atStartOfDay().toEpochSecond(ZoneOffset.UTC);
            CandleStickSeriesData data = toSeriesData(tokens, epochSecond);
            HistogramData volumeData = toVolumeData(tokens, data, epochSecond);

            if (weeklyData == null) {
                weeklyData = new CandleStickSeriesData();
                weeklyData.setTime(epochSecond);
                weeklyData.setOpen(data.getOpen());
                weeklyData.setLow(data.getLow());
                weeklyVolumeData = new HistogramData();
                weeklyVolumeData.setTime(epochSecond);
            }

            weeklyData.setClose(data.getClose());
            weeklyData.setHigh(Math.max(weeklyData.getHigh(), data.getHigh()));
            weeklyData.setLow(Math.min(weeklyData.getLow(), data.getLow()));
            weeklyVolumeData.setValue(weeklyVolumeData.getValue() + volumeData.getValue());

            seriesDataList.add(data);
            volumeDataList.add(volumeData);
        }

        if (weeklyData != null) {
            weeklyVolumeData.setColor(getVolumeColor(weeklyData));
            weeklySeriesDataList.add(weeklyData);
            weeklyVolumeDataList.add(weeklyVolumeData);
        }

        storeChartData(summary, Interval.DAILY, seriesDataList, volumeDataList);
        storeChartData(summary, Interval.WEEKLY, weeklySeriesDataList, weeklyVolumeDataList);
    }

    private CandleStickSeriesData toSeriesData(String[] tokens, long epochSecond) {
        CandleStickSeriesData data = new CandleStickSeriesData();
        data.setTime(epochSecond);
        data.setOpen(Double.parseDouble(tokens[1]));
        data.setHigh(Double.parseDouble(tokens[2]));
        data.setLow(Double.parseDouble(tokens[3]));
        data.setClose(Double.parseDouble(tokens[4]));
        return data;
    }

    private HistogramData toVolumeData(String[] tokens, CandleStickSeriesData seriesData, long epochSecond) {
        HistogramData data = new HistogramData();
        data.setTime(epochSecond);
        data.setValue(0);
        if (!"N/A".equals(tokens[5])) {
            data.setValue(Double.parseDouble(tokens[5]));
        }
        data.setColor(getVolumeColor(seriesData));
        return data;
    }

    private String getVolumeColor(CandleStickSeriesData data) {
        if (data.getClose() >= data.getOpen()) {
            return HistogramData.GREEN;
        }
        return HistogramData.RED;
    }

    private void storeChartData(TradeSummary summary, Interval interval, List<CandleStickSeriesData> seriesDataList, List<HistogramData> volumeDataList) {
        List<MarkerData> markerDataList = generateMarkers(summary, interval);
        int scrollPosition = calculateScrollPosition(summary, seriesDataList);
        dataMap.put(interval, new ChartData(seriesDataList, volumeDataList, markerDataList, scrollPosition));
    }

    private List<MarkerData> generateMarkers(TradeSummary summary, Interval interval) {
        List<MarkerData> markerDataList = new ArrayList<>();
        long minusMinutes = interval.getValue();
        if (!interval.isIntraDay()) {
            minusMinutes = TimeUnit.DAYS.toMinutes(interval.getValue());
        }
        for (TradeLog log : summary.getLogs()) {
            MarkerData data = new MarkerData();
            // When marker is on HALT, it will be placed on the next bar because the minus minutes will not be enough
            // It should be the size of the gap
            // Let it be a feature for now, to say that there is a gap
            data.setTime(log.getDate().minusMinutes(minusMinutes).toEpochSecond(ZoneOffset.UTC));
            String color = MarkerData.BUY_COLOR;
            if (!log.isBuy()) {
                color = log.isShort() ? MarkerData.SHORT_COLOR : MarkerData.SELL_COLOR;
            }
            data.setPosition(log.getPrice());
            data.setColor(color);
            data.setShape("diamond");
            data.setBorderWidth(0.32);
            data.setSize(1.65);
            markerDataList.add(data);
        }
        return markerDataList;
    }

    private int calculateScrollPosition(TradeSummary summary, List<CandleStickSeriesData> seriesDataList) {
        long first = summary.getLogs().get(0).getDate().truncatedTo(ChronoUnit.MINUTES).toEpochSecond(ZoneOffset.UTC);
        int position = 0;
        for (int i = 0; i < seriesDataList.size(); ++i) {
            if (seriesDataList.get(i).getTime() >= first) {
                position = i - seriesDataList.size();
                break;
            }
        }
        return position + 40;
    }

}
