package ui.jdbc;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;

public class ConnectionManager {

    static {
        initDriver();
    }

    private static void initDriver() {
        try {
            Class.forName("org.mariadb.jdbc.Driver");
        } catch (ClassNotFoundException e) {
            throw new RuntimeException(e);
        }
    }

    public static Connection open() {
        try {
            return DriverManager.getConnection("jdbc:mariadb://127.0.0.1:3306/springuser", "springuser", "springuser");
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

}
