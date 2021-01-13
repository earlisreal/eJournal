package io.earlisreal.ejournal.service;

import io.earlisreal.ejournal.model.TradeSummary;

import java.io.IOException;
import java.nio.file.Path;

public interface PlotService {

    Path plot(TradeSummary tradeSummary) throws IOException;

}
