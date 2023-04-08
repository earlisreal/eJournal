package io.earlisreal.ejournal;

import io.earlisreal.ejournal.input.ConsoleParser;
import io.earlisreal.ejournal.input.EmailFetcher;
import io.earlisreal.ejournal.parser.invoice.InvoiceParserFactory;
import io.earlisreal.ejournal.parser.ledger.LedgerParser;
import io.earlisreal.ejournal.parser.ledger.LedgerParserFactory;
import io.earlisreal.ejournal.scraper.ScraperProvider;
import io.earlisreal.ejournal.scraper.StockScraper;
import io.earlisreal.ejournal.service.ServiceProvider;
import io.earlisreal.ejournal.util.Broker;
import io.earlisreal.ejournal.util.CommonUtil;
import io.earlisreal.ejournal.util.Country;
import io.earlisreal.ejournal.util.PDFParser;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.security.GeneralSecurityException;
import java.util.Arrays;
import java.util.List;

public class EjournalTest {

    @Test
    void parseCsv() {
        List<String> csv = new ConsoleParser().parseCsv();
        if (csv.isEmpty()) return;
        ServiceProvider.getTradeLogService().insertCsv(csv);
    }

    @Test
    void parsePdf() {
        final String fileName = "INSERT FILE NAME HERE";
        System.out.println("Parsing PDF file: " + fileName);

        if (fileName.contains("SAP_")) {
            String ledger = PDFParser.parse(fileName);
            Broker broker = CommonUtil.identifyBroker(ledger);
            LedgerParser parser = LedgerParserFactory.getLedgerParser(broker);
            parser.parse(Arrays.asList(ledger.split(System.lineSeparator())));
            System.out.println(parser.getBankTransactions());
            System.out.println(parser.getTradeLogs());
        }
        else {
            String invoice = PDFParser.parse(fileName);
            Broker broker = CommonUtil.identifyBroker(invoice);
            System.out.println(broker.getName() + " Broker Found");
            System.out.println(InvoiceParserFactory.getInvoiceParser(broker).parseAsObject(invoice));
        }
    }

    @Test
    void parseLedger() {
        List<String> lines = new ConsoleParser().parseLedger();
        if (lines.isEmpty()) return;

        LedgerParser parser = LedgerParserFactory.getLedgerParser(CommonUtil.identifyBroker(lines.get(0)));
        parser.parse(lines);
        ServiceProvider.getTradeLogService().insert(parser.getTradeLogs());
        ServiceProvider.getBankTransactionService().insert(parser.getBankTransactions());
    }

    @Test
    void scrapeEmail() throws GeneralSecurityException, IOException {
        EmailFetcher.getInstance().parse();
    }

    @Test
    void scrapeStocks() {
        StockScraper stockListScraper = ScraperProvider.getStockListScraper();
        stockListScraper.scrape();
        var stocks = stockListScraper.scrape();
        ServiceProvider.getStockService().updateStocks(stocks, Country.US);
        System.out.println(stocks);
    }

}
