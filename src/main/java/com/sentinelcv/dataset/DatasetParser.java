package com.sentinelcv.dataset;

import com.sentinelcv.enums.DatasetFormat;
import com.sentinelcv.exception.DatasetParsingException;

import java.io.File;

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
}
