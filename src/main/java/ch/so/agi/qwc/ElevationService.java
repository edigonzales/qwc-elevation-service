package ch.so.agi.qwc;

import java.awt.geom.Point2D;
import java.io.File;
import java.io.IOException;
import java.nio.file.Paths;

import org.geotools.api.coverage.PointOutsideCoverageException;
import org.geotools.api.referencing.FactoryException;
import org.geotools.api.referencing.crs.CoordinateReferenceSystem;
import org.geotools.api.referencing.operation.MathTransform;
import org.geotools.api.referencing.operation.TransformException;
import org.geotools.coverage.grid.GridCoverage2D;
import org.geotools.gce.geotiff.GeoTiffReader;
import org.geotools.geometry.jts.JTS;
import org.geotools.referencing.CRS;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.Geometry;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.Point;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class ElevationService {
    private Logger log = LoggerFactory.getLogger(this.getClass());

    private RuntimeConfig runtimeConfig;
    
    private GridCoverage2D coverage;
    private CoordinateReferenceSystem rasterCRS;
    
    private GeometryFactory geometryFactory =  new GeometryFactory();
    
    public ElevationService(RuntimeConfig runtimeConfig) {
        this.runtimeConfig = runtimeConfig;
    }
    
    public double getElevationByXY(double x, double y, String crs) throws IOException {
    
        //String elevationDataset = "/Users/stefan/Downloads/ch.so.agi.lidar_2014.dtm.tif";
        //String elevationDataset = "https://files.geo.so.ch/ch.so.agi.lidar_2014.dtm/aktuell/ch.so.agi.lidar_2014.dtm.tif)";
        
        String elevationDataset = runtimeConfig.get("elevation_dataset");
        log.debug("elevationDataset: {}", elevationDataset);

        if (coverage == null) {
            GeoTiffReader reader;
            if (elevationDataset.startsWith("http")) {
                reader = new GeoTiffReader(new File(elevationDataset));
            } else {
                reader = new GeoTiffReader(new File(elevationDataset));
            }            
            coverage = reader.read(null);
            rasterCRS = reader.getCoordinateReferenceSystem();
            log.debug("rasterCRS: {}", rasterCRS);
        }
        
        try {
            CoordinateReferenceSystem inputCRS = CRS.decode("EPSG:"+crs);
            log.debug("inputCRS: {}", inputCRS);

            Point2D.Double pos = null;
            if (!rasterCRS.getName().getCode().equalsIgnoreCase(inputCRS.getName().getCode())) {
                MathTransform mTrans = CRS.findMathTransform(inputCRS, rasterCRS);
                Point p = geometryFactory.createPoint(new Coordinate(x, y));
                Geometry transformed = JTS.transform(p, mTrans);
                pos = new Point2D.Double(transformed.getCoordinate().x, transformed.getCoordinate().y);
                log.debug("coordinate transformation: {} -> {}", p.toText(), pos.toString());
            } else {
                pos = new Point2D.Double(x, y);
            }
            try {
                double[] height = new double[1];
                coverage.evaluate(pos, height);
                return height[0];                
            } catch (PointOutsideCoverageException e) {
                return 0;
            }
        } catch (FactoryException | TransformException e) {
            throw new IOException(e);
        }
    }
    
}
