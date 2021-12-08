package io.earlisreal.ejournal.dao;

import com.jsoniter.JsonIterator;
import com.jsoniter.output.JsonStream;
import io.earlisreal.ejournal.dto.AlphaSummary;
import io.earlisreal.ejournal.model.TradeSummary;
import io.earlisreal.ejournal.util.CommonUtil;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static io.earlisreal.ejournal.util.CommonUtil.handleException;

public class SQLAlphaSummaryDAO implements AlphaSummaryDAO {

    private final Connection connection;

    SQLAlphaSummaryDAO(Connection connection) {
        this.connection = connection;
    }

    @Override
    public List<AlphaSummary> getAll() {
        List<AlphaSummary> summaries = new ArrayList<>();
        try (PreparedStatement preparedStatement = connection.prepareStatement("SELECT * FROM alpha_summary");
             ResultSet resultSet = preparedStatement.executeQuery()) {

            while (resultSet.next()) {
                AlphaSummary alphaSummary = new AlphaSummary();
                alphaSummary.setId(resultSet.getString(1));
                var summaryArray = JsonIterator.deserialize(resultSet.getString(2), TradeSummary[].class);
                alphaSummary.setSummaries(Arrays.asList(summaryArray));
                summaries.add(alphaSummary);
            }
        } catch (SQLException sqlException) {
            CommonUtil.handleException(sqlException);
        }

        return summaries;
    }

    @Override
    public boolean insert(List<TradeSummary> summaries) {
        String sql = "INSERT INTO alpha_summary (json) VALUES (?)";
        try (PreparedStatement preparedStatement = connection.prepareStatement(sql)) {
            preparedStatement.setString(1, JsonStream.serialize(summaries));
            preparedStatement.execute();
            return preparedStatement.getUpdateCount() > 0;
        } catch (SQLException sqlException) {
            handleException(sqlException);
            return false;
        }
    }

    @Override
    public boolean delete(int id) {
        String sql = "DELETE FROM alpha_summary WHERE id = ?";
        try (PreparedStatement preparedStatement = connection.prepareStatement(sql)) {
            preparedStatement.setInt(1, id);
            preparedStatement.execute();
            return preparedStatement.getUpdateCount() > 0;
        } catch (SQLException sqlException) {
            handleException(sqlException);
            return false;
        }
    }

}
