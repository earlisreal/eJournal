package io.earlisreal.ejournal.dao;

import java.time.Instant;

public interface TradeLogDAO {

    boolean insertLog(Instant date, String stock, boolean isBuy, double price, int shares, String strategy, boolean isShort);

}
