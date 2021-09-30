package safetrip;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.graphhopper.util.CustomModel;
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
        HttpServer server = HttpServer.create(new InetSocketAddress(8080), 0);
        server.createContext("/route", new AccessResourceIntent());
        server.setExecutor(null); // creates a default executor
        server.start();
    }
    
    static class AccessResourceIntent implements HttpHandler {        
        @Override
        public void handle(HttpExchange t) throws IOException {
            System.out.println("OPA");
            if (t.getRequestMethod().equals("POST")){
                InputStream requestBody = t.getRequestBody();
                ObjectMapper mapper = new ObjectMapper();
                Map<String, Double> jsonMap = mapper.readValue(requestBody, Map.class);

                String response = "{\"available\": ";
                double initLat = jsonMap.get("initLat");
                double initLng = jsonMap.get("initLng");
                double destLat = jsonMap.get("destLat");
                double destLng = jsonMap.get("destLng");
                
                GraphHopperManager.customizableRouting( -25.445132D,
                -49.286595D, -25.40006D, -51.467750D);

                t.sendResponseHeaders(200, response.length());
                OutputStream os = t.getResponseBody();
                os.write(response.getBytes());
                os.close();
            }
        }
    }
}
