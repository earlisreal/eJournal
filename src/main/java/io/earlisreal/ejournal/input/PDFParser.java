package io.earlisreal.ejournal.input;

import io.earlisreal.ejournal.util.CommonUtil;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;

import java.io.File;
import java.io.IOException;

public class PDFParser {

    public String parse(String path) {
        try (PDDocument document = PDDocument.load(new File(path))) {
            return strip(document);
        } catch (IOException e) {
            CommonUtil.handleException(e);
        }

        return null;
    }

    public String parse(byte[] data) {
        try (PDDocument document = PDDocument.load(data)) {
            return strip(document);
        } catch (IOException e) {
            CommonUtil.handleException(e);
        }

        return null;
    }

    private String strip(PDDocument document) throws IOException {
        PDFTextStripper stripper = new PDFTextStripper();
        stripper.setSortByPosition(true);
        return stripper.getText(document);
    }

}
