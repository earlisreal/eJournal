package io.earlisreal.ejournal.input;

import java.util.ArrayList;
import java.util.List;
import java.util.Scanner;

public class ConsoleParser {

    public List<String> parseCsv() {
        System.out.println("Follow this csv format - date (yyyy-mm-dd), stock, action (buy/sell), price, shares, strategy, type (long/short)");
        System.out.println("Enter csv records:");
        List<String> records = new ArrayList<>();
        Scanner scanner = new Scanner(System.in);
        while (true) {
            String line = scanner.nextLine();
            if (line.isBlank()) {
                break;
            }

            records.add(line);
        }

        return records;
    }

}
