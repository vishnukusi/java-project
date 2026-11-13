package com.sentinelcv.dataset;

import com.sentinelcv.entity.Dataset;
import com.sentinelcv.enums.DatasetFormat;
import com.sentinelcv.exception.DatasetParsingException;

import java.io.File;
import java.util.List;
import java.util.Objects;

/**
 * Strategy interface for parsing different computer vision dataset formats.
 * Demonstrates the Strategy design pattern and Polymorphism.
 */
public interface DatasetParser {

    /**
     * Determines whether this parser can handle the dataset at the specified directory.
     */
    boolean canParse(File datasetDir);

    /**
     * Parses the dataset at the specified directory and returns a structured ParseResult.
     */
    DatasetModels.ParseResult parse(File datasetDir) throws DatasetParsingException;

    /**
     * Returns the dataset format handled by this parser.
     */
    DatasetFormat getFormat();

    /**
     * Strictly resolves a parser matching the persisted authoritative {@link DatasetFormat}.
     * Fails closed on missing or unsupported formats and never falls back to structural heuristics.
     */
    static DatasetParser resolveAuthoritativeParser(List<DatasetParser> parsers, Dataset dataset) {
        if (dataset == null || dataset.getFormat() == null) {
            throw new DatasetParsingException(
                    "Dataset format is missing for dataset: " + (dataset != null ? dataset.getName() : "null"));
        }
        if (parsers == null || parsers.isEmpty()) {
            throw new DatasetParsingException(
                    "No dataset parsers are registered to handle format: " + dataset.getFormat());
        }
        DatasetFormat authoritativeFormat = dataset.getFormat();
        return parsers.stream()
                .filter(Objects::nonNull)
                .filter(p -> p.getFormat() == authoritativeFormat)
                .findFirst()
                .orElseThrow(() -> new DatasetParsingException(
                        "Unsupported or unconfigured dataset format '" + authoritativeFormat
                                + "' for dataset: " + dataset.getName()));
    }
}
