package prod.tint_wym.novora_backend;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.setup.MockMvcBuilders.webAppContextSetup;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.context.WebApplicationContext;

@SpringBootTest
@TestPropertySource(properties = "app.ai.enabled=false")
class PolicyAssistantIntegrationTest {

    @Autowired
    private WebApplicationContext webApplicationContext;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = webAppContextSetup(webApplicationContext).apply(springSecurity()).build();
    }

    private MockHttpSession registerEmployee(String email) throws Exception {
        return (MockHttpSession) mockMvc.perform(post("/api/auth/register")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + email + "\",\"password\":\"SecurePass1!\","
                                + "\"companyName\":\"Policy QA Co\"}"))
                .andExpect(status().isCreated())
                .andReturn()
                .getRequest()
                .getSession(false);
    }

    @Test
    void employeeGetsAnswerFromSeededLeavePolicies() throws Exception {
        MockHttpSession session = registerEmployee("policy-qa-employee@example.com");

        mockMvc.perform(post("/api/ai/policy-qa")
                        .session(session)
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"question\":\"How many sick leave days do I get?\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.source").value("heuristic"))
                .andExpect(jsonPath("$.answer").value(containsString("Sick Leave")))
                .andExpect(jsonPath("$.answer").value(containsString("7 days")))
                .andExpect(jsonPath("$.disclaimer").isString());
    }

    @Test
    void rejectsBlankQuestion() throws Exception {
        MockHttpSession session = registerEmployee("policy-qa-blank@example.com");

        mockMvc.perform(post("/api/ai/policy-qa")
                        .session(session)
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"question\":\"   \"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void requiresSignIn() throws Exception {
        mockMvc.perform(post("/api/ai/policy-qa")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"question\":\"annual leave\"}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void employeeCannotCallAdminAiEndpoints() throws Exception {
        MockHttpSession session = registerEmployee("policy-qa-admin-block@example.com");

        mockMvc.perform(post("/api/admin/ai/benefits-tip")
                        .session(session)
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"employeeName\":\"x\"}"))
                .andExpect(status().isForbidden());
    }
}
