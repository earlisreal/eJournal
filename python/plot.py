import sys
from datetime import datetime

import mplfinance
import numpy
import pandas

if __name__ == "__main__":
    buys = {}
    buys_input = sys.argv[3].split(",")
    for buy in buys_input:
        buys[buy] = True

    sells = {}
    sells_input = sys.argv[4].split(",")
    for sell in sells_input:
        sells[sell] = True

    dataframe = pandas.read_csv(sys.argv[1], names=["Date", "Open", "High", "Low", "Close", "Volume"],
                                parse_dates=["Date"], index_col=0)
    dataframe.index.name = "Date"

    start = dataframe.index.searchsorted(datetime.fromisoformat(buys_input[0]))
    end = dataframe.index.searchsorted(datetime.fromisoformat(sells_input[-1]))
    dataframe = dataframe[start - 5:end + 5]

    buy_markers = []
    sell_markers = []
    for index, row in dataframe.iterrows():
        d = str(index.date())
        if d in sells and d in buys:
            buy_markers.append(row["Low"] - row["Low"] * 0.01)
            sell_markers.append(row["High"] + row["High"] * 0.01)
        elif d in buys:
            buy_markers.append(row["Low"] - row["Low"] * 0.01)
            sell_markers.append(numpy.nan)
        elif d in sells:
            buy_markers.append(numpy.nan)
            sell_markers.append(row["High"] + row["High"] * 0.01)
        else:
            buy_markers.append(numpy.nan)
            sell_markers.append(numpy.nan)

    buy_plot = mplfinance.make_addplot(buy_markers, type="scatter", marker="$\u21E7$", markersize=200, color="green")
    sell_plot = mplfinance.make_addplot(sell_markers, type="scatter", marker="$\u21E9$", markersize=200, color="red")
    mplfinance.plot(dataframe, type="candle", volume=True, addplot=[buy_plot, sell_plot], savefig=sys.argv[2])
