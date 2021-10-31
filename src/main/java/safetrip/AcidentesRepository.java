package safetrip;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Arrays;
import java.util.List;

public class AcidentesRepository {

    private final String tableName;

    public AcidentesRepository() {
        tableName = System.getenv("ACIDENTES_TABLE_NAME");
    }

    public ResultSet findByTrechos(List<String> trechos) throws SQLException {
        String[] placeholders = new String[trechos.size()];
        Arrays.fill(placeholders, "?");

        return DatabaseConnectionManager.queryPreparedStatement(
            setTable("SELECT * FROM public.:table WHERE trecho IN (" + String.join(",", placeholders) + ");"),
            trechos
        );
    }

    private String setTable(String statement) {
        return statement.replace(":table", tableName);
    }
}
