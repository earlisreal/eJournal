package io.earlisreal.ejournal.dao;

import io.earlisreal.ejournal.dto.TradeLog;

import java.util.List;

public interface TradeLogDAO {

    List<TradeLog> queryAll();

    boolean insertLog(TradeLog tradeLog);

    int insertLog(List<TradeLog> tradeLog);

}
