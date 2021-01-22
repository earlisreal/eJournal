package io.earlisreal.ejournal.dao;

import io.earlisreal.ejournal.database.DerbyDatabase;
import io.earlisreal.ejournal.dto.Plan;
import io.earlisreal.ejournal.util.CommonUtil;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import static io.earlisreal.ejournal.util.CommonUtil.toSqlDate;

public class DerbyPlanDAO implements PlanDAO {

    Connection connection = DerbyDatabase.getConnection();

    @Override
    public boolean insert(Plan plan) {
        String sql = "INSERT INTO plan (date, stock, entry, stop, risk) VALUES (?, ?, ?, ?, ?)";
        try (PreparedStatement preparedStatement = connection.prepareStatement(sql)) {
            preparedStatement.setDate(1, toSqlDate(plan.getDate()));
            preparedStatement.setString(2, plan.getStock());
            preparedStatement.setDouble(3, plan.getEntry());
            preparedStatement.setDouble(4, plan.getStop());
            preparedStatement.setDouble(5, plan.getRisk());
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

        try (PreparedStatement preparedStatement = connection.prepareStatement("SELECT * FROM plan");
             ResultSet resultSet = preparedStatement.executeQuery()) {

            while (resultSet.next()) {
                Plan plan = new Plan();
                plan.setId(resultSet.getInt(1));
                plan.setDate(LocalDate.parse(resultSet.getString(2)));
                plan.setStock(resultSet.getString(3));
                plan.setEntry(resultSet.getDouble(4));
                plan.setStop(resultSet.getDouble(5));
                plan.setRisk(resultSet.getDouble(6));
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
