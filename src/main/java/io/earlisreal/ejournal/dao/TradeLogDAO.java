package io.earlisreal.ejournal.dao;

import io.earlisreal.ejournal.dto.TradeLog;

import java.util.List;

public interface TradeLogDAO {

    List<TradeLog> queryAll();

    boolean update(TradeLog tradeLog);

    boolean insertLog(TradeLog tradeLog);

    List<TradeLog> insertLog(List<TradeLog> tradeLog);

    boolean delete(int id);

}
