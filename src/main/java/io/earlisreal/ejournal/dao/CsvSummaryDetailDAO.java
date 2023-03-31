package io.earlisreal.ejournal.dao;

import io.earlisreal.ejournal.database.FileDatabase;
import io.earlisreal.ejournal.dto.SummaryDetail;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import static io.earlisreal.ejournal.util.Configs.DEBUG_MODE;
import static java.time.temporal.ChronoUnit.MILLIS;

public class CsvSummaryDetailDAO implements SummaryDetailDAO {

    private static final Path path = FileDatabase.getSummaryPath();

    @Override
    public List<SummaryDetail> getAll() {
        var start = Instant.now();
        List<SummaryDetail> list = new ArrayList<>();
        try {
            StringBuilder builder = new StringBuilder();
            SummaryDetail summary = new SummaryDetail();
            for (String line : Files.readAllLines(path)) {
                var columns = line.split(",");
                if (columns.length < 2) {
                    builder.append(System.lineSeparator());
                    builder.append(line);
                    continue;
                }

                if (!builder.isEmpty()) {
                    summary.setRemarks(builder.toString());
                    list.add(summary);
                    summary = new SummaryDetail();
                    builder = new StringBuilder();
                }

                summary.setId(columns[0]);
                summary.setRating(Integer.parseInt(columns[1]));
                if (columns.length > 2) {
                    builder.append(columns[2]);
                }
            }

            summary.setRemarks(builder.toString());
            list.add(summary);
        } catch (IOException e) {
            throw new RuntimeException(e);
        } finally {
            if (DEBUG_MODE)
                System.out.println("Read all detail: " + MILLIS.between(start, Instant.now()));
        }
        return list;
    }

    @Override
    public boolean insert(SummaryDetail summaryDetail) {
        var start = Instant.now();
        try (var writer = Files.newBufferedWriter(path, StandardOpenOption.APPEND);) {
            writer.append(summaryDetail.toCsv());
            writer.newLine();
            return true;
        } catch (IOException e) {
            throw new RuntimeException(e);
        } finally {
            if (DEBUG_MODE)
                System.out.println("Insert detail: " + MILLIS.between(start, Instant.now()));
        }
    }

    @Override
    public boolean update(SummaryDetail summaryDetail) {
        var start = Instant.now();
        try {
            var lines = Files.readAllLines(path);
            for (int i = 0; i < lines.size(); i++) {
                if (lines.get(i).startsWith(summaryDetail.getId())) {
                    lines.set(i, summaryDetail.toCsv());
                    break;
                }
            }
            Files.write(path, lines);
        } catch (IOException e) {
            throw new RuntimeException(e);
        } finally {
            if (DEBUG_MODE)
                System.out.println("Update detail: " + MILLIS.between(start, Instant.now()));
        }
        return true;
    }

}
