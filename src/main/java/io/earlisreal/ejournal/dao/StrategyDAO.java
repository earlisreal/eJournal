package io.earlisreal.ejournal.dao;

import io.earlisreal.ejournal.dto.Strategy;

import java.util.List;

public interface StrategyDAO {

    List<Strategy> queryAll();

    boolean insert(Strategy strategy);

}
