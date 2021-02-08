package io.earlisreal.ejournal.parser.email;

import io.earlisreal.ejournal.util.Broker;

public interface EmailParserFactory {

    static EmailParser getEmailParser(Broker broker) {
        if (Broker.COL == broker) {
            return new COLFinancialEmailParser();
        }

        throw new RuntimeException("Broker name " + broker + " is not supported");
    }

}
