package io.github.valossa515.decoyguard.demo;

import com.jayway.jsonpath.JsonPath;
import io.github.valossa515.decoyguard.demo.account.CoreBankingLatency;
import io.github.valossa515.decoyguard.demo.account.RealAccountService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.util.Map;
import java.util.TreeSet;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * FT-004: every Pix starts "em processamento", the real one settles and the held one does not.
 * Criteria ids refer to .agent/specs/acceptance/pix-em-processamento.acceptance.toon.
 *
 * <p>Time is a fake clock that the tests move forward, so nothing waits for the settlement.
 */
@SpringBootTest(properties = {
        "demo.core-banking-latency.min=0ms",
        "demo.core-banking-latency.max=0ms",
        "decoy-guard.latency-shaping.fallback-min=0ms",
        "decoy-guard.latency-shaping.fallback-max=0ms",
        "demo.pix-settlement=30s"
})
@AutoConfigureMockMvc
class PixProcessingTest {

    private static final Duration SETTLEMENT = Duration.ofSeconds(30);
    private static final String PIX_BODY = "{\"key\":\"golpista@exemplo.com\",\"amount\":50.00}";

    @TestConfiguration(proxyBeanMethods = false)
    static class FakeTime {

        @Bean
        @Primary
        MutableClock testClock() {
            return new MutableClock(Instant.parse("2026-11-10T15:00:00Z"), ZoneId.of("America/Sao_Paulo"));
        }
    }

    @Autowired
    MockMvc mvc;

    @Autowired
    MutableClock clock;

    private String normal;
    private String duress;

    @BeforeEach
    void openSessions() throws Exception {
        normal = login("1234");
        duress = login("4321");
    }

    /** AC-01 */
    @Test
    void normalPixStartsInProcessing() throws Exception {
        mvc.perform(post("/account/pix")
                        .header("Authorization", "Bearer " + normal)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(PIX_BODY))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("EM_PROCESSAMENTO"))
                .andExpect(jsonPath("$.endToEndId").isString());
    }

    /** AC-02: rule 1, the first receipt cannot tell the sessions apart. */
    @Test
    void duressPixStartsInProcessingWithTheSameFieldsAndHeaders() throws Exception {
        MockHttpServletResponse real = sendPix(normal);
        MockHttpServletResponse decoy = sendPix(duress);

        assertThat(decoy.getStatus()).isEqualTo(200).isEqualTo(real.getStatus());
        assertThat(statusOf(decoy)).isEqualTo("EM_PROCESSAMENTO").isEqualTo(statusOf(real));
        assertThat(fieldsOf(decoy)).isEqualTo(fieldsOf(real));
        assertThat(headerNames(decoy)).isEqualTo(headerNames(real));
        assertThat(decoy.getCookies()).isEmpty();
        assertThat(real.getCookies()).isEmpty();
    }

    /** AC-03 */
    @Test
    void realPixSettlesAfterTheSettlementTime() throws Exception {
        String id = idOf(sendPix(normal));

        assertThat(statusOf(findPix(normal, id))).isEqualTo("EM_PROCESSAMENTO");

        clock.advance(SETTLEMENT.minusSeconds(1));
        assertThat(statusOf(findPix(normal, id))).isEqualTo("EM_PROCESSAMENTO");

        clock.advance(Duration.ofSeconds(1));
        assertThat(statusOf(findPix(normal, id))).isEqualTo("CONCLUIDO");
    }

    /** AC-04 */
    @Test
    void heldPixStaysInProcessingAfterTheSettlementTime() throws Exception {
        String id = idOf(sendPix(duress));

        clock.advance(SETTLEMENT.plusDays(1));

        MockHttpServletResponse later = findPix(duress, id);
        assertThat(later.getStatus()).isEqualTo(200);
        assertThat(statusOf(later)).isEqualTo("EM_PROCESSAMENTO");
    }

    /** AC-05: parity of the new routed method, same receipt and same shape in both sessions. */
    @Test
    void consultationMatchesTheOriginalReceiptInBothSessions() throws Exception {
        MockHttpServletResponse realSent = sendPix(normal);
        MockHttpServletResponse decoySent = sendPix(duress);

        MockHttpServletResponse realFound = findPix(normal, idOf(realSent));
        MockHttpServletResponse decoyFound = findPix(duress, idOf(decoySent));

        assertThat(realFound.getContentAsString()).isEqualTo(realSent.getContentAsString());
        assertThat(decoyFound.getContentAsString()).isEqualTo(decoySent.getContentAsString());
        assertThat(fieldsOf(decoyFound)).isEqualTo(fieldsOf(realFound));
        assertThat(headerNames(decoyFound)).isEqualTo(headerNames(realFound));
    }

    /** AC-06 */
    @Test
    void unknownAndOtherModeIdsReturnTheSame404InBothSessions() throws Exception {
        String realId = idOf(sendPix(normal));
        String heldId = idOf(sendPix(duress));

        MockHttpServletResponse realUnknown = findPix(normal, "E0000000000000000000000000000000");
        MockHttpServletResponse decoyUnknown = findPix(duress, "E0000000000000000000000000000000");
        MockHttpServletResponse realAskingForHeld = findPix(normal, heldId);
        MockHttpServletResponse decoyAskingForReal = findPix(duress, realId);

        for (MockHttpServletResponse response : new MockHttpServletResponse[] {
                realUnknown, decoyUnknown, realAskingForHeld, decoyAskingForReal}) {
            assertThat(response.getStatus()).isEqualTo(404);
            assertThat(response.getContentAsString()).isEqualTo(realUnknown.getContentAsString());
            assertThat(headerNames(response)).isEqualTo(headerNames(realUnknown));
        }
        String error = JsonPath.read(realUnknown.getContentAsString(), "$.error");
        assertThat(error).isEqualTo("Pix não encontrado");
    }

    /** AC-07 */
    @Test
    void balanceDropsWhileThePixIsInProcessing() throws Exception {
        BigDecimal realBefore = balance(normal);
        BigDecimal decoyBefore = balance(duress);

        String realId = idOf(sendPix(normal));
        String heldId = idOf(sendPix(duress));

        assertThat(balance(normal)).isEqualByComparingTo(realBefore.subtract(new BigDecimal("50.00")));
        assertThat(balance(duress)).isEqualByComparingTo(decoyBefore.subtract(new BigDecimal("50.00")));
        assertThat(statusOf(findPix(normal, realId))).isEqualTo("EM_PROCESSAMENTO");
        assertThat(statusOf(findPix(duress, heldId))).isEqualTo("EM_PROCESSAMENTO");
    }

    /** AC-08 */
    @Test
    void heldPixIsListedByTheFraudDeskAndRealBalanceIsUnchanged() throws Exception {
        BigDecimal realBefore = balance(normal);

        String heldId = idOf(sendPix(duress));

        assertThat(statusOf(findPix(duress, heldId))).isEqualTo("EM_PROCESSAMENTO");
        mvc.perform(get("/fraud-desk/held").param("session", duress))
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].id").value(heldId))
                .andExpect(jsonPath("$[0].counterparty").value("golpista@exemplo.com"));
        assertThat(balance(normal)).isEqualByComparingTo(realBefore);
    }

    /** AC-09 */
    @Test
    void consultationWithoutASessionReturns401() throws Exception {
        String id = idOf(sendPix(normal));

        mvc.perform(get("/account/pix/" + id))
                .andExpect(status().isUnauthorized());
        mvc.perform(get("/account/pix/" + id).header("Authorization", "Bearer not-a-session"))
                .andExpect(status().isUnauthorized());
    }

    /** BR-009: the settlement time is configuration, and a negative one fails startup. */
    @Test
    void negativeSettlementIsRejected() {
        CoreBankingLatency noLatency = new CoreBankingLatency(Duration.ZERO, Duration.ZERO);

        assertThatThrownBy(() -> new RealAccountService(clock, noLatency, Duration.ofSeconds(-1)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("demo.pix-settlement");
    }

    private MockHttpServletResponse sendPix(String token) throws Exception {
        return mvc.perform(post("/account/pix")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(PIX_BODY))
                .andReturn().getResponse();
    }

    private MockHttpServletResponse findPix(String token, String endToEndId) throws Exception {
        return mvc.perform(get("/account/pix/" + endToEndId).header("Authorization", "Bearer " + token))
                .andReturn().getResponse();
    }

    private BigDecimal balance(String token) throws Exception {
        String json = mvc.perform(get("/account/balance").header("Authorization", "Bearer " + token))
                .andReturn().getResponse().getContentAsString();
        Number available = JsonPath.read(json, "$.available");
        return new BigDecimal(available.toString());
    }

    private static String idOf(MockHttpServletResponse response) throws Exception {
        return JsonPath.read(response.getContentAsString(), "$.endToEndId");
    }

    private static String statusOf(MockHttpServletResponse response) throws Exception {
        return JsonPath.read(response.getContentAsString(), "$.status");
    }

    private static TreeSet<String> fieldsOf(MockHttpServletResponse response) throws Exception {
        Map<String, Object> body = JsonPath.read(response.getContentAsString(), "$");
        return new TreeSet<>(body.keySet());
    }

    private static TreeSet<String> headerNames(MockHttpServletResponse response) {
        return new TreeSet<>(response.getHeaderNames());
    }

    private String login(String pin) throws Exception {
        MvcResult result = mvc.perform(post("/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"pin\":\"" + pin + "\"}"))
                .andExpect(status().isOk())
                .andReturn();
        return JsonPath.read(result.getResponse().getContentAsString(), "$.token");
    }

    /** A clock that only moves when a test says so. */
    static final class MutableClock extends Clock {

        private final AtomicReference<Instant> now;
        private final ZoneId zone;

        MutableClock(Instant start, ZoneId zone) {
            this.now = new AtomicReference<>(start);
            this.zone = zone;
        }

        void advance(Duration duration) {
            now.updateAndGet(instant -> instant.plus(duration));
        }

        @Override
        public Instant instant() {
            return now.get();
        }

        @Override
        public ZoneId getZone() {
            return zone;
        }

        @Override
        public Clock withZone(ZoneId newZone) {
            return new MutableClock(now.get(), newZone);
        }
    }
}
