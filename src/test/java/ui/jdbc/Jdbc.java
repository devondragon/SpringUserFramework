package ui.jdbc;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;

import static ui.data.UserTestData.EMAIL;
import static ui.data.UserTestData.FIRST_NAME;

/**
 * Using for test user data from db when it storing by UI test
 */
public class Jdbc {
    private static final String DELETE_VERIFICATION_TOKEN_QUERY = "DELETE FROM verification_token WHERE user_id = (SELECT id FROM user_account WHERE first_name = ? AND email = ?)";
    private static final String DELETE_TEST_USER_ROLE = "DELETE FROM users_roles WHERE user_id = (SELECT id FROM user_account WHERE first_name = ? AND email = ?)";
    private static final String DELETE_TEST_USER_QUERY = "DELETE FROM user_account WHERE first_name = ? AND email = ?";
    public static void deleteTestUser() {
        try(Connection connection = ConnectionManager.open()) {
            execute(connection, DELETE_VERIFICATION_TOKEN_QUERY);
            execute(connection, DELETE_TEST_USER_ROLE);
            execute(connection, DELETE_TEST_USER_QUERY);
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    private static void execute(Connection connection, String query) throws SQLException {
        PreparedStatement statement = connection.prepareStatement(query);
        statement.setString(1, FIRST_NAME);
        statement.setString(2, EMAIL);
        statement.executeUpdate();
    }
}
