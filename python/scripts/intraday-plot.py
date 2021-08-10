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
    dataframe = dataframe[start - 5:end + 5]

    buy_markers = []
    sell_markers = []
    short_markers = []
    buys = args["buys"]
    sells = args["sells"]
    shorts = args["shorts"]
    for index, row in dataframe.iterrows():
        d = str(index)
        buy_markers.append(buys.get(d, numpy.nan))
        sell_markers.append(sells.get(d, numpy.nan))
        short_markers.append(shorts.get(d, numpy.nan))

    buy_plot = make_plot(buy_markers, "green")
    sell_plot = make_plot(sell_markers, "red")
    short_plot = make_plot(short_markers, "blue")
    plot = []
    if bool(shorts):
        plot.append(short_plot)
    if bool(buys):
        plot.append(buy_plot)
    if bool(sells):
        plot.append(sell_plot)

    mplfinance.plot(dataframe, type="candle", volume=True, addplot=plot, savefig=args["outputPath"])


if __name__ == "__main__":
    main()
