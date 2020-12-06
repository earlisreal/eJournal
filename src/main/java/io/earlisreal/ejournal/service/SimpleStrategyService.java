package io.earlisreal.ejournal.service;

import io.earlisreal.ejournal.dao.StrategyDAO;

public class SimpleStrategyService implements StrategyService {

    private final StrategyDAO strategyDAO;

    SimpleStrategyService(StrategyDAO strategyDAO) {
        this.strategyDAO = strategyDAO;
    }

}
