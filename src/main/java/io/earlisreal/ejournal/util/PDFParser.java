package io.earlisreal.ejournal.util;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;

import java.io.File;
import java.io.IOException;

public interface PDFParser {

    static String parse(String path) {
        try (PDDocument document = PDDocument.load(new File(path))) {
            return strip(document);
        } catch (IOException e) {
            CommonUtil.handleException(e);
        }

        return null;
    }

    static String parse(byte[] data) {
        try (PDDocument document = PDDocument.load(data)) {
            return strip(document);
        } catch (IOException e) {
            CommonUtil.handleException(e);
        }

        return null;
    }

    private static String strip(PDDocument document) throws IOException {
        PDFTextStripper stripper = new PDFTextStripper();
        stripper.setSortByPosition(true);
        return stripper.getText(document);
    }

}
