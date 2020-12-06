package io.earlisreal.ejournal;

import io.earlisreal.ejournal.database.DerbyDatabase;
import io.earlisreal.ejournal.identifier.BrokerIdentifier;
import io.earlisreal.ejournal.input.ConsoleParser;
import io.earlisreal.ejournal.input.PDFParser;
import io.earlisreal.ejournal.parser.InvoiceParserFactory;
import io.earlisreal.ejournal.service.ServiceProvider;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.List;

public class EJournal {

    public static void main(String[] args) {
        System.out.println("Welcome to eJournal!");
        try (Connection ignored = DerbyDatabase.initialize()) {
            EJournal eJournal = new EJournal();
            eJournal.run(args);
        } catch (SQLException e) {
            System.out.println(e.getMessage());
            e.printStackTrace();
        }

        System.out.println("bye!");
    }

    public void run(String[] args) {
        if (args.length > 0) {
            if (args[0].equals("csv")) {
                List<String> csv = new ConsoleParser().parseCsv();
                ServiceProvider.getTradeLogService().insertCsv(csv);
                return;
            }

            if (args[0].toLowerCase().contains(".pdf")) {
                System.out.println("Parsing PDF file: " + args[0]);

                String invoice = new PDFParser().parse(args[0]);
                String broker = BrokerIdentifier.identify(invoice);
                System.out.println(broker + " Broker Found");
                System.out.println(InvoiceParserFactory.getInvoiceParser(broker).parseAsCsv(invoice));
            }
        }
    }

}