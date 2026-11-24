package com.yunhefc;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import jakarta.servlet.http.Cookie;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;

import java.util.UUID;

/**
 * 活动集成测试的数据构造工具；账号夹具见 {@link AccountITSupport}。
 */
abstract class ActivityITSupport extends AccountITSupport {

    protected ResultActions createActivity(Cookie admin, String title, String startAt,
                                          Integer capacity) throws Exception {
        String endAt = java.time.LocalDateTime.parse(startAt).plusHours(2).toString();
        String body = """
                {"title":"%s","type":0,"startAt":"%s","endAt":"%s",
                 "signupDeadline":"%s","capacity":%s,"familyAllowed":true,"costPerPerson":88.50,
                 "location":"队内餐厅","description":"季度聚餐"}
                """.formatted(title, startAt, endAt, startAt, capacity);
        return createActivity(admin, body);
    }

    protected ResultActions createActivity(Cookie admin, String body) throws Exception {
        return mockMvc.perform(post("/api/v1/b/activities")
                        .cookie(admin)
                        .with(csrf())
                        .header("Idempotency-Key", UUID.randomUUID().toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk());
    }

    protected Long activityId(ResultActions actions) throws Exception {
        return activityId(actions.andReturn());
    }

    protected Long activityId(MvcResult result) throws Exception {
        return JsonPath.<Number>read(result.getResponse().getContentAsString(), "$.data.id").longValue();
    }

    protected Long insertActivity(String title, String startAt, int status, Integer capacity,
                                  String deadline, long teamId) {
        return ActivityFixtures.insert(jdbc, title, startAt, status, capacity, deadline, teamId);
    }
}
