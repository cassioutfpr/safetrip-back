package safetrip;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

public class TrechosRepository {

    private final String tableName;

    public TrechosRepository() {
        tableName = System.getenv("TRECHOS_TABLE_NAME");
    }

    public ResultSet findByContainingLongAndLatList(List<Coordinate> coordinatesList) throws SQLException {
        List<String> pointsList = coordinatesList.stream()
            .map(coordinates -> "POINT(" + coordinates.longitude + " " + coordinates.latitude + ")")
            .collect(Collectors.toList());
        String baseQuery = setTable("SELECT * FROM public.:table WHERE ST_CONTAINS(geom, ST_GeomFromText(?, 4326))");
        String[] queries = new String[pointsList.size()];
        Arrays.fill(queries, baseQuery);

        return DatabaseConnectionManager.queryPreparedStatement(
            String.join(" UNION ", queries) + ";",
            pointsList
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
