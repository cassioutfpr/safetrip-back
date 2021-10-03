package safetrip;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;
import org.apache.commons.io.FileUtils;

import java.io.*;
import java.net.InetSocketAddress;
import java.net.URL;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Map;

public class SafeTripServer {
       
    public static void main(String args[]) throws SQLException, IOException {
        DatabaseConnectionManager.init();
        ResultSet rs = DatabaseConnectionManager.query( "SELECT * FROM public.trechos;" );
        GraphHopperManager.createCustomModelFromPolygonsResultSet(rs);

        System.out.println("Baixeno arkivo");
        FileUtils.copyURLToFile(
                new URL(System.getenv("OSM_FILEPATH")),
                new File(GraphHopperManager.osmFile)
        );
        System.out.println("Arkivo abaxado");
        
        startHttpServer();
             
        //DatabaseConnectionManager.queryPreparedStatement(
        //        "SELECT * FROM public.acidentes, public.trechos WHERE ST_INTERSECTS(trechos.geom, ST_GeomFromText(?, 4326));",
        //        Arrays.asList(routeString));
        //System.out.println("OOOPA");
    }
    
    
    public static void startHttpServer() throws IOException {
        int port = Integer.parseInt(System.getenv("PORT"));
        HttpServer server = HttpServer.create(new InetSocketAddress(port), 0);
        server.createContext("/route", new AccessResourceIntent());
        server.setExecutor(null); // creates a default executor
        server.start();
    }
    
    static class AccessResourceIntent implements HttpHandler {        
        @Override
        public void handle(HttpExchange t) throws IOException {
            
            t.getResponseHeaders().add("Access-Control-Allow-Origin", "*");
            t.getResponseHeaders().add("Access-Control-Allow-Methods", "GET, OPTIONS, POST");
            t.getResponseHeaders().add("Access-Control-Allow-Headers", "Content-Type,Authorization");

            System.out.println("Receiving request");
            if (t.getRequestMethod().equalsIgnoreCase("OPTIONS")) {
                System.out.println("Options");
                t.getResponseHeaders().add("Access-Control-Allow-Methods", "GET, OPTIONS, POST");
                t.getResponseHeaders().add("Access-Control-Allow-Headers", "Content-Type,Authorization");
                t.sendResponseHeaders(204, -1);
                return;
            }
            
            if (t.getRequestMethod().equals("POST")){
                System.out.println("POST route");
                InputStream requestBody = t.getRequestBody();
                ObjectMapper mapper = new ObjectMapper();
                Map<String, Double> jsonMap = mapper.readValue(requestBody, Map.class);

                String response = "";
                double initLat = jsonMap.get("initLat");
                double initLng = jsonMap.get("initLng");
                double destLat = jsonMap.get("destLat");
                double destLng = jsonMap.get("destLng");
                response = GraphHopperManager.getFastestRoute(initLat, initLng, destLat, destLng);
                response += "|";
                response += GraphHopperManager.customizableRouting(initLat, initLng, destLat, destLng);
                //response = "23.543543,23.432432;12.432423;43.43242";
                
                //GraphHopperManager.customizableRouting( initLat, initLng,
                //        destLat, destLng);
                

                t.sendResponseHeaders(200, response.length());
                OutputStream os = t.getResponseBody();
                os.write(response.getBytes());
                os.close();
                return;
            }

            if (t.getRequestMethod().equals("GET")){
                System.out.println("GET route");
                String response = "TO VIVO";
                t.sendResponseHeaders(200, response.length());
                OutputStream os = t.getResponseBody();
                os.write(response.getBytes());
                os.close();
            }
        }
    }
}
