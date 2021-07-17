package io.earlisreal.ejournal.parser.ledger;

import io.earlisreal.ejournal.dto.BankTransaction;
import io.earlisreal.ejournal.dto.TradeLog;
import io.earlisreal.ejournal.util.Broker;
import io.earlisreal.ejournal.util.CommonUtil;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.text.ParseException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class EToroLedgerParser implements LedgerParser {

    private final List<TradeLog> tradeLogs;
    private final List<BankTransaction> bankTransactions;
    private final Map<String, String> positionMap;

    EToroLedgerParser() {
        this.tradeLogs = new ArrayList<>();
        this.bankTransactions = new ArrayList<>();
        this.positionMap = new HashMap<>();
    }

    @Override
    public void parse(String filename) {
        try (var workbook = new XSSFWorkbook(Files.newInputStream(Paths.get(filename)))) {
            parseTransactionsReport(workbook);
            parseClosedPositions(workbook);

        } catch (IOException | ParseException e) {
            CommonUtil.handleException(e);
        }
    }

    private void parseTransactionsReport(XSSFWorkbook workbook) {
        var sheet = workbook.getSheet("Transactions Report");
        var rows = sheet.iterator();
        rows.next();
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("uuuu-MM-dd HH:mm:ss");

        while (rows.hasNext()) {
            Row row = rows.next();
            String type = row.getCell(2).getStringCellValue();
            String details = row.getCell(3) == null ? "" : row.getCell(3).getStringCellValue();
            LocalDateTime dateTime = LocalDateTime.parse(row.getCell(0).getStringCellValue(), formatter);

            if (type.equals("Deposit") || type.startsWith("Withdraw ") || type.equals("Adjustment") || type.equals("Rollover Fee")) {
                StringBuilder reference = new StringBuilder();
                for (String token : type.split(" ")) {
                    reference.append(token.charAt(0));
                }
                BankTransaction transaction = new BankTransaction();
                transaction.setAmount(row.getCell(5).getNumericCellValue());
                transaction.setDate(dateTime.toLocalDate());
                transaction.setBroker(Broker.ETORO);
                transaction.setDividend(details.contains("dividend"));
                if (transaction.isDividend()) {
                    transaction.setReferenceNo("D" + row.getCell(4).getStringCellValue());
                }
                else {
                    transaction.setReferenceNo(reference + String.valueOf(dateTime.toEpochSecond(ZoneOffset.UTC)));
                }

                bankTransactions.add(transaction);
            }
            if (type.equals("Profit/Loss of Trade")) {
                String stock = details.substring(0, details.indexOf('/'));
                if (stock.contains(".")) stock = stock.substring(0, stock.indexOf('.'));
                positionMap.put(row.getCell(4).getStringCellValue(), stock);
            }
        }


    }

    private void parseClosedPositions(XSSFWorkbook workbook) throws ParseException {
        var sheet = workbook.getSheet("Closed Positions");
        var rows = sheet.iterator();
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("dd/MM/uuuu HH:mm");
        rows.next();
        while (rows.hasNext()) {
            Row row = rows.next();
            String positionId = row.getCell(0).getStringCellValue();
            double shares = parseDouble(row.getCell(4));

            LocalDate openDate = LocalDate.parse(row.getCell(9).getStringCellValue(), formatter);
            double openPrice = parseDouble(row.getCell(5));
            TradeLog open = makeLog(openDate, shares, openPrice, true, positionId);

            LocalDate closeDate = LocalDate.parse(row.getCell(10).getStringCellValue(), formatter);
            double closePrice = parseDouble(row.getCell(6));
            TradeLog close = makeLog(closeDate, shares, closePrice, false, positionId);

            tradeLogs.add(open);
            tradeLogs.add(close);
        }
    }

    private double parseDouble(Cell cell) throws ParseException {
        return CommonUtil.parseDouble(cell.getStringCellValue());
    }

    private TradeLog makeLog(LocalDate date, double shares, double price, boolean isBuy, String positionId) {
        TradeLog log = new TradeLog();
        log.setDate(date);
        log.setBroker(Broker.ETORO);
        log.setInvoiceNo((isBuy ? "B" : "S") + positionId);
        log.setBuy(isBuy);
        log.setPrice(price);
        log.setShares(shares);
        log.setStock(positionMap.get(positionId));
        return log;
    }

    @Override
    public List<TradeLog> getTradeLogs() {
        return tradeLogs;
    }

    @Override
    public List<BankTransaction> getBankTransactions() {
        return bankTransactions;
    }

}
