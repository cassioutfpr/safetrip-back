package safetrip;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Arrays;

public class TrechosRepository {

    private final String tableName;

    public TrechosRepository() {
        tableName = System.getenv("TRECHOS_TABLE_NAME");
    }

    public ResultSet findByContainingLongAndLat(Double longg, Double lat) throws SQLException {
        String containingPoint = "POINT(" + longg + " " + lat + ")";

        return DatabaseConnectionManager.queryPreparedStatement(
            setTable("SELECT * FROM public.:table WHERE ST_CONTAINS(geom, ST_GeomFromText(?, 4326));"),
            Arrays.asList(containingPoint)
        );
    }

    public ResultSet getAll() throws SQLException {
        return DatabaseConnectionManager.query(
            setTable("SELECT * FROM public.:table;")
        );
    }

    public Double getAverageAccidents() throws SQLException {
        ResultSet rs = DatabaseConnectionManager.query(
            setTable("SELECT AVG(accidents) AS accidents_average FROM public.:table;")
        );

        rs.next();
        Double average = Double.parseDouble(
            rs.getObject("accidents_average")
                .toString()
        );
        rs.close();
        return average;
    }

    public Integer getMaxAccidents() throws SQLException {
        ResultSet rs = DatabaseConnectionManager.query(
                setTable("SELECT MAX(accidents) AS accidents_max FROM public.:table;")
        );

        rs.next();
        Integer max = Integer.parseInt(
            rs.getObject("accidents_max")
                .toString()
        );
        rs.close();
        return max;
    }

    private String setTable(String statement) {
        return statement.replace(":table", tableName);
    }
}
