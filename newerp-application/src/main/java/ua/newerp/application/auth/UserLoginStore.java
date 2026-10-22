package ua.newerp.application.auth;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/** Users (dd_user of the old site) and their NewErp login (erp_user_login). */
public interface UserLoginStore {

    /** Empty if there is no such login or the user has no NewErp login. */
    Optional<UserLoginRecord> findByUsername(String username);

    Optional<UserLoginRecord> findById(int userId);

    void setLastLogin(int userId, LocalDateTime loggedInAt);

    /** Account version, if the login exists and the user is active on the old site; otherwise empty. */
    Optional<String> getActiveSecurityStamp(int userId);

    List<UserDto> list();

    Optional<UserDto> get(int userId);

    boolean usernameTaken(String username);

    /** Adds an employee to dd_user (without an old-site password, so they cannot log in to the old admin). */
    int createUser(NewUser user, LocalDateTime now);

    /**
     * Creates or changes a login. null in role, passwordHash or mustChangePassword means "do not change"
     * (a new login needs role and password). Always issues a new account version.
     *
     * @return the new account version
     */
    String saveLogin(int userId, String role, String passwordHash, Boolean mustChangePassword);

    /** @return false if there was no login anyway */
    boolean deleteLogin(int userId);
}
