package io.earlisreal.ejournal;

import io.earlisreal.ejournal.database.DerbyDatabase;
import io.earlisreal.ejournal.input.ConsoleParser;
import io.earlisreal.ejournal.input.EmailFetcher;
import io.earlisreal.ejournal.parser.invoice.InvoiceParserFactory;
import io.earlisreal.ejournal.parser.ledger.LedgerParser;
import io.earlisreal.ejournal.parser.ledger.LedgerParserFactory;
import io.earlisreal.ejournal.scraper.ScraperProvider;
import io.earlisreal.ejournal.scraper.StockScraper;
import io.earlisreal.ejournal.service.ServiceProvider;
import io.earlisreal.ejournal.ui.UILauncher;
import io.earlisreal.ejournal.util.Broker;
import io.earlisreal.ejournal.util.CommonUtil;
import io.earlisreal.ejournal.util.PDFParser;
import javafx.application.Application;

import java.io.IOException;
import java.security.GeneralSecurityException;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.Arrays;
import java.util.List;

public class EJournal {

    public static void main(String[] args) {
        System.out.println("Welcome to eJournal!");
        try (Connection ignored = DerbyDatabase.initialize()) {
            ServiceProvider.getStartupService().run();

            EJournal eJournal = new EJournal();
            eJournal.run(args);
        } catch (SQLException | GeneralSecurityException | IOException e) {
            System.out.println(e.getMessage());
            e.printStackTrace();
        }

        System.out.println("bye!");
    }

    public void run(String[] args) throws GeneralSecurityException, IOException {
        if (args.length > 0) {
            if (args[0].equals("csv")) {
                List<String> csv = new ConsoleParser().parseCsv();
                if (csv.isEmpty()) return;
                ServiceProvider.getTradeLogService().insertCsv(csv);
            }

            if (args[0].toLowerCase().contains(".pdf")) {
                System.out.println("Parsing PDF file: " + args[0]);

                if (args[0].contains("SAP_")) {
                    String ledger = PDFParser.parse(args[0]);
                    Broker broker = CommonUtil.identifyBroker(ledger);
                    LedgerParser parser = LedgerParserFactory.getLedgerParser(broker);
                    parser.parse(Arrays.asList(ledger.split(System.lineSeparator())));
                    System.out.println(parser.getBankTransactions());
                    System.out.println(parser.getTradeLogs());
                }
                else {
                    String invoice = PDFParser.parse(args[0]);
                    Broker broker = CommonUtil.identifyBroker(invoice);
                    System.out.println(broker.getName() + " Broker Found");
                    System.out.println(InvoiceParserFactory.getInvoiceParser(broker).parseAsObject(invoice));
                }
            }

            if (args[0].equals("ledger")) {
                List<String> lines = new ConsoleParser().parseLedger();
                if (lines.isEmpty()) return;

                LedgerParser parser = LedgerParserFactory.getLedgerParser(CommonUtil.identifyBroker(lines.get(0)));
                parser.parse(lines);
                ServiceProvider.getTradeLogService().insert(parser.getTradeLogs());
                ServiceProvider.getBankTransactionService().insert(parser.getBankTransactions());
            }

            if (args[0].equals("email")) {
                EmailFetcher.getInstance().parse();
            }

            if (args[0].equals("stocks")) {
                StockScraper stockListScraper = ScraperProvider.getStockListScraper();
                stockListScraper.scrape();
                var stocks = stockListScraper.scrape();
                ServiceProvider.getStockService().updateStocks(stocks);
                System.out.println(stocks);
            }
        }
        else {
            Application.launch(UILauncher.class, args);
        }
    }

}
