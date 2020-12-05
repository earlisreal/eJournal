package io.earlisreal.ejournal.input;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;

import java.io.File;
import java.io.IOException;

public class PDFParser {

    public String parse(String path) {
        try (PDDocument document = PDDocument.load(new File(path))) {
            PDFTextStripper stripper = new PDFTextStripper();
            stripper.setSortByPosition(true);
            return stripper.getText(document);
        } catch (IOException e) {
            System.out.println(e.getMessage());
            e.printStackTrace();
        }

        return null;
    }

}
