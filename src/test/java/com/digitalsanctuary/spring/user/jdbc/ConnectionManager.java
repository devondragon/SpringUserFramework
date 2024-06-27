package com.digitalsanctuary.spring.user.jdbc;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;

public class ConnectionManager {

    private static final String driver = "org.mariadb.jdbc.Driver";

    private static final String url = "jdbc:mariadb://127.0.0.1:3306/springuser";

    private static final String username = "springuser";

    private static final String password = "springuser";

    static {
        initDriver();
    }

    private static void initDriver() {
        try {
            Class.forName(driver);
        } catch (ClassNotFoundException e) {
            throw new RuntimeException(e);
        }
    }

    public static Connection open() {
        try {
            return DriverManager.getConnection(url, username, password);
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

}
