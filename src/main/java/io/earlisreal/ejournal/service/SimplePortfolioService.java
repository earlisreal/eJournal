package io.earlisreal.ejournal.service;

import io.earlisreal.ejournal.dao.PortfolioDAO;
import io.earlisreal.ejournal.dto.Portfolio;

import java.util.List;

public class SimplePortfolioService implements PortfolioService {

    private final PortfolioDAO portfolioDAO;

    public SimplePortfolioService(PortfolioDAO portfolioDAO) {
        this.portfolioDAO = portfolioDAO;
    }

    @Override
    public List<Portfolio> getAll() {
        return portfolioDAO.getAll();
    }

    @Override
    public boolean savePortfolio(Portfolio portfolio) {
        return portfolioDAO.save(portfolio);
    }

}
