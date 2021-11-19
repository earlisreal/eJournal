let series = null;
let volumeSeries = null;

function setData(data, volumeData) {
    if (series) {
        chart.removeSeries(series);
        chart.removeSeries(volumeSeries);
        series = null;
        volumeSeries = null;
    }

    series = chart.addCandlestickSeries({
        priceLineVisible: false,
        lastValueVisible: false
    });
    series.setData(data);

    volumeSeries = chart.addHistogramSeries({
        color: '#26a69a',
        priceFormat: {
            type: 'volume',
        },
        priceLineVisible: false,
        lastValueVisible: false,
        priceScaleId: '',
        scaleMargins: {
            top: 0.8,
            bottom: 0,
        },
    });
    volumeSeries.setData(volumeData);
}
