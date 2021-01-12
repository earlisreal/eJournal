import sys

import mplfinance
import numpy
import pandas

if __name__ == "__main__":
    print("hello")

    buys = {}
    for buy in sys.argv[3].split(","):
        buys[buy] = True

    sells = {}
    for sell in sys.argv[4].split(","):
        sells[sell] = True

    dataframe = pandas.read_csv(sys.argv[1], names=["Date", "Open", "High", "Low", "Close", "Volume"],
                                parse_dates=["Date"], index_col=0)
    dataframe.index.name = "Date"

    buymarkers = []
    sellmarkers = []
    for index, row in dataframe.iterrows():
        d = str(index.date())
        if d in buys:
            buymarkers.append(row["Low"])
            sellmarkers.append(numpy.nan)
        elif d in sells:
            buymarkers.append(numpy.nan)
            sellmarkers.append(row["High"])
        else:
            buymarkers.append(numpy.nan)
            sellmarkers.append(numpy.nan)

    buylot = mplfinance.make_addplot(buymarkers, type="scatter", marker="$\u21E7$", markersize=200, color="green", panel=0)
    selllot = mplfinance.make_addplot(sellmarkers, type="scatter", marker="$\u21E9$", markersize=200, color="red", panel=0)
    mplfinance.plot(dataframe, type="candle", volume=True, addplot=[buylot, selllot], savefig=sys.argv[2])
