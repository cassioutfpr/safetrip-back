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
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.locationtech.jts.geom.Geometry;
import org.locationtech.jts.io.WKBReader;
import org.postgresql.util.PGobject;

public class GraphHopperManager {

    private static CustomModel model;
    public static final String osmFile = "sul-latest.osm.pbf";
    private static final AcidentesRepository acidentesRepository = new AcidentesRepository();
    private static final TrechosRepository trechosRepository = new TrechosRepository();

    public static CustomModel createCustomModelFromPolygonsResultSet(
        ResultSet rs,
        Double averageAccidents,
        Integer maxAccidents
    ) throws SQLException {
        model = new CustomModel();
        Map<String, JsonFeature> maps = new HashMap<>();
        Map<String, Object> properties = new HashMap<>();
        List<Double> ids = new ArrayList<>();

        String nome = "poligono";
        int contador = 0;

        double step = (maxAccidents - averageAccidents)/50.0;

        System.out.println(averageAccidents);
        System.out.println(maxAccidents);

        while ( rs.next() ) {
            BigDecimal buff = (BigDecimal)(rs.getObject(4));
            int acidentesPolygon = buff.intValue();

            PGobject geomCol = (PGobject) rs.getObject(2);

            WKBReader wkbReader = new WKBReader();
            byte[] geom = wkbReader.hexToBytes(geomCol.getValue());

            Geometry geometry = null;
            try {
                geometry = wkbReader.read(geom);
            } catch (Exception e) {
                e.printStackTrace();
            }
            JsonFeature jsonFeature = new JsonFeature(nome + contador, "tipo", null, geometry, properties);

            maps.put(nome + contador, jsonFeature);
            double multiplier = 0.8 - (((acidentesPolygon - averageAccidents)/step) * 0.01);
            ids.add(multiplier);

            contador++;

        }
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
        ElapsedTimeService.printElapsedTime("Roteou fast");

        String response = "";
        Map<Integer, Integer> hoursMap = initHoursMap();
        Map<String, Integer> daysMap = initDaysMap();
        List<Coordinate> routeCoordinates = new ArrayList<>();
        for (int i = 0; i < routePoints; i++ ) {
            if (i%2 == 0) {
                response += String.valueOf(rsp.getBest().getPoints().getLat(i)) + "," + String.valueOf(rsp.getBest().getPoints().getLon(i)) + ";";
            }

            if (i%3 == 0) {
                routeCoordinates.add(new Coordinate(
                    rsp.getBest().getPoints().getLon(i),
                    rsp.getBest().getPoints().getLat(i)
                ));
            }
        }

        ResultSet rs = trechosRepository.findByContainingLongAndLatList(routeCoordinates);
        while ( rs.next() ) {
            String buff = (String) (rs.getObject(1));
            if (!polygonIdList.contains(buff)) {
                polygonIdList.add(buff);
            }
        }

        ResultSet rs2 = acidentesRepository.findByTrechos(polygonIdList);

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

        ElapsedTimeService.printElapsedTime("Carregou acidentes fast");

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
        req.putHint(CustomModel.KEY, model);

        rsp = hopper.route(req);
        ElapsedTimeService.printElapsedTime("Roteou safe");

        int latitudes = rsp.getBest().getPoints().size();
        Map<Integer, Integer> hoursMap = initHoursMap();
        Map<String, Integer> daysMap = initDaysMap();
        String response = "";
        List<String> polygonIdList = new ArrayList<>();
        List<Coordinate> routeCoordinates = new ArrayList<>();
        for (int i = 0; i < latitudes; i++ ) {
            if (i%2 == 0) {
                response += String.valueOf(rsp.getBest().getPoints().getLat(i)) + "," + String.valueOf(rsp.getBest().getPoints().getLon(i)) + ";";
            }

            if (i%3 == 0) {
                routeCoordinates.add(new Coordinate(
                    rsp.getBest().getPoints().getLon(i),
                    rsp.getBest().getPoints().getLat(i)
                ));
            }
        }

        ResultSet rs = trechosRepository.findByContainingLongAndLatList(routeCoordinates);
        while ( rs.next() ) {
            String buff = (String) (rs.getObject(1));
            if (!polygonIdList.contains(buff)) {
                polygonIdList.add(buff);
            }
        }
        ResultSet rs2 = acidentesRepository.findByTrechos(polygonIdList);

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
        ElapsedTimeService.printElapsedTime("Carregou acidentes safe");

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
}
