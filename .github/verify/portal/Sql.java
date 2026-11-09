import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;

/**
 * Verification only. Runs one SQL statement on uPortal-start's HSQLDB server and prints a query's
 * rows, tab separated, or an update's row count.
 *
 * <p>java -cp hsqldb.jar Sql.java "SELECT ..."
 */
public class Sql {
    public static void main(String[] args) throws Exception {
        try (Connection con =
                        DriverManager.getConnection(
                                "jdbc:hsqldb:hsql://localhost:8887/uPortal", "sa", "");
                Statement stmt = con.createStatement()) {
            if (stmt.execute(args[0])) {
                try (ResultSet rs = stmt.getResultSet()) {
                    final int columns = rs.getMetaData().getColumnCount();
                    while (rs.next()) {
                        final StringBuilder row = new StringBuilder();
                        for (int i = 1; i <= columns; i++) {
                            row.append(i > 1 ? "\t" : "").append(rs.getString(i));
                        }
                        System.out.println(row);
                    }
                }
            } else {
                System.out.println(stmt.getUpdateCount() + " rows changed");
            }
        }
    }
}
