package io.earlisreal.ejournal.model;

import io.earlisreal.ejournal.dto.TradeLog;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class TradeSummaryBuilder {

    private final Map<String, TradeSummary> trades = new HashMap<>();
    private final List<TradeSummary> summaries = new ArrayList<>();

    public TradeSummaryBuilder(List<TradeLog> tradeLogs) {
        tradeLogs.sort(Comparator.comparing(TradeLog::getDate).thenComparing(tradeLog -> !tradeLog.isBuy()));
        for (TradeLog log : tradeLogs) {
            String stock = log.getStock();
            if (trades.containsKey(stock)) {
                var trade = trades.get(stock);
                if (log.isBuy()) {
                    trade.buy(log);
                    if (trade.isShort()) {
                        log.setProfit((trade.getAverageSell() - log.getPrice()) * log.getShares());
                    }
                }
                else {
                    trade.sell(log);
                    if (!trade.isShort()) {
                        log.setProfit((log.getPrice() - trade.getAverageBuy()) * log.getShares());
                    }
                }
                if (trade.isClosed()) {
                    trades.remove(stock);
                    trade.setCloseDate(log.getDate());
                    summaries.add(trade);
                }
            }
            else {
                var trade = new TradeSummary(log);
                trades.put(stock, trade);
            }
        }
    }

    public List<TradeSummary> getSummaries() {
        return summaries;
    }

    public Collection<TradeSummary> getOpenPositions() {
        return trades.values();
    }

}
