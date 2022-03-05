package io.earlisreal.ejournal.service;

import io.earlisreal.ejournal.dto.Portfolio;

import java.util.List;

public interface PortfolioService {

    List<Portfolio> getAll();

    boolean savePortfolio(Portfolio portfolio);

}
