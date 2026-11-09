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

    private static String[] domainPackages() {
        return Arrays.stream(BUSINESS_DOMAINS)
                .map(domain -> "com.yunhefc." + domain + "..")
                .toArray(String[]::new);
    }

    private static String[] otherDomainInfrastructure(String domain) {
        return Arrays.stream(BUSINESS_DOMAINS)
                .filter(other -> !other.equals(domain))
                .map(other -> "com.yunhefc." + other + ".infrastructure..")
                .toArray(String[]::new);
    }
}
