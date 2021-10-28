package safetrip;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.*;
import org.apache.commons.io.FileUtils;
import java.util.concurrent.Executors;
import java.util.concurrent.ExecutorService;

import javax.net.ssl.*;
import java.io.*;
import java.net.InetSocketAddress;
import java.net.URL;
import java.security.KeyStore;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

public class SafeTripServer {

    private static TrechosRepository trechosRepository = new TrechosRepository();

    public static void main(String args[]) throws SQLException, IOException {
        DatabaseConnectionManager.init();
        Double averageAccidents = trechosRepository.getAverageAccidents();
        Integer maxAccidents = trechosRepository.getMaxAccidents();
        ResultSet rs = new TrechosRepository().getAll();
        GraphHopperManager.createCustomModelFromPolygonsResultSet(rs, averageAccidents, maxAccidents);

        System.out.println("Baixeno arkivo");
        FileUtils.copyURLToFile(
                new URL(System.getenv("OSM_FILEPATH")),
                new File(GraphHopperManager.osmFile)
        );
        System.out.println("Arkivo abaxado");
        
        startHttpServer();
        //startHttpsServer();
             
        //DatabaseConnectionManager.queryPreparedStatement(
        //        "SELECT * FROM public.acidentes, public.trechos WHERE ST_INTERSECTS(trechos.geom, ST_GeomFromText(?, 4326));",
        //        Arrays.asList(routeString));
        //System.out.println("OOOPA");
    }
    
    
    public static void startHttpServer() throws IOException {
        int port = Integer.parseInt(System.getenv("HTTP_PORT"));
        ExecutorService executor = Executors.newFixedThreadPool(4);
        HttpServer server = HttpServer.create(new InetSocketAddress(port), 0);
        server.createContext("/route-fast", new AccessResourceIntent());
        server.createContext("/route-safe", new AccessResourceIntent2());
        server.setExecutor(executor); // creates a default executor
        server.start();
    }

    public static void startHttpsServer() {
        try {
            // setup the socket address
            InetSocketAddress address = new InetSocketAddress(Integer.parseInt(System.getenv("HTTPS_PORT")));

            // initialise the HTTPS server
            HttpsServer httpsServer = HttpsServer.create(address, 0);
            SSLContext sslContext = SSLContext.getInstance("TLS");

            // initialise the keystore
            char[] password = System.getenv("CERTIFICATE_PASSWORD").toCharArray();
            KeyStore ks = KeyStore.getInstance("JKS");
            FileInputStream fis = new FileInputStream("testkey.jks");
            ks.load(fis, password);

            // setup the key manager factory
            KeyManagerFactory kmf = KeyManagerFactory.getInstance("SunX509");
            kmf.init(ks, password);

            // setup the trust manager factory
            TrustManagerFactory tmf = TrustManagerFactory.getInstance("SunX509");
            tmf.init(ks);

            // setup the HTTPS context and parameters
            sslContext.init(kmf.getKeyManagers(), tmf.getTrustManagers(), null);
            httpsServer.setHttpsConfigurator(new HttpsConfigurator(sslContext) {
                public void configure(HttpsParameters params) {
                    try {
                        // initialise the SSL context
                        SSLContext context = getSSLContext();
                        SSLEngine engine = context.createSSLEngine();
                        params.setNeedClientAuth(false);
                        params.setCipherSuites(engine.getEnabledCipherSuites());
                        params.setProtocols(engine.getEnabledProtocols());

                        // Set the SSL parameters
                        SSLParameters sslParameters = context.getSupportedSSLParameters();
                        params.setSSLParameters(sslParameters);

                    } catch (Exception ex) {
                        System.out.println("Failed to create HTTPS port");
                    }
                }
            });
            httpsServer.createContext("/route", new AccessResourceIntent());
            httpsServer.setExecutor(null); // creates a default executor
            httpsServer.start();

        } catch (Exception exception) {
            System.out.println("Failed to create HTTPS server on port " + 443 + " of localhost");
            exception.printStackTrace();

        }
    }
    
    static class AccessResourceIntent implements HttpHandler {        
        @Override
        public void handle(HttpExchange t) throws IOException {
            System.out.println("REQUEST FAST");
            
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
                try {
                    response = GraphHopperManager.getFastestRoute(initLat, initLng, destLat, destLng);
                } catch (SQLException ex) {
                    Logger.getLogger(SafeTripServer.class.getName()).log(Level.SEVERE, null, ex);
                    ex.printStackTrace();
                }
                System.out.println("rwesponse1");
                

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
    
    
        static class AccessResourceIntent2 implements HttpHandler {        
        @Override
        public void handle(HttpExchange t) throws IOException {
            System.out.println("REQUEST SAFE");
            
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
                try {
                    response += GraphHopperManager.customizableRouting(initLat, initLng, destLat, destLng);
                } catch (Exception ex) {
                    Logger.getLogger(SafeTripServer.class.getName()).log(Level.SEVERE, null, ex);
                    ex.printStackTrace();
                }
                System.out.println("rwesponse2");
                

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
