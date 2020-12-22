package io.earlisreal.ejournal.input;

import java.util.ArrayList;
import java.util.List;
import java.util.Scanner;

public class ConsoleParser {

    public List<String> parseCsv() {
        System.out.println("Follow this csv format - date (yyyy-mm-dd), stock, action (buy/sell), price, shares, strategy, type (long/short)");
        System.out.println("Enter csv records. Blank line to stop:");
        return scan(1);
    }

    public List<String> parseLedger() {
        System.out.println("Paste the Ledger here with 2 blank lines at the end:");
        return scan(2);
    }

    private List<String> scan(int blankLines) {
        int blank = 0;
        Scanner scanner = new Scanner(System.in);

        List<String> records = new ArrayList<>();
        while (true) {
            String line = scanner.nextLine();
            if (line.isBlank()) {
                ++blank;
            }
            else {
                blank = 0;
            }
            if (blank == blankLines) {
                break;
            }

            records.add(line);
        }

        return records;
    }

}
