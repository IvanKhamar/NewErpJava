package ua.newerp.infrastructure.auth;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.text.Collator;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Repository;

import ua.newerp.application.auth.NewUser;
import ua.newerp.application.auth.UserDto;
import ua.newerp.application.auth.UserLoginRecord;
import ua.newerp.application.auth.UserLoginStore;

@Repository
class JdbcUserLoginStore implements UserLoginStore {

    private static final String RECORD_SQL = """
            SELECT u.user_id, u.username, CONCAT(u.firstname, ' ', u.lastname) AS full_name, u.status,
                   l.password_hash, l.role, l.security_stamp, l.must_change_password
            FROM dd_user u JOIN erp_user_login l ON l.user_id = u.user_id
            """;

    private static final String USER_SQL = """
            SELECT u.user_id, u.username, CONCAT(u.firstname, ' ', u.lastname) AS full_name, u.email, u.status,
                   l.user_id AS login_user_id, l.role, l.last_login_at
            FROM dd_user u LEFT JOIN erp_user_login l ON l.user_id = u.user_id
            """;

    private final JdbcClient jdbc;

    JdbcUserLoginStore(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public Optional<UserLoginRecord> findByUsername(String username) {
        return jdbc.sql(RECORD_SQL + " WHERE u.username = :username").param("username", username)
                .query(JdbcUserLoginStore::record).optional();
    }

    @Override
    public Optional<UserLoginRecord> findById(int userId) {
        return jdbc.sql(RECORD_SQL + " WHERE u.user_id = :id").param("id", userId)
                .query(JdbcUserLoginStore::record).optional();
    }

    @Override
    public void setLastLogin(int userId, LocalDateTime loggedInAt) {
        jdbc.sql("UPDATE erp_user_login SET last_login_at = :at WHERE user_id = :id")
                .param("at", loggedInAt).param("id", userId).update();
    }

    @Override
    public Optional<String> getActiveSecurityStamp(int userId) {
        return jdbc.sql("""
                SELECT l.security_stamp FROM dd_user u JOIN erp_user_login l ON l.user_id = u.user_id
                WHERE u.user_id = :id AND u.status = 1
                """).param("id", userId).query(String.class).optional();
    }

    /** Those with NewErp access first, then by name. */
    @Override
    public List<UserDto> list() {
        Collator ukrainian = Collator.getInstance(Locale.of("uk", "UA"));
        return jdbc.sql(USER_SQL).query(JdbcUserLoginStore::user).list().stream()
                .sorted(Comparator.comparing(UserDto::hasAccess).reversed().thenComparing(UserDto::fullName, ukrainian))
                .toList();
    }

    @Override
    public Optional<UserDto> get(int userId) {
        return jdbc.sql(USER_SQL + " WHERE u.user_id = :id").param("id", userId).query(JdbcUserLoginStore::user).optional();
    }

    @Override
    public boolean usernameTaken(String username) {
        return jdbc.sql("SELECT COUNT(*) FROM dd_user WHERE username = :username").param("username", username)
                .query(Integer.class).single() > 0;
    }

    @Override
    public int createUser(NewUser user, LocalDateTime now) {
        // Empty password and salt: such a hash matches no password on the old site, so the person cannot log in
        // to the old admin. user_group_id = 0: no rights in the old admin.
        KeyHolder key = new GeneratedKeyHolder();
        jdbc.sql("""
                INSERT INTO dd_user (user_group_id, username, password, salt, firstname, lastname, email, image, code, ip,
                                     status, date_added)
                VALUES (0, :username, '', '', :firstName, :lastName, :email, '', '', '', 1, :now)
                """)
                .param("username", user.username())
                .param("firstName", user.firstName())
                .param("lastName", user.lastName())
                .param("email", user.email())
                .param("now", now)
                .update(key);
        return key.getKey().intValue();
    }

    @Override
    public String saveLogin(int userId, String role, String passwordHash, Boolean mustChangePassword) {
        String stamp = UUID.randomUUID().toString().replace("-", ""); // like Guid.ToString("N")
        boolean exists = jdbc.sql("SELECT COUNT(*) FROM erp_user_login WHERE user_id = :id").param("id", userId)
                .query(Integer.class).single() > 0;
        if (!exists) {
            if (role == null || passwordHash == null) {
                throw new IllegalStateException("Для нового входу потрібні роль і пароль.");
            }
            jdbc.sql("""
                    INSERT INTO erp_user_login (user_id, password_hash, role, security_stamp, must_change_password)
                    VALUES (:id, :hash, :role, :stamp, :must)
                    """)
                    .param("id", userId).param("hash", passwordHash).param("role", role).param("stamp", stamp)
                    .param("must", Boolean.TRUE.equals(mustChangePassword))
                    .update();
        } else {
            // COALESCE keeps the old value where the argument is null ("do not change")
            jdbc.sql("""
                    UPDATE erp_user_login
                    SET role = COALESCE(:role, role), password_hash = COALESCE(:hash, password_hash),
                        must_change_password = COALESCE(:must, must_change_password), security_stamp = :stamp
                    WHERE user_id = :id
                    """)
                    .param("id", userId).param("hash", passwordHash).param("role", role).param("stamp", stamp)
                    .param("must", mustChangePassword)
                    .update();
        }
        return stamp;
    }

    @Override
    public boolean deleteLogin(int userId) {
        return jdbc.sql("DELETE FROM erp_user_login WHERE user_id = :id").param("id", userId).update() > 0;
    }

    private static UserLoginRecord record(ResultSet rs, int rowNum) throws SQLException {
        return new UserLoginRecord(rs.getInt("user_id"), rs.getString("username"), rs.getString("full_name"),
                rs.getBoolean("status"), rs.getString("password_hash"), rs.getString("role"),
                rs.getString("security_stamp"), rs.getBoolean("must_change_password"));
    }

    private static UserDto user(ResultSet rs, int rowNum) throws SQLException {
        Timestamp lastLogin = rs.getTimestamp("last_login_at");
        return new UserDto(rs.getInt("user_id"), rs.getString("username"), rs.getString("full_name"),
                rs.getString("email"), rs.getBoolean("status"), rs.getObject("login_user_id") != null,
                rs.getString("role"), lastLogin == null ? null : lastLogin.toLocalDateTime());
    }
}
