package com.citylibrary.pll;

import io.zonky.test.db.postgres.embedded.EmbeddedPostgres;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import com.citylibrary.pll.support.TimeService;

import java.io.IOException;

/**
 * 集成测试基类：zonky 嵌入式 PostgreSQL（真实数据库，含 Flyway 迁移与校验）。
 * 全测试 JVM 复用同一个 PG 实例；每个测试方法前 clean + migrate，保证演示数据干净、用例独立。
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
public abstract class AbstractIntegrationTest {

    static final EmbeddedPostgres PG;

    static {
        try {
            PG = EmbeddedPostgres.builder().start();
            Runtime.getRuntime().addShutdownHook(new Thread(() -> {
                try {
                    PG.close();
                } catch (IOException ignored) {
                }
            }));
        } catch (IOException e) {
            throw new ExceptionInInitializerError(e);
        }
    }

    @DynamicPropertySource
    static void props(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url",
                () -> PG.getJdbcUrl("postgres", "postgres"));
        registry.add("spring.datasource.username", () -> "postgres");
        registry.add("spring.datasource.password", () -> "postgres");
        registry.add("spring.flyway.clean-disabled", () -> "false");
    }

    @Autowired
    private Flyway flyway;

    @Autowired
    private TimeService timeService;

    @BeforeEach
    void resetDatabase() {
        flyway.clean();
        flyway.migrate();
        timeService.reset();
    }

    @AfterEach
    void resetClock() {
        timeService.reset();
    }

    /** 将业务时钟固定到指定日期 10:00（Asia/Shanghai）。 */
    protected void fixClock(java.time.LocalDate date) {
        timeService.setClock(java.time.Clock.fixed(
                date.atTime(10, 0).atZone(java.time.ZoneId.of("Asia/Shanghai")).toInstant(),
                java.time.ZoneId.of("Asia/Shanghai")));
    }

    protected void advanceDays(int days) {
        fixClock(timeService.today().plusDays(days));
    }
}
