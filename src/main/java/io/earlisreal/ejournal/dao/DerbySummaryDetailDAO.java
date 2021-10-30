package io.earlisreal.ejournal.dao;


import io.earlisreal.ejournal.dto.SummaryDetail;
import io.earlisreal.ejournal.util.CommonUtil;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

import static io.earlisreal.ejournal.util.CommonUtil.handleException;

public class DerbySummaryDetailDAO implements SummaryDetailDAO {

    private final Connection connection;

    DerbySummaryDetailDAO(Connection connection) {
        this.connection = connection;
    }

    @Override
    public List<SummaryDetail> getAll() {
        List<SummaryDetail> details = new ArrayList<>();
        try (PreparedStatement preparedStatement = connection.prepareStatement("SELECT * FROM summary_detail");
             ResultSet resultSet = preparedStatement.executeQuery()) {

            while (resultSet.next()) {
                SummaryDetail detail = new SummaryDetail();
                detail.setId(resultSet.getString(1));
                detail.setRating(resultSet.getInt(2));
                detail.setRemarks(resultSet.getString(3));
                details.add(detail);
            }
        } catch (SQLException sqlException) {
            CommonUtil.handleException(sqlException);
        }

        return details;
    }

    @Override
    public boolean insert(SummaryDetail summaryDetail) {
        String sql = "INSERT INTO summary_detail (id, rating, remarks) VALUES (?, ?, ?)";
        try (PreparedStatement preparedStatement = connection.prepareStatement(sql)) {
            preparedStatement.setString(1, summaryDetail.getId());
            preparedStatement.setInt(2, summaryDetail.getRating());
            preparedStatement.setString(3, summaryDetail.getRemarks());
            preparedStatement.execute();
            return preparedStatement.getUpdateCount() > 0;
        } catch (SQLException sqlException) {
            handleException(sqlException);
            return false;
        }
    }

    @Override
    public boolean update(SummaryDetail summaryDetail) {
        String sql = "UPDATE summary_detail SET rating = ?, remarks = ? WHERE id = ?";
        try (PreparedStatement preparedStatement = connection.prepareStatement(sql)) {
            preparedStatement.setInt(1, summaryDetail.getRating());
            preparedStatement.setString(2, summaryDetail.getRemarks());
            preparedStatement.setString(3, summaryDetail.getId());
            preparedStatement.execute();
            return preparedStatement.getUpdateCount() > 0;
        } catch (SQLException sqlException) {
            handleException(sqlException);
            return false;
        }
    }

}
