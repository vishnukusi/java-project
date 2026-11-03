package com.sentinelcv.drift;

import com.sentinelcv.dataset.DatasetModels;
import com.sentinelcv.util.ImageMetricsUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

/**
 * Extracts and aggregates image-level feature distributions across a dataset.
 */
@Component
public class FeatureStatistics {

    private static final Logger logger = LoggerFactory.getLogger(FeatureStatistics.class);

    public static class DatasetFeatureDistributions {
        public String datasetName;
        public int totalImages;
        public double[] brightnessValues;
        public double[] contrastValues;
        public double[] meanIntensityValues;
        public double[] redMeans;
        public double[] greenMeans;
        public double[] blueMeans;
        public double[] aspectRatios;
        public double[] widths;
        public double[] heights;

        public double avgBrightness;
        public double avgContrast;
        public double avgMeanIntensity;
        public double avgRed;
        public double avgGreen;
        public double avgBlue;
        public double avgAspectRatio;
    }

    public DatasetFeatureDistributions extractDistributions(File datasetDir, List<DatasetModels.ImageInfo> images) {
        DatasetFeatureDistributions dist = new DatasetFeatureDistributions();
        dist.datasetName = datasetDir.getName();

        List<Double> brightnessList = new ArrayList<>();
        List<Double> contrastList = new ArrayList<>();
        List<Double> intensityList = new ArrayList<>();
        List<Double> rList = new ArrayList<>();
        List<Double> gList = new ArrayList<>();
        List<Double> bList = new ArrayList<>();
        List<Double> aspectList = new ArrayList<>();
        List<Double> widthList = new ArrayList<>();
        List<Double> heightList = new ArrayList<>();

        for (DatasetModels.ImageInfo imgInfo : images) {
            File imgFile = new File(datasetDir, imgInfo.relativePath);
            if (!imgFile.exists() || !imgFile.isFile() || imgFile.length() == 0) {
                continue;
            }

            try {
                ImageMetricsUtil.ImageStats stats = ImageMetricsUtil.extractStats(imgFile);
                brightnessList.add(stats.brightness);
                contrastList.add(stats.contrast);
                intensityList.add(stats.meanIntensity);
                rList.add(stats.redMean);
                gList.add(stats.greenMean);
                bList.add(stats.blueMean);
                aspectList.add(stats.aspectRatio);
                widthList.add((double) stats.width);
                heightList.add((double) stats.height);
            } catch (Exception e) {
                logger.warn("Skipping image {} for feature statistics: {}", imgInfo.fileName, e.getMessage());
            }
        }

        dist.totalImages = brightnessList.size();
        dist.brightnessValues = toArray(brightnessList);
        dist.contrastValues = toArray(contrastList);
        dist.meanIntensityValues = toArray(intensityList);
        dist.redMeans = toArray(rList);
        dist.greenMeans = toArray(gList);
        dist.blueMeans = toArray(bList);
        dist.aspectRatios = toArray(aspectList);
        dist.widths = toArray(widthList);
        dist.heights = toArray(heightList);

        dist.avgBrightness = average(dist.brightnessValues);
        dist.avgContrast = average(dist.contrastValues);
        dist.avgMeanIntensity = average(dist.meanIntensityValues);
        dist.avgRed = average(dist.redMeans);
        dist.avgGreen = average(dist.greenMeans);
        dist.avgBlue = average(dist.blueMeans);
        dist.avgAspectRatio = average(dist.aspectRatios);

        return dist;
    }

    private double[] toArray(List<Double> list) {
        double[] arr = new double[list.size()];
        for (int i = 0; i < list.size(); i++) {
            arr[i] = list.get(i);
        }
        return arr;
    }

    private double average(double[] arr) {
        if (arr.length == 0) return 0.0;
        double sum = 0;
        for (double v : arr) sum += v;
        return sum / arr.length;
    }
}
