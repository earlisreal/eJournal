package io.earlisreal.ejournal.dao;

import io.earlisreal.ejournal.dto.Plan;

import java.util.List;

public class CsvPlanDAO implements PlanDAO {

    @Override
    public boolean insert(Plan plan) {
        return false;
    }

    @Override
    public List<Plan> getAll() {
        return null;
    }

    @Override
    public boolean delete(int id) {
        return false;
    }

}
