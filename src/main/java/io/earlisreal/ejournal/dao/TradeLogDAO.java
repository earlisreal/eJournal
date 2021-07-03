package io.earlisreal.ejournal.dao;

import io.earlisreal.ejournal.dto.TradeLog;

import java.time.LocalDate;
import java.util.List;

public interface TradeLogDAO {

    List<TradeLog> queryInBetween(LocalDate startDate, LocalDate endDate);

    List<TradeLog> queryAll();

    boolean upsert(TradeLog tradeLog);

    boolean update(TradeLog tradeLog);

    boolean insertLog(TradeLog tradeLog);

    int insertLog(List<TradeLog> tradeLog);

    int upsert(List<TradeLog> tradeLogs);

    boolean delete(int id);

}
