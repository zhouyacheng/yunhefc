package com.yunhefc.common.util;

import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class MaskUtilsTest {

    @Test
    void masksPhoneKeepingPrefixAndSuffix() {
        assertThat(MaskUtils.maskPhone("13812345678")).isEqualTo("138****5678");
        assertThat(MaskUtils.maskPhone("12345")).isEqualTo("***");
        assertThat(MaskUtils.maskPhone(null)).isNull();
    }

    @Test
    void masksSensitiveValuesInFreeText() {
        assertThat(MaskUtils.maskText("user login, phone=13812345678"))
                .isEqualTo("user login, phone=138****5678");
        assertThat(MaskUtils.maskText("mail to zhangsan@example.com"))
                .isEqualTo("mail to ***@example.com");
        assertThat(MaskUtils.maskText("id=110101199001011234"))
                .isEqualTo("id=110101********1234");
        assertThat(MaskUtils.maskText("nothing sensitive here"))
                .isEqualTo("nothing sensitive here");
        assertThat(MaskUtils.maskText(null)).isNull();
    }

    @Test
    void masksIpv4LastSegment() {
        assertThat(MaskUtils.maskIp("192.168.1.25")).isEqualTo("192.168.1.*");
        assertThat(MaskUtils.maskIp("10.0.0.1")).isEqualTo("10.0.0.*");
        assertThat(MaskUtils.maskIp("999.1.1.1")).isEqualTo("***");
        assertThat(MaskUtils.maskIp("not-an-ip")).isEqualTo("***");
        assertThat(MaskUtils.maskIp(null)).isNull();
    }

    @Test
    void masksIpv6KeepingFirstThreeGroups() {
        assertThat(MaskUtils.maskIp("2001:db8:85a3:8d3:1319:8a2e:370:7348"))
                .isEqualTo("2001:db8:85a3::*");
        assertThat(MaskUtils.maskIp("::1")).isEqualTo("0:0:0::*");
        assertThat(MaskUtils.maskIp("2001:db8:zzzz")).isEqualTo("***");
    }

    @Test
    void masksNestedObjectsAndArraysIgnoringKeyCase() {
        Map<String, Object> snapshot = new LinkedHashMap<>();
        snapshot.put("phone", "13812345678");
        snapshot.put("OpenID", "o-raw");
        snapshot.put("nested", Map.of(
                "PASSWORDHASH", "hash",
                "TokenHash", 42,
                "token", "raw-token",
                "region", "杭州",
                "items", List.of(
                        Map.of("Phone", "13900001111"),
                        Map.of("reason", "受伤"))));
        snapshot.put("avatarUrl", "avatar/1/x.jpg");

        Object masked = MaskUtils.mask(snapshot);

        assertThat(masked).isInstanceOf(Map.class);
        @SuppressWarnings("unchecked")
        Map<String, Object> result = (Map<String, Object>) masked;
        assertThat(result.get("phone")).isEqualTo("138****5678");
        assertThat(result.get("OpenID")).isEqualTo("***");
        assertThat(result.get("avatarUrl")).isEqualTo("avatar/1/x.jpg");

        @SuppressWarnings("unchecked")
        Map<String, Object> nested = (Map<String, Object>) result.get("nested");
        assertThat(nested.get("PASSWORDHASH")).isEqualTo("***");
        assertThat(nested.get("TokenHash")).isEqualTo("***");
        assertThat(nested.get("token")).isEqualTo("***");
        assertThat(nested.get("region")).isEqualTo("杭州");

        @SuppressWarnings("unchecked")
        List<Object> items = (List<Object>) nested.get("items");
        @SuppressWarnings("unchecked")
        Map<String, Object> first = (Map<String, Object>) items.get(0);
        @SuppressWarnings("unchecked")
        Map<String, Object> second = (Map<String, Object>) items.get(1);
        assertThat(first.get("Phone")).isEqualTo("139****1111");
        assertThat(second.get("reason")).isEqualTo("受伤");

        // 纯函数：入参不被修改
        assertThat(snapshot.get("phone")).isEqualTo("13812345678");
    }

    @Test
    void doesNotMaskUnrelatedFields() {
        Map<String, Object> snapshot = Map.of(
                "region", "宁波",
                "reason", "临时有事",
                "avatarUrl", "avatar/7/object-key.jpg");

        assertThat(MaskUtils.mask(snapshot)).isEqualTo(snapshot);
    }

    @Test
    void handlesNullInput() {
        assertThat(MaskUtils.mask(null)).isNull();
    }
}
