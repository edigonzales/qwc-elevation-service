package ch.so.agi.qwc;

import java.awt.geom.Point2D;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.IntStream;

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

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import it.geosolutions.imageio.core.BasicAuthURI;
import it.geosolutions.imageio.plugins.cog.CogImageReadParam;
import it.geosolutions.imageioimpl.plugins.cog.CogImageInputStreamSpi;
import it.geosolutions.imageioimpl.plugins.cog.CogImageReaderSpi;
import it.geosolutions.imageioimpl.plugins.cog.CogSourceSPIProvider;
import it.geosolutions.imageioimpl.plugins.cog.HttpRangeReader;
import jakarta.annotation.PostConstruct;

@Service
public class ElevationService {
    private Logger log = LoggerFactory.getLogger(this.getClass());

    private ObjectMapper objectMapper;
    private RuntimeConfig runtimeConfig;
    
    private GridCoverage2D coverage;
    private CoordinateReferenceSystem rasterCRS;
    
    private GeometryFactory geometryFactory =  new GeometryFactory();
    
    public ElevationService(ObjectMapper objectMapper, RuntimeConfig runtimeConfig) {
        this.objectMapper = objectMapper;
        this.runtimeConfig = runtimeConfig;
    }
    
    @PostConstruct
    private void init() throws IOException {
        String elevationDataset = runtimeConfig.get("elevation_dataset");
        log.debug("elevationDataset: {}", elevationDataset);

        GeoTiffReader reader;
        if (elevationDataset.startsWith("http")) {
            BasicAuthURI cogUri = new BasicAuthURI(elevationDataset, false);
            HttpRangeReader rangeReader = new HttpRangeReader(cogUri.getUri(), CogImageReadParam.DEFAULT_HEADER_LENGTH);
            
            CogSourceSPIProvider input = new CogSourceSPIProvider(
                    cogUri,
                    new CogImageReaderSpi(),
                    new CogImageInputStreamSpi(),
                    rangeReader.getClass().getName());

            reader = new GeoTiffReader(input);
            log.info("using local geotiff file");
        } else {
            reader = new GeoTiffReader(new File(elevationDataset));
            log.info("using remote geotiff file");
        }            
        coverage = reader.read(null);
        rasterCRS = reader.getCoordinateReferenceSystem();
        log.debug("rasterCRS: {}", rasterCRS);
    }
    
    public List<Double> getElevationsByLinestring(String requestData) throws IOException {
        // TODO CRS wird ignoriert
        
        Map<String, Object> query = parseJsonString(requestData);

        List<List<Double>> coordinates = (List<List<Double>>) query.get("coordinates");
        List<Double> distances = (List<Double>) query.get("distances");
        List<Double> elevations = new ArrayList<>();
        int numSamples = (int) query.get("samples");
        
        // Compute cumulative distances
        List<Double> cumDistances = new ArrayList<>();
        cumDistances.add(0.0);
        IntStream.range(0, distances.size()).forEach(i -> cumDistances.add(cumDistances.get(i) + distances.get(i)));
        double totDistance = distances.stream().mapToDouble(Double::doubleValue).sum();

        // Initialize tracking variables
        double x = 0;
        int i = 0;
        double[] p1 = {coordinates.get(i).get(0), coordinates.get(i).get(1)};
        double[] p2 = {coordinates.get(i + 1).get(0), coordinates.get(i + 1).get(1)};
        double[] dr = {p2[0] - p1[0], p2[1] - p1[1]};

        for (int s = 0; s < numSamples; s++) {
            // Find correct segment for current x
            while (i + 2 < cumDistances.size() && x > cumDistances.get(i + 1)) {
                i++;
                p1 = new double[]{coordinates.get(i).get(0), coordinates.get(i).get(1)};
                p2 = new double[]{coordinates.get(i + 1).get(0), coordinates.get(i + 1).get(1)};
                dr = new double[]{p2[0] - p1[0], p2[1] - p1[1]};
            }

            // Compute interpolation fraction
            double mu = 0;
            try {
                mu = (x - cumDistances.get(i)) / (cumDistances.get(i + 1) - cumDistances.get(i));
            } catch (ArithmeticException e) {
                mu = 0;
            }

            // Transform interpolated point
            double interpX = p1[0] + mu * dr[0];
            double interpY = p1[1] + mu * dr[1];

            try {
                Point2D.Double pos = new Point2D.Double(interpX, interpY);
                double[] height = new double[1];
                coverage.evaluate(pos, height);
                elevations.add(height[0]);
            } catch (PointOutsideCoverageException e) {
                elevations.add(0.0);
            }
                      
            x += totDistance / (numSamples - 1);
        }
        
        return elevations;
    }
    
    public double getElevationByXY(double x, double y, String crs) throws IOException {                
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
    
    private Map<String, Object> parseJsonString(String jsonString) throws JsonMappingException, JsonProcessingException {
        JsonNode rootNode = objectMapper.readTree(jsonString);

        // Extract coordinates
        List<List<Double>> coordinates = new ArrayList<>();
        JsonNode coordinatesArray = rootNode.get("coordinates");
        if (coordinatesArray != null && coordinatesArray.isArray()) {
            for (JsonNode point : coordinatesArray) {
                if (point.isArray() && point.size() == 2) {
                    coordinates.add(Arrays.asList(point.get(0).asDouble(), point.get(1).asDouble()));
                }
            }
        }

        // Extract distances
        List<Double> distances = new ArrayList<>();
        JsonNode distancesArray = rootNode.get("distances");
        if (distancesArray != null && distancesArray.isArray()) {
            for (JsonNode distance : distancesArray) {
                distances.add(distance.asDouble());
            }
        }

        // Extract other fields
        String projection = rootNode.has("projection") ? rootNode.get("projection").asText() : "";
        int samples = rootNode.has("samples") ? rootNode.get("samples").asInt() : 0;

        // Store in a map
        Map<String, Object> query = new HashMap<>();
        query.put("coordinates", coordinates);
        query.put("distances", distances);
        query.put("projection", projection);
        query.put("samples", samples);

        return query;
    }
}
