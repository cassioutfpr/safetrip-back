package safetrip;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.locationtech.jts.geom.Geometry;


public class DatabaseConnectionManager {
    
    private static final String driverName = "org.postgresql.Driver";
    private static Connection con;
    private static Statement stmt;
    
    public static void init() throws SQLException {
        createConnection();
        createStatement();
    }
    
    public static Connection createConnection() throws SQLException {
        try {
            Class.forName(driverName);
            try {
                String hostname = System.getenv("DATABASE_HOST");
                int port = Integer.parseInt(System.getenv("DATABASE_PORT"));
                String username = System.getenv("DATABASE_USERNAME");
                String password = System.getenv("DATABASE_PASSWORD");
                String url =  "jdbc:postgresql://" + hostname + ":" + port + "/postgres";
                con = DriverManager.getConnection(url, username, password);
            } catch (SQLException ex) {
                System.out.println("Failed to create the database connection."); 
            }
        } catch (ClassNotFoundException ex) {
            System.out.println(ex.getMessage());
            ex.printStackTrace();
        }
        return con;
    }
    
    public static Statement createStatement() throws SQLException {
        try {
            stmt = con.createStatement();
        } catch (SQLException ex) {
            Logger.getLogger(SafeTripServer.class.getName()).log(Level.SEVERE, null, ex);
            ex.printStackTrace();
        }
        return stmt;
    }
    
    public static ResultSet query(String queryString) throws SQLException {
        return stmt.executeQuery(queryString);
    }
    
    public static ResultSet queryPreparedStatement(String queryString,
            List<String> replaceStringList) throws SQLException {
        PreparedStatement pstmt = con.prepareStatement(queryString);
        
        int stringIndex = 1;
        for (String strignToReplace : replaceStringList) {
            pstmt.setString(stringIndex, strignToReplace);    
            stringIndex++;
        }
        
        return pstmt.executeQuery();
    }
   
}
