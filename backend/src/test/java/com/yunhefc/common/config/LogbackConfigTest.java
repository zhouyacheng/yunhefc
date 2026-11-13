package com.yunhefc.common.config;

import static org.assertj.core.api.Assertions.assertThat;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.classic.spi.LoggingEvent;
import ch.qos.logback.classic.spi.ThrowableProxy;
import org.junit.jupiter.api.Test;
import org.springframework.boot.logging.logback.StructuredLogEncoder;
import org.springframework.core.env.Environment;
import org.springframework.mock.env.MockEnvironment;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import javax.xml.parsers.DocumentBuilderFactory;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

class LogbackConfigTest {

    private static final String STRUCTURED_ENCODER =
            "org.springframework.boot.logging.logback.StructuredLogEncoder";

    @Test
    void prodUsesEcsJsonEncodersAndRollingPolicy() throws Exception {
        Element prod = profile("prod");
        Map<String, Element> appenders = appenders(prod);

        Element console = appenders.get("CONSOLE");
        assertThat(console).isNotNull();
        assertThat(encoderClass(console)).isEqualTo(STRUCTURED_ENCODER);
        assertThat(text(child(console, "encoder"), "format")).isEqualTo("ecs");

        Element file = appenders.get("FILE");
        assertThat(file).isNotNull();
        assertThat(encoderClass(file)).isEqualTo(STRUCTURED_ENCODER);
        assertThat(text(child(file, "encoder"), "format")).isEqualTo("ecs");

        Element policy = child(file, "rollingPolicy");
        assertThat(policy.getAttribute("class"))
                .isEqualTo("ch.qos.logback.core.rolling.SizeAndTimeBasedRollingPolicy");
        assertThat(text(policy, "maxFileSize")).isEqualTo("100MB");
        assertThat(text(policy, "maxHistory")).isEqualTo("14");
        assertThat(text(policy, "totalSizeCap")).isEqualTo("2GB");
    }

    @Test
    void nonProdKeepsReadableConsoleAppender() throws Exception {
        Element nonProd = profile("!prod");

        Element include = child(nonProd, "include");
        assertThat(include.getAttribute("resource"))
                .isEqualTo("org/springframework/boot/logging/logback/console-appender.xml");
        assertThat(nonProd.getElementsByTagName("encoder").getLength()).isZero();
    }

    @Test
    void ecsEncoderWritesJsonWithMdcAndException() {
        MockEnvironment environment = new MockEnvironment()
                .withProperty("logging.structured.json.context.include", "true");
        LoggerContext context = new LoggerContext();
        context.start();
        context.putObject(Environment.class.getName(), environment);

        StructuredLogEncoder encoder = new StructuredLogEncoder();
        encoder.setContext(context);
        encoder.setFormat("ecs");
        encoder.start();

        LoggingEvent event = new LoggingEvent();
        event.setLoggerContext(context);
        event.setLevel(Level.INFO);
        event.setLoggerName("com.yunhefc.LogTest");
        event.setThreadName("test-thread");
        event.setTimeStamp(0L);
        event.setMessage("boom");
        event.setMDCPropertyMap(Map.of("requestId", "req-1", "userId", "42"));
        event.setThrowableProxy(new ThrowableProxy(new IllegalStateException("boom")));

        String json = new String(encoder.encode(event), StandardCharsets.UTF_8);

        assertThat(json)
                .contains("\"log\":{")
                .contains("\"level\":\"INFO\"")
                .contains("\"logger\":\"com.yunhefc.LogTest\"")
                .contains("\"message\":\"boom\"")
                .contains("\"requestId\":\"req-1\"")
                .contains("\"userId\":\"42\"")
                .contains("\"error\":{")
                .contains("\"stack_trace\"");
        encoder.stop();
        context.stop();
    }

    private Element profile(String name) throws Exception {
        try (InputStream input = getClass().getResourceAsStream("/logback-spring.xml")) {
            assertThat(input).as("logback-spring.xml").isNotNull();
            Document document = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(input);
            NodeList profiles = document.getElementsByTagName("springProfile");
            for (int i = 0; i < profiles.getLength(); i++) {
                Element element = (Element) profiles.item(i);
                if (name.equals(element.getAttribute("name"))) {
                    return element;
                }
            }
        }
        throw new AssertionError("missing springProfile " + name);
    }

    private Map<String, Element> appenders(Element profile) {
        Map<String, Element> appenders = new HashMap<>();
        NodeList nodes = profile.getElementsByTagName("appender");
        for (int i = 0; i < nodes.getLength(); i++) {
            Element element = (Element) nodes.item(i);
            appenders.put(element.getAttribute("name"), element);
        }
        return appenders;
    }

    private String encoderClass(Element appender) {
        return child(appender, "encoder").getAttribute("class");
    }

    private Element child(Element parent, String tagName) {
        NodeList nodes = parent.getChildNodes();
        for (int i = 0; i < nodes.getLength(); i++) {
            Node node = nodes.item(i);
            if (node instanceof Element element && tagName.equals(element.getTagName())) {
                return element;
            }
        }
        throw new AssertionError("missing <" + tagName + "> under <" + parent.getTagName() + ">");
    }

    private String text(Element parent, String tagName) {
        return child(parent, tagName).getTextContent().trim();
    }
}
