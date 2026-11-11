package com.yunhefc;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

import java.util.Arrays;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

@AnalyzeClasses(packages = "com.yunhefc", importOptions = ImportOption.DoNotIncludeTests.class)

class ArchitectureTest {

    private static final String[] BUSINESS_DOMAINS = {
            "auth", "player", "match", "registration", "attendance", "event", "lineup",
            "grouping", "notification", "audit", "settings", "query", "stats"
    };

    @ArchTest
    static final ArchRule controllersMustNotAccessMappers =
            noClasses().that().resideInAPackage("..controller..")
                    .should().dependOnClassesThat().haveSimpleNameEndingWith("Mapper")
                    .because("controller 只能调用 application 服务");

    @ArchTest
    static final ArchRule commonAndContractsMustNotDependOnBusinessDomains =
            noClasses().that().resideInAnyPackage("com.yunhefc.common..", "com.yunhefc.contracts..")
                    .should().dependOnClassesThat().resideInAnyPackage(domainPackages())
                    .because("common/contracts 是横切基础包，不能反向依赖业务域");

    @ArchTest
    static void domainsMustNotAccessOtherDomainInfrastructure(JavaClasses classes) {
        for (String domain : BUSINESS_DOMAINS) {
            noClasses().that().resideInAPackage("com.yunhefc." + domain + "..")
                    .should().dependOnClassesThat().resideInAnyPackage(otherDomainInfrastructure(domain))
                    .because(domain + " 只能通过 contracts port 或对方 application 服务跨域读取")
                    .check(classes);
        }
    }

    @ArchTest
    static final ArchRule statsMustOnlyReadThroughContracts =
            noClasses().that().resideInAPackage("com.yunhefc.stats..")
                    .should().dependOnClassesThat().resideInAnyPackage(otherDomainPackages("stats"))
                    .because("stats 只读依赖各域 contracts 端口，不直读其他业务域");

    @ArchTest
    static final ArchRule onlyQueryAndGroupingMayDependOnStats =
            noClasses().that().resideInAnyPackage(domainPackagesExcept("stats", "query", "grouping"))
                    .should().dependOnClassesThat().resideInAPackage("com.yunhefc.stats..")
                    .because("仅 query 与 grouping 可依赖 stats 只读接口");

    private static String[] domainPackages() {
        return Arrays.stream(BUSINESS_DOMAINS)
                .map(domain -> "com.yunhefc." + domain + "..")
                .toArray(String[]::new);
    }

    private static String[] domainPackagesExcept(String... excluded) {
        return Arrays.stream(BUSINESS_DOMAINS)
                .filter(domain -> Arrays.stream(excluded).noneMatch(domain::equals))
                .map(domain -> "com.yunhefc." + domain + "..")
                .toArray(String[]::new);
    }

    private static String[] otherDomainInfrastructure(String domain) {
        return Arrays.stream(BUSINESS_DOMAINS)
                .filter(other -> !other.equals(domain))
                .map(other -> "com.yunhefc." + other + ".infrastructure..")
                .toArray(String[]::new);
    }

    private static String[] otherDomainPackages(String domain) {
        return Arrays.stream(BUSINESS_DOMAINS)
                .filter(other -> !other.equals(domain))
                .map(other -> "com.yunhefc." + other + "..")
                .toArray(String[]::new);
    }
}
