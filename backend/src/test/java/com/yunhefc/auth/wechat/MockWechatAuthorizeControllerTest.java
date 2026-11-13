package com.yunhefc.auth.wechat;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.util.TestPropertyValues;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;

import static org.assertj.core.api.Assertions.assertThat;

class MockWechatAuthorizeControllerTest {

    @Test
    void registeredOnlyOutsideProdInMockMode() {
        assertThat(beanNames("dev", "mock"))
                .containsExactly("mockWechatAuthorizeController");
        assertThat(beanNames("prod", "mock")).isEmpty();
        assertThat(beanNames("dev", "real")).isEmpty();
    }

    private String[] beanNames(String profile, String mode) {
        try (AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext()) {
            context.getEnvironment().setActiveProfiles(profile);
            TestPropertyValues.of("yunhefc.wechat.mode=" + mode).applyTo(context);
            context.register(MockWechatAuthorizeController.class);
            context.refresh();
            return context.getBeanNamesForType(MockWechatAuthorizeController.class);
        }
    }
}
