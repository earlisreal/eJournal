package io.earlisreal.ejournal.dao;

import io.earlisreal.ejournal.database.FileDatabase;
import io.earlisreal.ejournal.dto.TradeLog;
import io.earlisreal.ejournal.util.ParseUtil;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class CsvTradeLogDAO implements TradeLogDAO {

    private final FileDatabase fileDatabase;

    public CsvTradeLogDAO(FileDatabase fileDatabase) {
        this.fileDatabase = fileDatabase;
    }

    @Override
    public List<TradeLog> queryAll() {
        final BufferedReader reader = fileDatabase.getReader();
        List<TradeLog> logs = new ArrayList<>();
        while (true) {
            String line = null;
            try {
                line = reader.readLine();
            } catch (IOException e) {
                System.out.println(e.getMessage());
            }

            if (line == null) break;

            ParseUtil.parseRecord(line).ifPresent(logs::add);
        }
        return logs;
    }

    @Override
    public boolean update(TradeLog tradeLog) {
        return false;
    }

    @Override
    public boolean insertLog(TradeLog tradeLog) {
        final BufferedWriter writer = fileDatabase.getWriter();
        try {
            writer.write(tradeLog.toCsv());
            writer.newLine();
            return true;
        } catch (IOException e) {
            System.out.println(e.getMessage());
            return false;
        }
    }

    @Override
    public List<TradeLog> insertLog(List<TradeLog> tradeLog) {
        return null;
    }

    @Override
    public boolean delete(int id) {
        return false;
    }

}
