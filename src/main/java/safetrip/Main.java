/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package com.ronaldinho.roadaccidents;


import com.graphhopper.GHRequest;
import com.graphhopper.GHResponse;
import com.graphhopper.GraphHopper;
import com.graphhopper.config.CHProfile;
import com.graphhopper.config.LMProfile;
import com.graphhopper.config.Profile;
import com.graphhopper.routing.util.EncodingManager;
import com.graphhopper.routing.weighting.custom.CustomProfile;
import com.graphhopper.util.CustomModel;
import com.graphhopper.util.JsonFeature;
import com.graphhopper.util.shapes.GHPoint;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.HashMap;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.Envelope;
import org.locationtech.jts.geom.Geometry;
import org.locationtech.jts.geom.GeometryFactory;
import static com.graphhopper.json.Statement.Op.LIMIT;
import static com.graphhopper.json.Statement.Op.MULTIPLY;
import static com.graphhopper.json.Statement.If;
import java.sql.PreparedStatement;
import java.util.ArrayList;
import java.util.List;
import org.locationtech.jts.io.ParseException;
import org.locationtech.jts.io.WKBReader;
import org.locationtech.jts.io.WKTReader;
import org.postgresql.util.PGobject;

public class Main {
    
    public static Connection c;
    
    public static Statement databaseConnection() {
        c = null;
        Statement stmt = null;
        
        try {
            Class.forName("org.postgresql.Driver");
            c = DriverManager
                .getConnection("jdbc:postgresql://192.168.99.100:5432/safe-trip",
                        "postgres", "dev");
        }   catch (ClassNotFoundException ex) {
            Logger.getLogger(Main.class.getName()).log(Level.SEVERE, null, ex);   
        } catch (SQLException ex) {
            Logger.getLogger(Main.class.getName()).log(Level.SEVERE, null, ex);
        }
        
        try {
            stmt = c.createStatement();
        } catch (SQLException ex) {
            Logger.getLogger(Main.class.getName()).log(Level.SEVERE, null, ex);
        }
        
        return stmt;
    }
    
   
    public static void main(String args[]) throws SQLException, ParseException {
        
        Statement stmt = databaseConnection();
        ResultSet rs = stmt.executeQuery( "SELECT * FROM public.trechos;" );
//        ResultSet rs = stmt.executeQuery( "SELECT * FROM public.trechos WHERE mortos > 0;" );pioe
        
        CustomModel model = new CustomModel();
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
           
        //GraphHopper hopper = createGraphHopperInstance("sul-latest.osm.pbf"); 
        //        System.out.println(routeString);
//        
//        WKTReader reader = new WKTReader();
//        Geometry lineStringGeom = reader.read(routeString);
        
        String routeString = customizableRouting("sul-latest.osm.pbf", model);
        //String query = "SELECT * FROM public.acidentes WHERE ST_CONTAINS(SELECT * FROM public.acidentes, public.trechos WHERE ST_INTERSECTS(trechos.geom, ST_GeomFromText(?, 4326)), acidentes.geom);";
        String query = "SELECT * FROM public.acidentes, public.trechos WHERE ST_INTERSECTS(trechos.geom, ST_GeomFromText(?, 4326));";
        PreparedStatement pstmt = c.prepareStatement(query);
        pstmt.setString(1, routeString);
//        rs = stmt.executeQuery( "SELECT * FROM public.trechos;" );
        rs = pstmt.executeQuery();
        System.out.println("OOOPA");
 
    }
    
    
    static String customizableRouting(String ghLoc, CustomModel model) {
        GraphHopper hopper = new GraphHopper();
        hopper.setOSMFile(ghLoc);
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
                addPoint(new GHPoint(-25.445132, -49.286595)).addPoint(new GHPoint(-25.40006, -51.467750));

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
            lineString += String.valueOf(round(res.getBest().getPoints().getLng(i), 5)) + " " + String.valueOf(round(res.getBest().getPoints().getLat(i),5)) + ", ";
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
