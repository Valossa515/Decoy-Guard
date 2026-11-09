package io.github.valossa515.decoyguard.demo;

import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.forwardedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * FT-002: the mobile-style front end served by the demo. Criteria ids refer to
 * .agent/specs/acceptance/mobile-front-end.acceptance.toon.
 */
@SpringBootTest
@AutoConfigureMockMvc
class FrontEndTest {

    private static final Pattern TEMPLATE =
            Pattern.compile("<template id=\"phone-template\">(.*?)</template>", Pattern.DOTALL);

    @Autowired
    MockMvc mvc;

    /** AC-01 */
    @Test
    void pageIsServed() throws Exception {
        String html = fetch("/index.html");

        assertThat(html)
                .contains("Mesa de fraude")
                .containsOnlyOnce("<template id=\"phone-template\">");
        assertThat(count(html, "data-phone>")).isEqualTo(2);
    }

    /** AC-02 */
    @Test
    void rootServesThePage() throws Exception {
        mvc.perform(get("/"))
                .andExpect(status().isOk())
                .andExpect(forwardedUrl("index.html"));
    }

    /** AC-03 */
    @Test
    void assetsAreServed() throws Exception {
        mvc.perform(get("/styles.css"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith("text/css"));
        String type = mvc.perform(get("/app.js"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentType();
        assertThat(type).contains("javascript");
    }

    /** AC-04: rule 1, nothing inside the phone may tell the sessions apart. */
    @Test
    void phoneTemplateHasNoModeMarker() throws Exception {
        String html = fetch("/index.html");
        String template = phoneTemplate(html);

        Pattern marker = Pattern.compile("(?iu)coa[cç][aã]o|duress|decoy|\\bnormal\\b|\\breal\\b|1234|4321");
        assertThat(marker.matcher(template).find())
                .as("mode marker inside the phone template")
                .isFalse();

        String outside = html.replace(template, "");
        assertThat(outside)
                .contains("Sessão normal (PIN 1234)")
                .contains("Sessão sob coação (PIN 4321)");
    }

    /** AC-05: one module mounts both phones, and the phone talks only to the account API. */
    @Test
    void phonesShareOneModuleAndUseOnlyTheAccountApi() throws Exception {
        String js = fetch("/app.js");

        assertThat(js).contains("document.querySelectorAll('[data-phone]').forEach((slot) => mountPhone(slot));");
        assertThat(js).containsOnlyOnce("function mountPhone(slot)");

        String phoneCode = js.substring(js.indexOf("function mountPhone(slot)"), js.indexOf("// --- fraud desk"));
        Set<String> paths = new TreeSet<>();
        Matcher literal = Pattern.compile("'(/[a-z\\-/]+)'").matcher(phoneCode);
        while (literal.find()) {
            paths.add(literal.group(1));
        }
        assertThat(paths).containsExactlyInAnyOrder(
                "/login", "/account/balance", "/account/statement", "/account/pix");
        assertThat(phoneCode).doesNotContainIgnoringCase("fraud").doesNotContainIgnoringCase("duress");
    }

    /** AC-06: the panel polls every token; only the duress session has an alert and a held Pix. */
    @Test
    void fraudDeskShowsOnlyTheDuressSession() throws Exception {
        String normal = login("1234");
        String duress = login("4321");
        mvc.perform(post("/account/pix")
                        .header("Authorization", "Bearer " + duress)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"key\":\"golpista@exemplo.com\",\"amount\":\"200.00\"}"))
                .andExpect(status().isOk());

        mvc.perform(get("/fraud-desk/alerts").param("session", duress))
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].sessionId").value(duress))
                .andExpect(jsonPath("$[0].trigger").value("DURESS_CREDENTIAL"))
                .andExpect(jsonPath("$[0].raisedAt").isString());
        mvc.perform(get("/fraud-desk/held").param("session", duress))
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].amount").value(200.00));

        mvc.perform(get("/fraud-desk/alerts").param("session", normal))
                .andExpect(jsonPath("$", hasSize(0)));
        mvc.perform(get("/fraud-desk/held").param("session", normal))
                .andExpect(jsonPath("$", hasSize(0)));
    }

    /** AC-07 */
    @Test
    void pageUsesNoExternalHost() throws Exception {
        for (String path : List.of("/index.html", "/app.js", "/styles.css")) {
            assertThat(fetch(path)).as(path).doesNotContain("http://").doesNotContain("https://");
        }
    }

    private String fetch(String path) throws Exception {
        MvcResult result = mvc.perform(get(path)).andExpect(status().isOk()).andReturn();
        return result.getResponse().getContentAsString(StandardCharsets.UTF_8);
    }

    private static String phoneTemplate(String html) {
        Matcher matcher = TEMPLATE.matcher(html);
        assertThat(matcher.find()).as("phone template present").isTrue();
        return matcher.group(1);
    }

    private static int count(String text, String needle) {
        int count = 0;
        for (int i = text.indexOf(needle); i >= 0; i = text.indexOf(needle, i + 1)) {
            count++;
        }
        return count;
    }

    private String login(String pin) throws Exception {
        MvcResult result = mvc.perform(post("/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"pin\":\"" + pin + "\"}"))
                .andExpect(status().isOk())
                .andReturn();
        return JsonPath.read(result.getResponse().getContentAsString(), "$.token");
    }
}
