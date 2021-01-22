package io.earlisreal.ejournal.dao;

import io.earlisreal.ejournal.dto.Plan;

import java.util.List;

public interface PlanDAO {

    boolean insert(Plan plan);

    List<Plan> getAll();

    boolean delete(int id);

}
