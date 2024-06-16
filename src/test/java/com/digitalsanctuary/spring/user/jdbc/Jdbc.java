package com.digitalsanctuary.spring.user.jdbc;

import com.digitalsanctuary.spring.user.dto.UserDto;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

import static com.digitalsanctuary.spring.user.ui.data.UiTestData.TEST_USER_ENCODED_PASSWORD;

/**
 * Using for delete/save user test data
 */
public class Jdbc {
    private static final String DELETE_VERIFICATION_TOKEN_QUERY = "DELETE FROM verification_token WHERE user_id = (SELECT id FROM user_account WHERE email = ?)";
    private static final String DELETE_TEST_USER_ROLE = "DELETE FROM users_roles WHERE user_id = (SELECT id FROM user_account WHERE email = ?)";
    private static final String DELETE_TEST_PASSWORD_RESET_TOKEN = "DELETE FROM password_reset_token WHERE user_id = (SELECT id FROM user_account WHERE email = ?)";
    private static final String DELETE_TEST_USER_QUERY = "DELETE FROM user_account WHERE email = ?";
    private static final String GET_LAST_USER_ID_QUERY = "SELECT max(id) FROM user_account";
    private static final String GET_PASSWORD_RESET_TOKEN_BY_USER_EMAIL_QUERY = "SELECT token FROM password_reset_token WHERE user_id = (SELECT id FROM user_account WHERE email = ?)";

    private static final String SAVE_TEST_USER_QUERY = "INSERT INTO user_account (id, first_name, last_name, email, " +
            "password, enabled, failed_login_attempts, locked) VALUES (?,?,?,?,?,?,?,?)";

    public static void deleteTestUser(UserDto userDto) {
        try(Connection connection = ConnectionManager.open()) {
            String[] params = new String[]{userDto.getEmail()};
            execute(connection, DELETE_VERIFICATION_TOKEN_QUERY, params);
            execute(connection, DELETE_TEST_USER_ROLE, params);
            execute(connection, DELETE_TEST_PASSWORD_RESET_TOKEN, params);
            execute(connection, DELETE_TEST_USER_QUERY, params);
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }


    public static void saveTestUser(UserDto userDto) {
        try(Connection connection = ConnectionManager.open()) {
            ResultSet resultSet = connection.prepareStatement(GET_LAST_USER_ID_QUERY).executeQuery();
            int id = 0;
            if (resultSet.next()) {
                id = (resultSet.getInt(1) + 1);
            }
            Object[] params = new Object[]{id, userDto.getFirstName(), userDto.getLastName(),
                    userDto.getEmail(), TEST_USER_ENCODED_PASSWORD, true, 0, false};
            execute(connection, SAVE_TEST_USER_QUERY, params);
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    private static void execute(Connection connection, String query, Object[] params) throws SQLException {
        PreparedStatement statement = connection.prepareStatement(query);
        for(int i = 0; i < params.length; i ++) {
            Object param = params[i];
            if (param instanceof Integer) {
                statement.setInt((i + 1), (Integer) param);
            }
            if (param instanceof String) {
                statement.setString((i + 1), (String) param);
            }
            if (param instanceof Boolean) {
                statement.setBoolean((i + 1), (Boolean) param);
            }
        }
        statement.executeUpdate();
    }

    public static String getPasswordRestTokenByUserEmail(String email) {
        String token = "";
        try (Connection connection = ConnectionManager.open()) {
            PreparedStatement statement = connection.prepareStatement(GET_PASSWORD_RESET_TOKEN_BY_USER_EMAIL_QUERY);
            statement.setString(1, email);
            ResultSet set = statement.executeQuery();
            if (set.next()) {
                token = set.getString(1);
            }
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
        return token;
    }
}
