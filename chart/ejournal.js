let series = null;
let volumeSeries = null;

function updateTitle(stock, interval) {
    titleLegend.innerText = stock + ' - ' + interval;
}

function updateLegend(price, volume) {
    const className = price.open <= price.close ? 'greenLegend' : 'redLegend';
    volumeLegend.innerText = volume;
    openLegend.innerText = price.open.toFixed(2);
    high.innerText = price.high.toFixed(2);
    lowLegend.innerText = price.low.toFixed(2);
    closeLegend.innerText = price.close.toFixed(2);

    openLegend.className = className;
    highLegend.className = className;
    lowLegend.className = className;
    closeLegend.className = className;
    volumeLegend.className = className;
}

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

    updateLegend(data[data.length - 1], volumeData[volumeData.length - 1].value);

    chart.subscribeCrosshairMove((param) => {
        const price = param.seriesPrices.get(series);
        const volume = param.seriesPrices.get(volumeSeries);
        if (price) {
            updateLegend(price, volume);
        }
    });
}
