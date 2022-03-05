package io.earlisreal.ejournal.dao;

import io.earlisreal.ejournal.dto.Portfolio;

import java.util.List;

public interface PortfolioDAO {

    List<Portfolio> getAll();

    boolean save(Portfolio portfolio);

}
