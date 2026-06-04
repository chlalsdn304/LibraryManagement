import org.junit.jupiter.api.*;

import java.sql.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * SQL Injection 인증 우회 차단을 검증하는 보안 테스트입니다.
 * <p>실제 MariaDB 대신 H2 인메모리 DB를 사용하여 외부 DB 연결 없이 실행됩니다.
 * {@link LibraryRepository#getConnection()}을 오버라이드하여 동일한 {@code loadUser()}
 * 로직을 H2에 대해 검증합니다.</p>
 *
 * @see LibraryRepository#loadUser(String, String)
 * @see <a href="https://owasp.org/www-community/attacks/SQL_Injection">OWASP: SQL Injection</a>
 */
@DisplayName("보안 테스트: SQL Injection 인증 우회 차단 (H2)")
class SqlInjectionRegressionTest {

    private static final String H2_URL =
            "jdbc:h2:mem:authdb;DB_CLOSE_DELAY=-1;MODE=MySQL";

    static class H2Repository extends LibraryRepository {
        @Override
        protected Connection getConnection() throws SQLException {
            return DriverManager.getConnection(H2_URL, "sa", "");
        }
    }

    private LibraryRepository repository;

    @BeforeEach
    void setUp() throws SQLException {
        repository = new H2Repository();
        try (Connection conn = DriverManager.getConnection(H2_URL, "sa", "");
             Statement stmt = conn.createStatement()) {
            stmt.execute("DROP TABLE IF EXISTS users");
            stmt.execute("CREATE TABLE users (" +
                    "user_id VARCHAR(50) PRIMARY KEY, " +
                    "password VARCHAR(100), " +
                    "type VARCHAR(20))");
            stmt.execute("INSERT INTO users (user_id, password, type) " +
                    "VALUES ('admin', '1111', 'ADMIN')");
        }
    }

    @Test
    @DisplayName("정상 자격증명은 로그인에 성공한다")
    void validCredentials_login_succeeds() {
        User user = repository.loadUser("admin", "1111");

        assertNotNull(user, "정상 자격증명은 사용자 객체를 반환해야 합니다.");
        assertEquals("admin", user.getUserId());
        assertEquals("ADMIN", user.getRole());
    }

    @Test
    @DisplayName("틀린 비밀번호는 로그인에 실패한다")
    void wrongPassword_login_fails() {
        assertNull(repository.loadUser("admin", "wrong_password"),
                "비밀번호가 틀리면 null을 반환해야 합니다.");
    }

    @Test
    @DisplayName("SQL Injection 페이로드로는 인증을 우회할 수 없다")
    void sqlInjectionPayloads_areBlocked() {
        assertNull(repository.loadUser("' OR '1'='1' -- ", "anything"));
        assertNull(repository.loadUser("admin' -- ", "anything"));
        assertNull(repository.loadUser("' OR 1=1 #", "x"));

        assertNotNull(repository.loadUser("admin", "1111"));
    }
}
