package io.earlisreal.ejournal.service;

import io.earlisreal.ejournal.dto.Strategy;

import java.util.List;

public interface StrategyService {

    List<Strategy> getAll();

    void insert(Strategy strategy);

}
