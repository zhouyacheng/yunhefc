package com.yunhefc.common.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.dao.DataAccessException;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.core.io.support.ResourcePatternResolver;
import org.springframework.jdbc.core.JdbcTemplate;

import java.io.IOException;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 生产启动自检：数据库 Flyway 版本落后于代码内置迁移时立即失败。
 * 只拦「库落后」，库超前（回滚旧镜像）放行。
 * 版本解析仅支持 {@code V<整数>__} 命名；引入点分版本时需同步改造本类。
 */
public class SchemaVersionGuard implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(SchemaVersionGuard.class);
    private static final String HISTORY_SQL =
            "SELECT version FROM flyway_schema_history WHERE success = 1";
    private static final String MIGRATION_LOCATION = "classpath*:db/migration/V*__*.sql";
    private static final Pattern MIGRATION_NAME = Pattern.compile("^V(\\d+)__.*\\.sql$");

    private final JdbcTemplate jdbc;
    private final ResourcePatternResolver resolver;

    public SchemaVersionGuard(JdbcTemplate jdbc) {
        this(jdbc, new PathMatchingResourcePatternResolver());
    }

    SchemaVersionGuard(JdbcTemplate jdbc, ResourcePatternResolver resolver) {
        this.jdbc = jdbc;
        this.resolver = resolver;
    }

    @Override
    public void run(ApplicationArguments args) {
        validate();
    }

    void validate() {
        int required = highestBundledVersion();
        int applied = highestAppliedVersion();
        if (applied < required) {
            throw new IllegalStateException("生产数据库 schema 版本落后：已应用 V" + applied
                    + "，代码要求 V" + required + "；请先执行 flyway migrate 再启动");
        }
        log.info("schema 版本自检通过: applied=V{}, required=V{}", applied, required);
    }

    int highestBundledVersion() {
        Resource[] resources;
        try {
            resources = resolver.getResources(MIGRATION_LOCATION);
        } catch (IOException ex) {
            throw new IllegalStateException("无法读取内置迁移脚本", ex);
        }
        int highest = -1;
        for (Resource resource : resources) {
            Matcher matcher = MIGRATION_NAME.matcher(String.valueOf(resource.getFilename()));
            if (matcher.matches()) {
                highest = Math.max(highest, Integer.parseInt(matcher.group(1)));
            } else {
                log.warn("忽略无法解析版本的迁移脚本: {}", resource.getFilename());
            }
        }
        if (highest < 0) {
            throw new IllegalStateException("未找到内置 Flyway 迁移脚本");
        }
        return highest;
    }

    int highestAppliedVersion() {
        List<String> versions;
        try {
            versions = jdbc.queryForList(HISTORY_SQL, String.class);
        } catch (DataAccessException ex) {
            throw new IllegalStateException("无法读取 flyway_schema_history；请先执行 flyway migrate", ex);
        }
        int highest = -1;
        for (String version : versions) {
            if (version == null) {
                continue;
            }
            try {
                highest = Math.max(highest, Integer.parseInt(version.trim()));
            } catch (NumberFormatException ex) {
                log.warn("忽略非数字 Flyway 版本: {}", version);
            }
        }
        if (highest < 0) {
            throw new IllegalStateException("生产数据库没有成功的 Flyway 迁移记录；请先执行 flyway migrate");
        }
        return highest;
    }
}
