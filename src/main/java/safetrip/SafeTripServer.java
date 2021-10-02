package safetrip;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Map;
import org.locationtech.jts.io.ParseException;

public class SafeTripServer {
       
    public static void main(String args[]) throws SQLException, ParseException, IOException {
        DatabaseConnectionManager.init();
        ResultSet rs = DatabaseConnectionManager.query( "SELECT * FROM public.trechos;" );
        GraphHopperManager.createCustomModelFromPolygonsResultSet(rs);
        
        startHttpServer();
             
        //DatabaseConnectionManager.queryPreparedStatement(
        //        "SELECT * FROM public.acidentes, public.trechos WHERE ST_INTERSECTS(trechos.geom, ST_GeomFromText(?, 4326));",
        //        Arrays.asList(routeString));
        //System.out.println("OOOPA");
    }
    
    
    public static void startHttpServer() throws IOException {
        HttpServer server = HttpServer.create(new InetSocketAddress(8081), 0);
        server.createContext("/route", new AccessResourceIntent());
        server.setExecutor(null); // creates a default executor
        server.start();
    }
    
    static class AccessResourceIntent implements HttpHandler {        
        @Override
        public void handle(HttpExchange t) throws IOException {
            
            t.getResponseHeaders().add("Access-Control-Allow-Origin", "*");

            if (t.getRequestMethod().equalsIgnoreCase("OPTIONS")) {
                t.getResponseHeaders().add("Access-Control-Allow-Methods", "GET, OPTIONS, POST");
                t.getResponseHeaders().add("Access-Control-Allow-Headers", "Content-Type,Authorization");
                t.sendResponseHeaders(204, -1);
                return;
            }
            
            if (t.getRequestMethod().equals("POST")){
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
            }
        }
    }
}
