/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
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


public class DatabaseConnectionManager {
    
    private static final String url = "jdbc:postgresql://192.168.99.100:5432/safe-trip";    
    private static final String driverName = "org.postgresql.Driver";   
    private static final String username = "postgres";   
    private static final String password = "dev";
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
                con = DriverManager.getConnection(url, username, password);
            } catch (SQLException ex) {
                System.out.println("Failed to create the database connection."); 
            }
        } catch (ClassNotFoundException ex) {
            System.out.println("Driver not found."); 
        }
        return con;
    }
    
    public static Statement createStatement() throws SQLException {
        try {
            stmt = con.createStatement();
        } catch (SQLException ex) {
            Logger.getLogger(SafeTripServer.class.getName()).log(Level.SEVERE, null, ex);
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
