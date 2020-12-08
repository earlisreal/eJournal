package io.earlisreal.ejournal.service;

import io.earlisreal.ejournal.dao.StrategyDAO;
import io.earlisreal.ejournal.dto.Strategy;

import java.util.List;

public class SimpleStrategyService implements StrategyService {

    private final StrategyDAO strategyDAO;

    SimpleStrategyService(StrategyDAO strategyDAO) {
        this.strategyDAO = strategyDAO;
    }

    @Override
    public List<Strategy> getAll() {
        return strategyDAO.queryAll();
    }

    @Override
    public void insert(Strategy strategy) {
        boolean success = strategyDAO.insert(strategy);
        if (success) {
            System.out.println("Strategy inserted");
        }
        else {
            System.out.println("Failed to insert Strategy");
        }
    }

}
