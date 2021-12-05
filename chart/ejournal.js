let series = null;
let volumeSeries = null;
let vwapSeries = null;

function updateTitle(stock, interval) {
    titleLegend.innerText = stock + ' - ' + interval;
}

function updateLegend(price, volume, vwap) {
    const className = price.open <= price.close ? 'greenLegend' : 'redLegend';
    volumeLegend.innerText = volume;
    openLegend.innerText = price.open.toFixed(2);
    high.innerText = price.high.toFixed(2);
    lowLegend.innerText = price.low.toFixed(2);
    closeLegend.innerText = price.close.toFixed(2);
    vwapLegend.innerText = vwap;

    openLegend.className = className;
    highLegend.className = className;
    lowLegend.className = className;
    closeLegend.className = className;
    volumeLegend.className = className;
}

function setData(data, volumeData, vwapData) {
    if (series) {
        chart.removeSeries(series);
        chart.removeSeries(volumeSeries);
        series = null;
        volumeSeries = null;
    }

    if (vwapSeries) {
        chart.removeSeries(vwapSeries);
        vwapSeries = null;
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

    const lastIndex = volumeData.length - 1;
    let lastVwap = undefined;
    if (vwapData) {
        vwapSeries = chart.addLineSeries({
            color: '#009688',
            lastValueVisible: false,
            lineWidth: 1,
            crosshairMarkerVisible: false
        });
        vwapSeries.setData(vwapData);
        vwapDiv.style.display = 'block';
        lastVwap = vwapData[lastIndex].value;
    }
    else {
        vwapDiv.style.display = 'none';
    }

    updateLegend(data[lastIndex], volumeData[lastIndex].value, lastVwap);

    chart.subscribeCrosshairMove((param) => {
        const price = param.seriesPrices.get(series);
        const volume = param.seriesPrices.get(volumeSeries);
        const vwap = param.seriesPrices.get(vwapSeries);
        if (price) {
            updateLegend(price, volume, vwap);
        }
    });
}
