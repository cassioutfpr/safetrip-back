package safetrip;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Arrays;

public class AcidentesRepository {

    private final String tableName;

    public AcidentesRepository() {
        tableName = System.getenv("ACIDENTES_TABLE_NAME");
    }

    public ResultSet findByTrecho(String trecho) throws SQLException {
        return DatabaseConnectionManager.queryPreparedStatement(
            setTable("SELECT * FROM public.:table WHERE trecho = ?;"),
            Arrays.asList(trecho)
        );
    }

    private String setTable(String statement) {
        return statement.replace(":table", tableName);
    }
}
