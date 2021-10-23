/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package safetrip;

import com.graphhopper.GHRequest;
import com.graphhopper.GHResponse;
import com.graphhopper.GraphHopper;
import com.graphhopper.config.CHProfile;
import com.graphhopper.config.LMProfile;
import com.graphhopper.config.Profile;
import static com.graphhopper.json.Statement.If;
import static com.graphhopper.json.Statement.Op.MULTIPLY;
import com.graphhopper.routing.util.EncodingManager;
import com.graphhopper.routing.weighting.custom.CustomProfile;
import com.graphhopper.util.CustomModel;
import com.graphhopper.util.JsonFeature;
import com.graphhopper.util.shapes.GHPoint;
import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.locationtech.jts.geom.Envelope;
import org.locationtech.jts.geom.Geometry;
import org.locationtech.jts.io.WKBReader;
import org.postgresql.util.PGobject;

public class GraphHopperManager {
    
    private static CustomModel model;
    public static final String osmFile = "sul-latest.osm.pbf";
    
    public static CustomModel createCustomModelFromPolygonsResultSet(ResultSet rs) throws SQLException {
        model = new CustomModel();
        Map<String, JsonFeature> maps = new HashMap<>();
        Map<String, Object> properties = new HashMap<>();
        List<Double> ids = new ArrayList<>();
        
        String nome = "poligono";
        int contador = 0;
        String opalele = nome + contador;
        
//        ResultSet rs1 = rs;
        
        int count = 0;
        int max = 0;
        int numberAcidents = 0;
//        while ( rs1.next() ) {
//            BigDecimal buff = (BigDecimal)(rs.getObject(4));
//            numberAcidents += buff.intValue();
//            if (numberAcidents > max) {
//                max = numberAcidents;
//            }
//            count++;
//        }
        
//        float average = numberAcidents/count;
        double average = 200;
        double step = (4763 - average)/50.0;
        
        System.out.println(average);
        
        while ( rs.next() ) {
            BigDecimal buff = (BigDecimal)(rs.getObject(4));
            int acidentesPolygon = buff.intValue();
//            ids.add(nome + contador);
            
            
            PGobject geomCol = (PGobject) rs.getObject(2);
            
            WKBReader wkbReader = new WKBReader();
            byte[] geom = wkbReader.hexToBytes(geomCol.getValue());

            Geometry geometry = null;
            try {
                geometry = wkbReader.read(geom);
            } catch (Exception e) {
                e.printStackTrace();
            }
            Envelope env = geometry.getEnvelopeInternal();
            JsonFeature jsonFeature = new JsonFeature(nome + contador, "tipo", null, geometry, properties);
        
            maps.put(nome + contador, jsonFeature);
            double multiplier = 0.8 - (((acidentesPolygon - average)/step) * 0.01);
            ids.add(multiplier);
            
            contador++;

        }
        System.out.println("floating in the most peculiar waaaay");
        
        model.setAreas(maps);
       
        
        String ifStatement = "road_class != STEPS";
        model.addToPriority(If(ifStatement, MULTIPLY, 0.8D));
        
        contador = 0;
        for (double mult : ids) {
            ifStatement = "in_area_" + nome + contador + " == true";
            int scale = (int) Math.pow(10, 1);
            double opal = (double) Math.round(mult * scale) / scale;
            if (opal > 1) {
                opal = 1.0D;
            }
            System.out.println(ifStatement + " => " + opal);
            model.addToPriority(If(ifStatement, MULTIPLY, opal));
            contador++;
        }
        
        return model;
    }
    
    public static Map<Integer, Integer> initHoursMap() {
        Map<Integer, Integer> hoursMap = new HashMap<>();
        for(int i = 0; i < 24; i++) {
            hoursMap.put(i, 0);
        }
        return hoursMap;
    }
    
    public static Map<String, Integer> initDaysMap() {
        Map<String, Integer> daysMap = new HashMap<>();
        daysMap.put("Segunda", 0);
        daysMap.put("Terça", 0);
        daysMap.put("Quarta", 0);
        daysMap.put("Quinta", 0);
        daysMap.put("Sexta", 0);
        daysMap.put("Sábado", 0);
        daysMap.put("Domingo", 0);
        
        return daysMap;
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

    public static String getFastestRoute(double initLat, double initLng,
            double destLat, double destLng) throws SQLException {
            
        GraphHopper hopper = createGraphHopperInstance();
        List<String> polygonIdList = new ArrayList<>();
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
       

        
        int routePoints = rsp.getBest().getPoints().size();
        System.out.println("Roteou 1");
        
        String response = "";
        Map<Integer, Integer> hoursMap = initHoursMap();
        Map<String, Integer> daysMap = initDaysMap();
        for (int i = 0; i < routePoints; i++ ) {
            if (i%2 == 0) {
                response += String.valueOf(rsp.getBest().getPoints().getLat(i)) + "," + String.valueOf(rsp.getBest().getPoints().getLon(i)) + ";";
            }
            
            if (i%50 == 0) {
                String wktString = "POINT(" + rsp.getBest().getPoints().getLon(i) + " " + rsp.getBest().getPoints().getLat(i) + ")";
                        
                ResultSet rs = DatabaseConnectionManager.queryPreparedStatement(
                "SELECT * FROM public.trechos WHERE ST_CONTAINS(geom, ST_GeomFromText(?, 4326));",
                Arrays.asList(wktString));
                while ( rs.next() ) {
                    String buff = (String)(rs.getObject(1));
                    if (!polygonIdList.contains(buff))  {
                        polygonIdList.add(buff);
            
                        ResultSet rs2 = DatabaseConnectionManager.queryPreparedStatement(
                        "SELECT * FROM public.acidentes WHERE trecho = ?;",
                        Arrays.asList(buff));

                        while ( rs2.next() ) {
                            String dia_semana = (String)(rs2.getObject(3));
                            BigDecimal hora = (BigDecimal)(rs2.getObject(33));
                            
                            if (hoursMap.get(hora.intValue()) != null) {
                                hoursMap.put(hora.intValue(), hoursMap.get(hora.intValue()) + 1);
                            }
                            
                            if (daysMap.get(dia_semana) != null) {
                                daysMap.put(dia_semana, daysMap.get(dia_semana) + 1);
                            }
                            
                        }
                    }
                }
            }
        }
        
        System.out.println("Respondeu 1");
        
        response = response + "|" + rsp.getBest().getTime() + "|" + rsp.getBest().getDistance();
        
        for (int i = 0; i < 24; i++) {
            response += "|" + hoursMap.get(i);
        }
        
        response += "|" + daysMap.get("Segunda") + "|" + daysMap.get("Terça")
                + "|" + daysMap.get("Quarta") + "|" + daysMap.get("Quinta")
                + "|" + daysMap.get("Sexta") + "|" + daysMap.get("Sábado")
                + "|" + daysMap.get("Domingo");
        
        return response;
        
        /*
        OUTRAS INFOS DO CAMINHO MAIS Rï¿½PIDO
        
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
        * 
        * ***/
    }
    
    static String customizableRouting(double initLat, double initLng,
            double destLat, double destLng) throws SQLException {
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

        GHResponse rsp;
        
        // 2. now avoid primary roads and reduce maximum speed, see docs/core/profiles.md for an in-depth explanation
        // and also the blog posts https://www.graphhopper.com/?s=customizable+routing
//        CustomModel model = new CustomModel();
        req.putHint(CustomModel.KEY, model);
        
        //model = createEnvolope(-50.39154052734375, -50.167694091796875, -25.506502941775665, -25.408546892670543, model);

        rsp = hopper.route(req);
        System.out.println("Roteou 2");
                        
        int latitudes = rsp.getBest().getPoints().size();
        Map<Integer, Integer> hoursMap = initHoursMap();
        Map<String, Integer> daysMap = initDaysMap();
        String response = "";
        List<String> polygonIdList = new ArrayList<>();
        for (int i = 0; i < latitudes; i++ ) {
            if (i%2 == 0) {
                response += String.valueOf(rsp.getBest().getPoints().getLat(i)) + "," + String.valueOf(rsp.getBest().getPoints().getLon(i)) + ";";
            }
            
            if (i%50 == 0) {
                String wktString = "POINT(" + rsp.getBest().getPoints().getLon(i) + " " + rsp.getBest().getPoints().getLat(i) + ")";

                
                ResultSet rs = DatabaseConnectionManager.queryPreparedStatement(
                "SELECT * FROM public.trechos WHERE ST_CONTAINS(geom, ST_GeomFromText(?, 4326));",
                Arrays.asList(wktString));
                while ( rs.next() ) {
                    String buff = (String)(rs.getObject(1));
                    if (!polygonIdList.contains(buff))  {
                        polygonIdList.add(buff);
            
                        ResultSet rs2 = DatabaseConnectionManager.queryPreparedStatement(
                        "SELECT * FROM public.acidentes WHERE trecho = ?;",
                        Arrays.asList(buff));

                        while ( rs2.next() ) {
                            String dia_semana = (String)(rs2.getObject(3));
                            BigDecimal hora = (BigDecimal)(rs2.getObject(33));
                            
                            if (hoursMap.get(hora.intValue()) != null) {
                                hoursMap.put(hora.intValue(), hoursMap.get(hora.intValue()) + 1);
                            }
                            
                            if (daysMap.get(dia_semana) != null) {
                                daysMap.put(dia_semana, daysMap.get(dia_semana) + 1);
                            }
                            
                        }
                    }
                }
            }
        }
        
        System.out.println("Respondeu 2");
        response = response + "|" + rsp.getBest().getTime() + "|" + rsp.getBest().getDistance();
        
        for (int i = 0; i < 24; i++) {
            response += "|" + hoursMap.get(i);
        }
        
        response += "|" + daysMap.get("Segunda") + "|" + daysMap.get("Terça")
                + "|" + daysMap.get("Quarta") + "|" + daysMap.get("Quinta")
                + "|" + daysMap.get("Sexta") + "|" + daysMap.get("Sábado")
                + "|" + daysMap.get("Domingo");
        
        return response;

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
