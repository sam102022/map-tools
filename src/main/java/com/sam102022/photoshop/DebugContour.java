package com.sam102022.photoshop;

import com.sam102022.photoshop.core.detection.GreenMaskExtractor;
import com.sam102022.photoshop.core.detection.RoadDetector;
import com.sam102022.photoshop.core.geometry.ContourExtractor;
import com.sam102022.photoshop.core.geometry.ContourSimplifier;
import com.sam102022.photoshop.core.geometry.PolygonBuilder;
import com.sam102022.photoshop.core.geometry.RoadSnapper;
import com.sam102022.photoshop.core.model.BinaryMask;
import com.sam102022.photoshop.core.model.CoverageMask;
import com.sam102022.photoshop.core.model.SnappingConfig;
import com.sam102022.photoshop.io.ImageExporter;
import com.sam102022.photoshop.io.ImageLoader;

import java.awt.Point;
import java.awt.image.BufferedImage;
import java.nio.file.Path;
import java.util.List;

public class DebugContour {
    public static void main(String[] args) throws Exception {
        BufferedImage mapImg = ImageLoader.load(Path.of("src/main/resources/sample/a découper.jpg"));
        BufferedImage maskImg = ImageLoader.load(Path.of("src/main/resources/sample/limites.jpg"));
        GreenMaskExtractor ext = new GreenMaskExtractor();
        BinaryMask roughMask = ext.extract(maskImg);

        SnappingConfig config = SnappingConfig.defaults();
        RoadDetector rd = new RoadDetector();
        BinaryMask roads = rd.detectRoads(mapImg, config);

        ContourExtractor ce = new ContourExtractor();
        List<Point> c = ce.extractLargestContour(roughMask);

        ContourSimplifier cs = new ContourSimplifier();
        List<Point> s = cs.simplify(c, 1.0);

        RoadSnapper rs = new RoadSnapper();
        List<Point> adjusted = rs.snap(s, roads, roughMask.getWidth(), roughMask.getHeight(), config);

        PolygonBuilder pb = new PolygonBuilder();
        CoverageMask cov = pb.rasterizeCoverage(roughMask.getWidth(), roughMask.getHeight(), adjusted, true);

        BufferedImage clippedNoCore = ImageExporter.createClippedImage(mapImg, cov, 0);
        ImageExporter.savePng(clippedNoCore, Path.of("debug_no_core_smooth0.png"));

        BufferedImage clippedNoCoreSmooth1 = ImageExporter.createClippedImage(mapImg, cov, 1);
        ImageExporter.savePng(clippedNoCoreSmooth1, Path.of("debug_no_core_smooth1.png"));

        System.out.println("Exported debug images.");
    }
}
