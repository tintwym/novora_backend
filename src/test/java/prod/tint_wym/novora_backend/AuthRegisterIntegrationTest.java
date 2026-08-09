package prod.tint_wym.novora_backend;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.setup.MockMvcBuilders.webAppContextSetup;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.context.WebApplicationContext;

@SpringBootTest
class AuthRegisterIntegrationTest {

    @Autowired
    private WebApplicationContext webApplicationContext;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = webAppContextSetup(webApplicationContext).build();
    }

    @Test
    void registerReturnsCreatedAndSessionAuth() throws Exception {
        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"regtest-unique@example.com\","
                                + "\"password\":\"SecurePass1!\","
                                + "\"companyName\":\"Acme Demo Co\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.email").value("regtest-unique@example.com"))
                .andExpect(jsonPath("$.accessToken").isEmpty())
                // Public signup is always EMPLOYEE; Admin assigns position/role later.
                .andExpect(jsonPath("$.roles[0]").value("EMPLOYEE"))
                .andExpect(jsonPath("$.organization.name").isString())
                .andExpect(jsonPath("$.organization.plan").isString())
                .andExpect(jsonPath("$.organization.status").value("ACTIVE"));
    }

    @Test
    void registerDuplicateEmailConflict() throws Exception {
        String body = "{\"email\":\"regtest-dup@example.com\","
                + "\"password\":\"SecurePass1!\","
                + "\"companyName\":\"Dup Test Co\"}";
        mockMvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated());
        mockMvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isConflict());
    }

    @Test
    void registerRejectsWeakPasswordWithMessage() throws Exception {
        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"weak-pass@example.com\","
                                + "\"password\":\"noupper1!\","
                                + "\"companyName\":\"Weak Pass Co\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").isString())
                .andExpect(jsonPath("$.errors.password").exists());
    }

    @Test
    void registerRejectsMissingCompanyName() throws Exception {
        // companyName is required (used when creating a workspace if no Admin org exists yet).
        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"missing-co@example.com\","
                                + "\"password\":\"SecurePass1!\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.companyName").exists());
    }
}
