package io.earlisreal.ejournal.scraper;

import com.google.api.services.gmail.Gmail;
import com.google.api.services.gmail.model.Message;
import com.google.api.services.gmail.model.MessagePart;
import io.earlisreal.ejournal.dto.BankTransaction;
import io.earlisreal.ejournal.dto.TradeLog;
import io.earlisreal.ejournal.parser.invoice.InvoiceParserFactory;
import io.earlisreal.ejournal.parser.ledger.LedgerParser;
import io.earlisreal.ejournal.parser.ledger.LedgerParserFactory;
import io.earlisreal.ejournal.service.BankTransactionService;
import io.earlisreal.ejournal.service.ServiceProvider;
import io.earlisreal.ejournal.service.TradeLogService;
import io.earlisreal.ejournal.util.Broker;
import io.earlisreal.ejournal.util.CommonUtil;
import io.earlisreal.ejournal.util.PDFParser;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class EmailAttachmentScraper {

    public static final String USER = "me";

    private final TradeLogService tradeLogService;
    private final BankTransactionService bankTransactionService;
    private static EmailAttachmentScraper emailAttachmentScraper;

    EmailAttachmentScraper(TradeLogService tradeLogService, BankTransactionService bankTransactionService) {
        this.tradeLogService = tradeLogService;
        this.bankTransactionService = bankTransactionService;
    }

    public int scrape(Gmail gmail, String messageId) {
        Message message;
        try {
            message = gmail.users().messages().get(USER, messageId).execute();
        } catch (IOException e) {
            CommonUtil.handleException(e);
            return 0;
        }

        List<TradeLog> tradeLogs = new ArrayList<>();
        List<BankTransaction> bankTransactions = new ArrayList<>();

        var attachments = getAttachments(message.getPayload());
        for (MessagePart attachment : attachments) {
            try {
                var body = gmail.users().messages()
                        .attachments().get(USER, message.getId(), attachment.getBody().getAttachmentId()).execute();
                byte[] data = body.decodeData();
                String rawData = PDFParser.parse(data);
                if (rawData == null) continue;

                Broker broker = CommonUtil.identifyBroker(rawData);
                String filename = attachment.getFilename();
                if (filename.startsWith("PDFAPAR")) {
                    TradeLog tradeLog = InvoiceParserFactory.getInvoiceParser(broker).parseAsObject(rawData);
                    tradeLogs.add(tradeLog);
                }
                else {
                    LedgerParser parser = LedgerParserFactory.getLedgerParser(broker);
                    parser.parse(Arrays.asList(rawData.split(System.lineSeparator())));
                    bankTransactions.addAll(parser.getBankTransactions());
                    tradeLogs.addAll(parser.getTradeLogs());
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
        }

        int res = 0;
        res += tradeLogService.insert(tradeLogs);
        res += bankTransactionService.insert(bankTransactions);
        return res;
    }

    private List<MessagePart> getAttachments(MessagePart messagePart) {
        List<MessagePart> attachmentIds = new ArrayList<>();
        if (messagePart.getFilename().toLowerCase().endsWith(".pdf")) {
            attachmentIds.add(messagePart);
        }

        if (messagePart.getParts() != null) {
            for (MessagePart part : messagePart.getParts()) {
                attachmentIds.addAll(getAttachments(part));
            }
        }

        return attachmentIds;
    }

    public static EmailAttachmentScraper getInstance() {
        if (emailAttachmentScraper == null) {
            synchronized (EmailAttachmentScraper.class) {
                if (emailAttachmentScraper == null) {
                    emailAttachmentScraper = new EmailAttachmentScraper(ServiceProvider.getTradeLogService(),
                            ServiceProvider.getBankTransactionService());
                }
            }
        }

        return emailAttachmentScraper;
    }

}
