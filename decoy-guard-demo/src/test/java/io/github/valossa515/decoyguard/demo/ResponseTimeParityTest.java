package io.github.valossa515.decoyguard.demo;

import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeSet;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

/**
 * FT-003 end to end. The simulated core banking hop is fixed at 60 ms and the fallback range is
 * zero, so a decoy call can only take as long as a real one if it learned that from real calls.
 * Lower bounds are safe on a slow machine because a sleep never returns early. Upper bounds are
 * generous on purpose.
 */
@SpringBootTest(properties = {
        "demo.core-banking-latency.min=60ms",
        "demo.core-banking-latency.max=60ms",
        "decoy-guard.latency-shaping.min-observations=3",
        "decoy-guard.latency-shaping.fallback-min=0ms",
        "decoy-guard.latency-shaping.fallback-max=0ms"
})
@AutoConfigureMockMvc
class ResponseTimeParityTest {

    private static final int ROUNDS = 5;
    private static final long HOP_MILLIS = 60;
    private static final long SLACK_MILLIS = 250;

    @Autowired
    MockMvc mvc;

    private String normal;
    private String duress;

    @BeforeEach
    void openSessions() throws Exception {
        normal = login("1234");
        duress = login("4321");
    }

    @Test
    void balanceAndPixTakeComparableTimeInBothSessions() throws Exception {
        // AC-10
        long realBalance = median(normal, this::balance);
        long realPix = median(normal, token -> pix(token, "1.00"));
        long decoyBalance = median(duress, this::balance);
        long decoyPix = median(duress, token -> pix(token, "1.00"));

        // One hop for the balance, two for an accepted Pix (core banking and the Pix rail).
        assertThat(realBalance).isGreaterThanOrEqualTo(HOP_MILLIS);
        assertThat(realPix).isGreaterThanOrEqualTo(2 * HOP_MILLIS);
        assertThat(decoyBalance).isBetween(HOP_MILLIS - 5, realBalance + SLACK_MILLIS);
        assertThat(decoyPix).isBetween(2 * HOP_MILLIS - 5, realPix + SLACK_MILLIS);
    }

    @Test
    void rejectedPixIsIdenticalAndComparableInBothSessions() throws Exception {
        // AC-11
        long real = median(normal, token -> pix(token, "9999999.00"));
        long decoy = median(duress, token -> pix(token, "9999999.00"));

        assertThat(decoy).isBetween(HOP_MILLIS - 5, real + SLACK_MILLIS);

        MockHttpServletResponse realResponse = mvc.perform(pix(normal, "9999999.00")).andReturn().getResponse();
        MockHttpServletResponse decoyResponse = mvc.perform(pix(duress, "9999999.00")).andReturn().getResponse();
        assertThat(decoyResponse.getStatus()).isEqualTo(422).isEqualTo(realResponse.getStatus());
        assertThat(decoyResponse.getContentAsString()).isEqualTo(realResponse.getContentAsString());
        assertThat(headerNames(decoyResponse)).isEqualTo(headerNames(realResponse));
    }

    @Test
    void responsesHaveTheSameFieldsAndHeaders() throws Exception {
        // AC-10: shaping adds nothing the client can see
        for (int i = 0; i < 3; i++) {
            mvc.perform(balance(normal));
        }
        MockHttpServletResponse real = mvc.perform(balance(normal)).andReturn().getResponse();
        MockHttpServletResponse decoy = mvc.perform(balance(duress)).andReturn().getResponse();

        assertThat(headerNames(decoy)).isEqualTo(headerNames(real));
        assertThat(decoy.getCookies()).isEmpty();
        assertThat(real.getCookies()).isEmpty();
        Map<String, Object> realBody = JsonPath.read(real.getContentAsString(), "$");
        Map<String, Object> decoyBody = JsonPath.read(decoy.getContentAsString(), "$");
        assertThat(decoyBody.keySet()).isEqualTo(realBody.keySet());
    }

    private interface Request {
        MockHttpServletRequestBuilder with(String token);
    }

    private long median(String token, Request request) throws Exception {
        List<Long> millis = new ArrayList<>();
        for (int i = 0; i < ROUNDS; i++) {
            long start = System.nanoTime();
            mvc.perform(request.with(token));
            millis.add((System.nanoTime() - start) / 1_000_000);
        }
        millis.sort(null);
        return millis.get(ROUNDS / 2);
    }

    private MockHttpServletRequestBuilder balance(String token) {
        return get("/account/balance").header("Authorization", "Bearer " + token);
    }

    private MockHttpServletRequestBuilder pix(String token, String amount) {
        return post("/account/pix")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"key\":\"golpista@exemplo.com\",\"amount\":" + amount + "}");
    }

    private static TreeSet<String> headerNames(MockHttpServletResponse response) {
        return new TreeSet<>(response.getHeaderNames());
    }

    private String login(String pin) throws Exception {
        MvcResult result = mvc.perform(post("/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"pin\":\"" + pin + "\"}"))
                .andReturn();
        return JsonPath.read(result.getResponse().getContentAsString(), "$.token");
    }
}
