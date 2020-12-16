package io.earlisreal.ejournal.scraper;

import io.earlisreal.ejournal.service.ServiceProvider;
import io.earlisreal.ejournal.util.Broker;

public class EmailScrapperFactory {

    private EmailScrapperFactory() {}

    private static COLFinancialEmailScraper colFinancialEmailScraper;
    private static SimpleEmailScraper simpleEmailScraper;

    public static EmailScraper getEmailScraper(Broker broker) {
        if (Broker.COL == broker) {
            if (colFinancialEmailScraper == null) {
                synchronized (EmailScrapperFactory.class) {
                    if (colFinancialEmailScraper == null) {
                        colFinancialEmailScraper = new COLFinancialEmailScraper(ServiceProvider.getTradeLogService());
                    }
                }
            }

            return colFinancialEmailScraper;
        }

        if (Broker.AAA == broker || Broker.YAPSTER == broker) {
            if (simpleEmailScraper == null) {
                synchronized (EmailScrapperFactory.class) {
                    if (simpleEmailScraper == null) {
                        simpleEmailScraper = new SimpleEmailScraper(ServiceProvider.getTradeLogService());
                    }
                }
            }

            return simpleEmailScraper;
        }

        throw new RuntimeException("Invalid Broker: " + broker.getName());
    }

}
