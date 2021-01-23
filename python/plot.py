import sys
from datetime import datetime

import mplfinance
import numpy
import pandas

if __name__ == "__main__":
    buys = {}
    buys_input = sys.argv[4].split(",")
    for buy in buys_input:
        buys[buy] = True

    sells = {}
    sells_input = None
    if len(sys.argv) > 5:
        sells_input = sys.argv[5].split(",")
        for sell in sells_input:
            sells[sell] = True

    dataframe = pandas.read_csv(sys.argv[1], names=["Date", "Open", "High", "Low", "Close", "Volume"],
                                parse_dates=["Date"], index_col=0)
    dataframe.index.name = "Date"

    start = dataframe.index.searchsorted(datetime.fromisoformat(buys_input[0]))
    if sells_input is not None:
        end = dataframe.index.searchsorted(datetime.fromisoformat(sells_input[-1]))
        if sys.argv[3] is "1":
            dataframe = dataframe[start - 5:end + 5]
        else:
            dataframe = dataframe[start - 5:]
    else:
        dataframe = dataframe[start - 5:]

    buy_markers = []
    sell_markers = []
    for index, row in dataframe.iterrows():
        d = str(index.date())
        if d in sells and d in buys:
            buy_markers.append(row["Low"])
            sell_markers.append(row["High"])
        elif d in buys:
            buy_markers.append(row["Low"] - row["Low"] * 0.01)
            sell_markers.append(numpy.nan)
        elif d in sells:
            buy_markers.append(numpy.nan)
            sell_markers.append(row["High"] + row["High"] * 0.01)
        else:
            buy_markers.append(numpy.nan)
            sell_markers.append(numpy.nan)

    buy_plot = mplfinance.make_addplot(buy_markers, type="scatter", marker="^", markersize=200, color="green")
    sell_plot = mplfinance.make_addplot(sell_markers, type="scatter", alpha=0.8, marker="v", markersize=200, color="red")
    if sells_input is None:
        plot = [buy_plot]
    else:
        plot = [buy_plot, sell_plot]
    mplfinance.plot(dataframe, type="candle", volume=True, addplot=plot, savefig=sys.argv[2])
