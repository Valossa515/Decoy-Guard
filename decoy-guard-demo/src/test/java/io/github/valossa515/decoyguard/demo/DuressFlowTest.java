package io.github.valossa515.decoyguard.demo;

import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class DuressFlowTest {

    @Autowired
    MockMvc mvc;

    @Test
    void normalSessionSeesRealBalance() throws Exception {
        String token = login("1234");

        mvc.perform(get("/account/balance").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.available").value(48750.32));
    }

    @Test
    void duressSessionSeesDecoyBalance() throws Exception {
        String token = login("4321");

        mvc.perform(get("/account/balance").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.available").value(312.47));
    }

    @Test
    void duressPixLooksSuccessfulButIsHeld() throws Exception {
        String realToken = login("1234");
        String duressToken = login("4321");
        String realBalanceBefore = balanceJson(realToken);

        mvc.perform(post("/account/pix")
                        .header("Authorization", "Bearer " + duressToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"key\":\"golpista@exemplo.com\",\"amount\":200.00}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CONCLUIDO"))
                .andExpect(jsonPath("$.endToEndId").isString());

        // The attacker sees the decoy balance drop...
        mvc.perform(get("/account/balance").header("Authorization", "Bearer " + duressToken))
                .andExpect(jsonPath("$.available").value(112.47));
        // ...the real account is untouched...
        assertThat(balanceJson(realToken)).isEqualTo(realBalanceBefore);
        // ...and the fraud desk can see what was attempted.
        mvc.perform(get("/fraud-desk/held").param("session", duressToken))
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].counterparty").value("golpista@exemplo.com"));
    }

    @Test
    void decoyRejectsTransfersAboveDecoyBalanceLikeTheRealAccount() throws Exception {
        String token = login("4321");

        mvc.perform(post("/account/pix")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"key\":\"golpista@exemplo.com\",\"amount\":5000.00}"))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.error").value("Saldo insuficiente"));
    }

    @Test
    void responsesHaveTheSameShapeInBothModes() throws Exception {
        Set<String> realFields = fieldsOf(balanceJson(login("1234")));
        Set<String> decoyFields = fieldsOf(balanceJson(login("4321")));

        assertThat(decoyFields).isEqualTo(realFields);
    }

    @Test
    void wrongPinIsRejected() throws Exception {
        mvc.perform(post("/login").contentType(MediaType.APPLICATION_JSON).content("{\"pin\":\"0000\"}"))
                .andExpect(status().isUnauthorized());
    }

    private String login(String pin) throws Exception {
        MvcResult result = mvc.perform(post("/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"pin\":\"" + pin + "\"}"))
                .andExpect(status().isOk())
                .andReturn();
        return JsonPath.read(result.getResponse().getContentAsString(), "$.token");
    }

    private String balanceJson(String token) throws Exception {
        return mvc.perform(get("/account/balance").header("Authorization", "Bearer " + token))
                .andReturn().getResponse().getContentAsString();
    }

    private static Set<String> fieldsOf(String json) {
        java.util.Map<String, Object> map = JsonPath.read(json, "$");
        return map.keySet();
    }
}
