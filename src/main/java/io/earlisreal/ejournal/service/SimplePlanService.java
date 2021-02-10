package io.earlisreal.ejournal.service;

import io.earlisreal.ejournal.dao.PlanDAO;
import io.earlisreal.ejournal.dto.Plan;

import java.util.List;

public class SimplePlanService implements PlanService {

    private final PlanDAO planDAO;

    SimplePlanService(PlanDAO planDAO) {
        this.planDAO = planDAO;
    }

    @Override
    public boolean insert(Plan plan) {
        return planDAO.insert(plan);
    }

    @Override
    public List<Plan> getAll() {
        return planDAO.getAll();
    }

    @Override
    public boolean delete(int id) {
        return planDAO.delete(id);
    }

}
