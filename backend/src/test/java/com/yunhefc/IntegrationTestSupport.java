package com.yunhefc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import jakarta.servlet.Filter;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.RedisCallback;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.init.DatabasePopulatorUtils;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.FilterChainProxy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.csrf.CsrfFilter;
import org.springframework.security.web.csrf.CsrfTokenRepository;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.utility.DockerImageName;

import javax.sql.DataSource;
import java.nio.charset.StandardCharsets;

@SpringBootTest(properties = {"management.health.redis.enabled=false", "yunhefc.kafka.listener-enabled=false", "yunhefc.outbox.enabled=false", "yunhefc.sms.mock-enabled=true", "yunhefc.wechat.mini.enabled=true", "yunhefc.wechat.mini.mode=mock", "spring.quartz.auto-startup=false"})
@AutoConfigureMockMvc
abstract class IntegrationTestSupport {

    /**
     * 跨 JVM 复用仅在显式开启时生效（Testcontainers 只认环境变量或用户级配置）；
     * Failsafe 已默认设置该变量，IDE 直接运行则退化为“每个 JVM 一套容器”。
     */
    private static final boolean REUSE =
            "true".equalsIgnoreCase(System.getenv("TESTCONTAINERS_REUSE_ENABLE"));

    private static final MySQLContainer<?> MYSQL = new MySQLContainer<>(DockerImageName.parse("mysql:8.4"))
            .withDatabaseName("yunhefc")
            .withUsername("yunhefc")
            .withPassword("yunhefc")
            // 与 RDS/本地容器保持一致，证明会话时区由连接池固定，而非依赖服务端默认值。
            .withCommand("mysqld", "--default-time-zone=+08:00")
            .withReuse(REUSE);

    private static final GenericContainer<?> REDIS = new GenericContainer<>(DockerImageName.parse("redis:6.2.24"))
            .withExposedPorts(6379)
            .withReuse(REUSE);

    private static final ResourceDatabasePopulator RESET_DB = new ResourceDatabasePopulator(
            new ClassPathResource("db/test/reset.sql"),
            new ClassPathResource("db/migration/V3__seed_team_roles_permissions_settings.sql"),
            new ClassPathResource("db/migration/V10__remove_grouping_weight_settings.sql"),
            new ClassPathResource("db/migration/V17__seed_activity_ledger.sql"));

    static {
        RESET_DB.setSqlScriptEncoding(StandardCharsets.UTF_8.name());
        // 单例容器：整个 Failsafe JVM 共享，不再按测试类停止。
        MYSQL.start();
        REDIS.start();
    }

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", MYSQL::getJdbcUrl);
        registry.add("spring.datasource.username", MYSQL::getUsername);
        registry.add("spring.datasource.password", MYSQL::getPassword);
        registry.add("spring.data.redis.host", REDIS::getHost);
        registry.add("spring.data.redis.port", () -> REDIS.getMappedPort(6379));
        registry.add("spring.session.store-type", () -> "redis");
    }

    @Autowired
    protected DataSource dataSource;

    @Autowired
    protected MockMvc mockMvc;

    @Autowired
    protected JdbcTemplate jdbc;

    @Autowired
    protected PasswordEncoder passwordEncoder;

    @Autowired
    protected StringRedisTemplate redisTemplate;

    @Autowired
    private FilterChainProxy filterChainProxy;

    @Autowired
    private CsrfTokenRepository csrfTokenRepository;

    @BeforeEach
    void resetDatabaseAndRedis() {
        // 父类 @BeforeEach 先于子类执行；每个测试方法都从 Flyway 种子状态开始。
        DatabasePopulatorUtils.execute(RESET_DB, dataSource);
        redisTemplate.execute((RedisCallback<Void>) connection -> {
            connection.serverCommands().flushDb();
            return null;
        });
        restoreCsrfTokenRepository();
    }

    /**
     * MockMvc 的 csrf() 会反射替换共享 CsrfFilter 的仓库；上下文跨类复用后必须还原，
     * 否则后续使用真实 CSRF 流程的测试会读到测试仓库的默认请求头名。
     */
    private void restoreCsrfTokenRepository() {
        for (SecurityFilterChain chain : filterChainProxy.getFilterChains()) {
            for (Filter filter : chain.getFilters()) {
                if (filter instanceof CsrfFilter csrfFilter) {
                    ReflectionTestUtils.setField(csrfFilter, "tokenRepository", csrfTokenRepository);
                }
            }
        }
    }

    protected Cookie cookie(MvcResult result, String name) {
        Cookie cookie = result.getResponse().getCookie(name);
        assertThat(cookie).as("cookie " + name).isNotNull();
        return cookie;
    }

    protected String csrfToken(MvcResult result) throws Exception {
        return JsonPath.read(result.getResponse().getContentAsString(), "$.data.token");
    }

    /** 小程序登录结果：10 分钟一次性票据（AUTH-21 只需票据）。 */
    protected record MiniLoginTicket(String ticket) {
    }

    protected MiniLoginTicket miniLogin(String code) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/c/auth/mini/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"code\":\"" + code + "\"}"))
                .andExpect(status().isOk())
                .andReturn();
        Object ticket = JsonPath.read(result.getResponse().getContentAsString(), "$.data.bindTicket");
        return new MiniLoginTicket(ticket == null ? null : ticket.toString());
    }

    /** 真实 CSRF 握手的产物：会话 Cookie + 请求头 token。 */
    protected record CsrfSession(Cookie cookie, String token) {
    }

    /**
     * AUTH-02 的真实 CSRF 握手：GET /auth/csrf 取 token 与会话 Cookie。
     * <p>
     * 同一测试方法里不得再混用 Spring Security Test 的 {@code .with(csrf())}：它会把共享
     * CsrfFilter 的 tokenRepository 换成默认头名 X-CSRF-TOKEN 的测试仓库，直到下一个
     * @BeforeEach 才复原，随后真实握手的 X-XSRF-TOKEN 会被判无效而返回 403。
     */
    protected CsrfSession csrfSession() throws Exception {
        MvcResult csrf = mockMvc.perform(get("/api/v1/c/auth/csrf"))
                .andExpect(status().isOk())
                .andReturn();
        return new CsrfSession(cookie(csrf, "YHFC_C"), csrfToken(csrf));
    }

    /** B 端真实 CSRF 握手（登录前还没有 B 会话时使用）。 */
    protected CsrfSession bCsrfSession() throws Exception {
        MvcResult csrf = mockMvc.perform(get("/api/v1/b/auth/csrf"))
                .andExpect(status().isOk())
                .andReturn();
        return new CsrfSession(cookie(csrf, "YHFC_B"), csrfToken(csrf));
    }

    /** 取指定 C 端会话的 CSRF token；写请求的 token 必须与 Cookie 同会话。 */
    protected String cCsrfToken(Cookie cookie) throws Exception {
        return csrfToken(mockMvc.perform(get("/api/v1/c/auth/csrf").cookie(cookie))
                .andExpect(status().isOk())
                .andReturn());
    }

    /** 取指定 B 端会话的 CSRF token。 */
    protected String bCsrfToken(Cookie cookie) throws Exception {
        return csrfToken(mockMvc.perform(get("/api/v1/b/auth/csrf").cookie(cookie))
                .andExpect(status().isOk())
                .andReturn());
    }

    /** AUTH-02 仍需 CSRF；小程序端会先取 /auth/csrf，这里等价模拟。 */
    protected void sendBindSmsCode(String phone) throws Exception {
        CsrfSession csrf = csrfSession();
        mockMvc.perform(post("/api/v1/c/auth/sms-codes")
                        .cookie(csrf.cookie())
                        .header("X-XSRF-TOKEN", csrf.token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"phone":"%s","purpose":"BIND"}
                                """.formatted(phone)))
                .andExpect(status().isOk());
    }
}
