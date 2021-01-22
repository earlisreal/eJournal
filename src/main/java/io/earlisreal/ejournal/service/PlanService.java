package io.earlisreal.ejournal.service;

import io.earlisreal.ejournal.dto.Plan;

import java.util.List;

public interface PlanService {

    boolean insert(Plan plan);

    List<Plan> getAll();

    boolean delete(int id);

}
