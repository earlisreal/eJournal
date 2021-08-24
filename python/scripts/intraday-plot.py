import json
import sys
from datetime import datetime

import mplfinance
import numpy
import pandas


def make_plot(markers, color):
    return mplfinance.make_addplot(markers, type="scatter", alpha=0.8, marker="D", markersize=40, color=color)


def main():
    print("Starting Intraday Plotting")
    args = json.loads(sys.argv[1])

    dataframe = pandas.read_csv(args["dataPath"], names=["Date", "Open", "High", "Low", "Close", "Volume"],
                                parse_dates=["Date"], index_col=0)
    dataframe.index.name = "Date"

    start = dataframe.index.searchsorted(datetime.fromisoformat(args["start"]))
    end = dataframe.index.searchsorted(datetime.fromisoformat(args["end"]))
    dataframe = dataframe[start - 30:end + 30]

    buys_length = args["buysLength"]
    sells_length = args["sellsLength"]
    shorts_length = args["shortsLength"]
    buy_markers = []
    for i in range(buys_length):
        buy_markers.append([])

    sell_markers = []
    for i in range(sells_length):
        sell_markers.append([])

    short_markers = []
    for i in range(shorts_length):
        short_markers.append([])

    buys = args["buys"]
    sells = args["sells"]
    shorts = args["shorts"]
    for index, row in dataframe.iterrows():
        d = str(index)

        for i in range(args["buysLength"]):
            cur = buys.get(d, [])
            buy_markers[i].append(cur[i] if i < len(cur) else numpy.nan)

        for i in range(args["sellsLength"]):
            cur = sells.get(d, [])
            sell_markers[i].append(cur[i] if i < len(cur) else numpy.nan)

        for i in range(args["shortsLength"]):
            cur = shorts.get(d, [])
            short_markers[i].append(cur[i] if i < len(cur) else numpy.nan)

    plot = []
    for marker in buy_markers:
        plot.append(make_plot(marker, "green"))
    for marker in sell_markers:
        plot.append(make_plot(marker, "red"))
    for marker in short_markers:
        plot.append(make_plot(marker, "blue"))

    mplfinance.plot(dataframe, type="candle", volume=True, addplot=plot, savefig=args["outputPath"], tight_layout=True)


if __name__ == "__main__":
    main()
