package io.earlisreal.ejournal.dao;

import io.earlisreal.ejournal.database.DerbyDatabase;
import io.earlisreal.ejournal.dto.Plan;
import io.earlisreal.ejournal.util.CommonUtil;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

public class DerbyPlanDAO implements PlanDAO {

    Connection connection = DerbyDatabase.getConnection();

    @Override
    public boolean insert(Plan plan) {
        String sql = "INSERT INTO plan (stock, entry, stop, risk) VALUES (?, ?, ?, ?)";
        try (PreparedStatement preparedStatement = connection.prepareStatement(sql)) {
            preparedStatement.setString(1, plan.getStock());
            preparedStatement.setDouble(2, plan.getEntry());
            preparedStatement.setDouble(3, plan.getStop());
            preparedStatement.setDouble(4, plan.getRisk());
            preparedStatement.execute();
            return preparedStatement.getUpdateCount() > 0;
        } catch (SQLException sqlException) {
            CommonUtil.handleException(sqlException);
        }

        return false;
    }

    @Override
    public List<Plan> getAll() {
        List<Plan> plans = new ArrayList<>();

        try (PreparedStatement preparedStatement = connection.prepareStatement("SELECT * FROM strategy");
             ResultSet resultSet = preparedStatement.executeQuery()) {

            while (resultSet.next()) {
                Plan plan = new Plan();
                plan.setStock(resultSet.getString(1));
                plan.setEntry(resultSet.getDouble(2));
                plan.setStop(resultSet.getDouble(3));
                plan.setRisk(resultSet.getDouble(4));
                plans.add(plan);
            }
        } catch (SQLException sqlException) {
            CommonUtil.handleException(sqlException);
        }

        return plans;
    }

    @Override
    public boolean delete(int id) {
        String sql = "DELETE FROM plan WHERE id = ?";
        try (PreparedStatement preparedStatement = connection.prepareStatement(sql)) {
            preparedStatement.setInt(1, id);
            preparedStatement.execute();
            return preparedStatement.getUpdateCount() > 0;
        } catch (SQLException sqlException) {
            CommonUtil.handleException(sqlException);
        }
        return false;
    }

}
