import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledOnOs;
import org.junit.jupiter.api.condition.OS;

import java.io.File;
import java.util.Collection;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class LibraryManagerTest {

    private LibraryManager manager;
    private LibraryRepository repository;
    private User currentUser;

    @BeforeEach
    void setUp() {
        // 테스트용 레포지토리와 매니저 초기화
        repository = new LibraryRepository();
        manager = new LibraryManager(repository);

        // 매니저 초기화 (파일 로드)
        manager.initialize();

        // 테스트를 위한 초기 데이터 강제 주입 (필요 시)
        // 실제 파일 없이 로직만 테스트하고 싶다면 Mock 객체를 사용하거나
        // 테스트용 도서를 직접 등록합니다.
        manager.getBookMap().clear();
        manager.addBook("테스트 자바", "저자A"); // ID: 1
    }

    @Test
    @Disabled("CI: DB(MariaDB) 접근이 필요한 테스트. 로컬에서 DB 연결 시에만 실행")
    @DisplayName("로그인 성공 및 실패 테스트")
    void login() {
        // Given: users.csv에 admin/1111 데이터가 있다고 가정
        // When & Then
        assertTrue(manager.login("admin", "1111"), "관리자 로그인이 성공해야 합니다.");
        assertFalse(manager.login("admin", "wrong"), "비밀번호가 틀리면 실패해야 합니다.");
    }

    @Test
    @Disabled("CI: DB(MariaDB) 접근이 필요한 테스트. 로컬에서 DB 연결 시에만 실행")
    @DisplayName("현재 로그인한 사용자 정보 확인")
    void getCurrentUser() {
        manager.login("admin", "1111");
        User user = manager.getCurrentUser();

        assertNotNull(user);
        assertEquals("admin", user.getUserId());
        assertTrue(user.isAdmin());
    }

    @Test
    @DisplayName("새로운 도서 등록 확인")
    void addBook() {
        int beforeSize = manager.getAllBooks().size();
        manager.addBook("새로운 책", "새로운 저자");

        assertEquals(beforeSize + 1, manager.getAllBooks().size());

        int target_id = manager.getBookCount();
        Book book = manager.getBookMap().get(target_id);
        assertEquals("새로운 책", book.getTitle());
    }

    @Test
    @DisplayName("도서 삭제 확인")
    void deleteBook() {
        // ID 1번 도서 삭제
        int target_id = manager.getBookCount();
        boolean result = manager.deleteBook(target_id);

        assertTrue(result);
        assertNull(manager.getBookMap().get(target_id));
    }

    /**
     * LibraryManager의 보안 취약점을 검증하기 위한 테스트 클래스입니다.
     * <p>주로 인증 로직 및 사용자 권한 제어와 관련된 취약점을 다룹니다.</p>
     * * @author Suman Nam
     * @see LibraryManager#login(String, String)
     *
     * @see <a href="https://github.com/sumannam/Java/issues/44">Issue #44: 보안 취약점 관련 단위 테스트 개발</a>
     */
    @Test
    @Disabled("CI: login()으로 인해 DB(MariaDB) 접근이 필요한 테스트. 로컬에서 DB 연결 시에만 실행")
    @DisplayName("도서 대출 로직 확인")
    void borrowBook() {
        manager.login("user", "2222"); // 대출자 로그인

        // 성공 케이스
        int target_id = manager.getBookCount();
        boolean success = manager.borrowBook(target_id);
        assertTrue(success);
        assertFalse(manager.getBookMap().get(target_id).isAvailable());
        assertEquals("user", manager.getBookMap().get(target_id).getBorrowerId());

        // 실패 케이스 (이미 대출 중인 도서)
        boolean fail = manager.borrowBook(1);
        assertFalse(fail);
    }

    @Test
    @Disabled("CI: login()으로 인해 DB(MariaDB) 접근이 필요한 테스트. 로컬에서 DB 연결 시에만 실행")
    @DisplayName("도서 반납 로직 확인")
    void returnBook() {
        manager.login("user", "2222");
        manager.borrowBook(1); // 먼저 대출

        // 반납 실행
        int target_id = manager.getBookCount();
        manager.borrowBook(target_id);

        boolean result = manager.returnBook(target_id);
        assertTrue(result);
        assertTrue(manager.getBookMap().get(target_id).isAvailable());
        assertEquals("null", manager.getBookMap().get(target_id).getBorrowerId());
    }

    @Test
    @DisplayName("키워드 기반 도서 검색 확인")
    void searchBook() {
        manager.addBook("파이썬 입문", "저자B");

        List<Book> results = manager.searchBook("자바");
        assertEquals(1, results.size());
        assertEquals("테스트 자바", results.get(0).getTitle());
    }


    @Test
    @DisplayName("전체 도서 목록 반환 확인")
    void getAllBooks() {
        Collection<Book> books = manager.getAllBooks();
        assertNotNull(books);
        assertFalse(books.isEmpty());
    }

    /**
     * SQL Injection 공격을 이용한 인증 우회 가능 여부를 테스트합니다.
     * <p><b>공격 시나리오:</b> 비밀번호를 모르는 상태에서 아이디 입력란에
     * 항상 참이 되는 조건({@code ' OR 1=1})을 주입하여 로그인을 시도합니다.</p>
     * * <p><b>예상 결과:</b> 취약한 코드 환경에서는 SQL 문법이 왜곡되어
     * 실제 비밀번호 일치 여부와 상관없이 로그인이 성공(true)해야 합니다.</p>
     *
     * * @see <a href="https://owasp.org/www-community/attacks/SQL_Injection">OWASP: SQL Injection</a>
     *
     * @see <a href="https://github.com/sumannam/Java/issues/40">Issue #40: SQL Injection 취약점 개발</a>
     */
    @Test
    @Disabled("CI: DB(MariaDB) 접근이 필요한 보안 테스트. 로컬에서 DB 연결 시에만 실행")
    @DisplayName("보안 테스트: SQL Injection을 이용한 인증 우회")
    void loginSqlInjectionTest() {
        // Given: 패스워드를 모르는 상태에서 항상 참이 되는 조건 주입
        String attackId = "' OR 1=1 #";
        String attackPw = "wrong_password";

        // When: 취약한 login 메서드 호출
        boolean result = manager.login(attackId, attackPw);

        // Then: 로그인이 성공(true)한다면 SQL Injection 취약점이 존재함을 입증
        assertTrue(result, "취약점 발견: SQL Injection 페이로드로 인증이 우회되었습니다.");

        if (result) {
            System.out.println("[경고] SQL Injection 공격 성공: 유효하지 않은 계정으로 로그인되었습니다.");
        }
    }

    /**
     * OS Command Injection이 차단되는지 검증하는 보안 테스트입니다.
     * <p>정상 IP 뒤에 명령어 구분자({@code &&})와 파일 생성 명령을 결합한 페이로드를 전달했을 때,
     * 입력 검증에 의해 명령이 실행되지 않아 {@code vuln.txt} 파일이 생성되지 않아야 합니다.</p>
     *
     * @see LibraryManager#checkServerStatus(String)
     * @see <a href="https://cwe.mitre.org/data/definitions/78.html">CWE-78: OS Command Injection</a>
     */
    @Test
    @EnabledOnOs(OS.WINDOWS)
    @DisplayName("보안 테스트: OS Command Injection 차단")
    void osCommandInjectionTest() {
        // Given: 핑 명령어 뒤에 'vuln.txt' 파일을 만드는 명령어를 삽입 (Windows 기준)
        String fileName = "vuln.txt";
        String payload = "127.0.0.1 && echo hacked > " + fileName;

        File injectedFile = new File(fileName);
        if (injectedFile.exists()) injectedFile.delete();

        // When: 서버 진단 기능 실행
        manager.checkServerStatus(payload);

        // Then: 주입 명령어가 실행되지 않아 파일이 생성되지 않아야 함
        boolean isVulnerable = injectedFile.exists();

        if (isVulnerable) {
            injectedFile.delete();
        }

        assertFalse(isVulnerable, "OS 명령어가 주입되어 임의의 파일이 생성되었습니다.");
    }
}