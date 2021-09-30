/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package safetrip;

import com.graphhopper.GHRequest;
import com.graphhopper.GHResponse;
import com.graphhopper.GraphHopper;
import com.graphhopper.ResponsePath;
import com.graphhopper.config.CHProfile;
import com.graphhopper.config.LMProfile;
import com.graphhopper.config.Profile;
import static com.graphhopper.json.Statement.If;
import static com.graphhopper.json.Statement.Op.MULTIPLY;
import com.graphhopper.routing.util.EncodingManager;
import com.graphhopper.routing.weighting.custom.CustomProfile;
import com.graphhopper.util.CustomModel;
import com.graphhopper.util.Helper;
import com.graphhopper.util.Instruction;
import com.graphhopper.util.InstructionList;
import com.graphhopper.util.JsonFeature;
import com.graphhopper.util.PointList;
import com.graphhopper.util.Translation;
import com.graphhopper.util.shapes.GHPoint;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.locationtech.jts.geom.Envelope;
import org.locationtech.jts.geom.Geometry;
import org.locationtech.jts.io.ParseException;
import org.locationtech.jts.io.WKBReader;
import org.postgresql.util.PGobject;

public class GraphHopperManager {
    
    private static CustomModel model;
    private static final String osmFile = "sul-latest.osm.pbf";
    
    public static CustomModel createCustomModelFromPolygonsResultSet(ResultSet rs) throws SQLException, ParseException {         
        model = new CustomModel();
        Map<String, JsonFeature> maps = new HashMap<>();
        Map<String, Object> properties = new HashMap<>();
        List<String> ids = new ArrayList<>();
        
        String nome = "poligono";
        int contador = 0;
        String opalele = nome + contador;
        
        while ( rs.next() ) {
            //String id = (String) rs.getObject(1);
            ids.add(nome + contador);
            
            
            PGobject geomCol = (PGobject) rs.getObject(2);
            
            WKBReader wkbReader = new WKBReader();
            byte[] geom = wkbReader.hexToBytes(geomCol.getValue());
            
            Geometry geometry = wkbReader.read(geom);
            Envelope env = geometry.getEnvelopeInternal();
            JsonFeature jsonFeature = new JsonFeature(nome + contador, "tipo", null, geometry, properties);
        
            maps.put(nome + contador, jsonFeature);
            
            contador++;
        }
        
        model.setAreas(maps);
        
        contador = 0;
        for (String id : ids) {
            String ifStatement = "in_area_" + nome + contador + " == true";
            model.addToPriority(If(ifStatement, MULTIPLY, 1));
            contador++;
        }
        
        return model;
    }
    
    static GraphHopper createGraphHopperInstance() {
        GraphHopper hopper = new GraphHopper();
        hopper.setOSMFile(osmFile);
        // specify where to store graphhopper files
        hopper.setGraphHopperLocation("target/routing-graph-cache");
        hopper.setEncodingManager(EncodingManager.create("car"));

        // see docs/core/profiles.md to learn more about profiles
        hopper.setProfiles(new Profile("car").setVehicle("car").setWeighting("fastest").setTurnCosts(false));

        // this enables speed mode for the profile we called car
        hopper.getCHPreparationHandler().setCHProfiles(new CHProfile("car"));

        // now this can take minutes if it imports or a few seconds for loading of course this is dependent on the area you import
        hopper.importOrLoad();
        return hopper;
    }

    public static void getFastestRoute(double initLat, double initLng,
            double destLat, double destLng) {
            
        GraphHopper hopper = createGraphHopperInstance();
        // simple configuration of the request object
        GHRequest req = new GHRequest(initLat, initLng, destLat, destLng).
                // note that we have to specify which profile we are using even when there is only one like here
                        setProfile("car").
                // define the language for the turn instructions
                        setLocale(Locale.US);
        GHResponse rsp = hopper.route(req);

        // handle errors
        if (rsp.hasErrors())
            throw new RuntimeException(rsp.getErrors().toString());
        
        System.out.println("Passou do throw eror");

        
        int latitudes = rsp.getBest().getPoints().size();
        for (int i = 0; i < latitudes; i++ ) {
            System.out.println("Latitude " + i + ": " + String.valueOf(rsp.getBest().getPoints().getLat(i)));
            System.out.println("Longitude " + i + ": " + String.valueOf(rsp.getBest().getPoints().getLon(i)));
        }
        
        // use the best path, see the GHResponse class for more possibilities.
        ResponsePath path = rsp.getBest();

        // points, distance in meters and time in millis of the full path
        PointList pointList = path.getPoints();
        double distance = path.getDistance();
        long timeInMs = path.getTime();

        Translation tr = hopper.getTranslationMap().getWithFallBack(Locale.UK);
        InstructionList il = path.getInstructions();
        // iterate over all turn instructions
        for (Instruction instruction : il) {
            // System.out.println("distance " + instruction.getDistance() + " for instruction: " + instruction.getTurnDeion(tr));
        }
        assert il.size() == 6;
        assert Helper.round(path.getDistance(), -2) == 900;
    }
    
    static String customizableRouting(double initLat, double initLng,
            double destLat, double destLng) {
        GraphHopper hopper = new GraphHopper();
        hopper.setOSMFile(osmFile);
        hopper.setGraphHopperLocation("target/routing-custom-graph-cache");
        hopper.setEncodingManager(EncodingManager.create("car"));
        hopper.setProfiles(new CustomProfile("car_custom").setCustomModel(new CustomModel()).setVehicle("car"));

        // The hybrid mode uses the "landmark algorithm" and is up to 15x faster than the flexible mode (Dijkstra).
        // Still it is slower than the speed mode ("contraction hierarchies algorithm") ...
        hopper.getLMPreparationHandler().setLMProfiles(new LMProfile("car_custom"));
        hopper.importOrLoad();

        // ... but for the hybrid mode we can customize the route calculation even at request time:
        // 1. a request with default preferences
        GHRequest req = new GHRequest().setProfile("car_custom").
                addPoint(new GHPoint(initLat, initLng)).addPoint(new GHPoint(destLat, destLng));

        GHResponse res;
        
        // 2. now avoid primary roads and reduce maximum speed, see docs/core/profiles.md for an in-depth explanation
        // and also the blog posts https://www.graphhopper.com/?s=customizable+routing
//        CustomModel model = new CustomModel();
        req.putHint(CustomModel.KEY, model);
        
        //model = createEnvolope(-50.39154052734375, -50.167694091796875, -25.506502941775665, -25.408546892670543, model);

        res = hopper.route(req);
        
                        
        int latitudes = res.getBest().getPoints().size();
        String lineString = "LINESTRING (";
        for (int i = 0; i < latitudes; i++ ) {
            System.out.println(String.valueOf(round(res.getBest().getPoints().getLat(i), 5)) + "," + 
                    String.valueOf(round(res.getBest().getPoints().getLon(i),5)) + "|");
            if (i%1000 == 0) {
                lineString += String.valueOf(round(res.getBest().getPoints().getLon(i), 5)) + " " + String.valueOf(round(res.getBest().getPoints().getLat(i),5)) + ", ";
            }
        }
        
        lineString = lineString.substring(0, lineString.length() - 2) + ")";
        return lineString;

    }
    
    public static double round(double value, int places) {
        if (places < 0) {
            throw new IllegalArgumentException();
        }

        long factor = (long) Math.pow(10, places);
        value = value * factor;
        long tmp = Math.round(value);
        return (double) tmp / factor;
    }

    public static CustomModel createEnvolope(Geometry geom, CustomModel model) {
        
        Map<String, JsonFeature> maps = new HashMap<>();
        Map<String, Object> properties = new HashMap<>();
        
        Envelope env = geom.getEnvelopeInternal();
        
        JsonFeature jsonFeature = new JsonFeature("opa", "id1", env, geom, properties);
        
        maps.put("opa", jsonFeature);
        model.setAreas(maps);
        
        model.addToPriority(If("in_area_opa == true", MULTIPLY, 0.80));
        
        return model;
    }
    
    
}
