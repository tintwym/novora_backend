package prod.tint_wym.novora_backend.integration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.setup.MockMvcBuilders.webAppContextSetup;

import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.web.FilterChainProxy;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.web.context.WebApplicationContext;
import prod.tint_wym.novora_backend.dto.CrmIntegrationDtos;
import prod.tint_wym.novora_backend.dto.HrDtos;
import prod.tint_wym.novora_backend.entity.CrmConnection;
import prod.tint_wym.novora_backend.entity.CrmLinkCode;
import prod.tint_wym.novora_backend.entity.Department;
import prod.tint_wym.novora_backend.entity.IntegrationOutboxEvent;
import prod.tint_wym.novora_backend.entity.Organization;
import prod.tint_wym.novora_backend.entity.Position;
import prod.tint_wym.novora_backend.repository.CrmConnectionRepository;
import prod.tint_wym.novora_backend.repository.CrmLinkCodeRepository;
import prod.tint_wym.novora_backend.repository.DepartmentRepository;
import prod.tint_wym.novora_backend.repository.IntegrationOutboxRepository;
import prod.tint_wym.novora_backend.repository.OrganizationRepository;
import prod.tint_wym.novora_backend.repository.PositionRepository;
import prod.tint_wym.novora_backend.service.CrmIntegrationService;
import prod.tint_wym.novora_backend.service.EmployeeService;
import prod.tint_wym.novora_backend.tenancy.TenantContext;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * End-to-end checks for the CRM link: one-time codes, signed requests, workspace isolation, and
 * the outbox that carries employee changes to the CRM.
 */
@SpringBootTest(properties = {
        "app.crm.enabled=true",
        "app.crm.base-url=http://127.0.0.1:9",
        "app.crm.integration-secret=" + CrmIntegrationFlowTest.SECRET,
        // Keep the scheduled dispatcher out of the way; tests drive delivery explicitly.
        "app.crm.dispatch-initial-delay-ms=3600000"
})
class CrmIntegrationFlowTest {

    static final String SECRET = "flow-test-crm-integration-secret-0123456789";
    private static final String BASE = "/api/integrations/crm/v1";

    @Autowired private WebApplicationContext webApplicationContext;
    @Autowired private FilterChainProxy springSecurityFilterChain;
    @Autowired private CrmIntegrationService crmIntegrationService;
    @Autowired private EmployeeService employeeService;
    @Autowired private OrganizationRepository organizationRepository;
    @Autowired private DepartmentRepository departmentRepository;
    @Autowired private PositionRepository positionRepository;
    @Autowired private CrmConnectionRepository connectionRepository;
    @Autowired private CrmLinkCodeRepository linkCodeRepository;
    @Autowired private IntegrationOutboxRepository outboxRepository;
    @Autowired private JsonMapper jsonMapper;

    private MockMvc mockMvc;
    private String suffix;
    private UUID orgA;
    private UUID orgB;
    private UUID salesDeptA;
    private UUID managerPositionA;
    private UUID salesDeptB;

    @BeforeEach
    void setUp() {
        mockMvc = webAppContextSetup(webApplicationContext).addFilters(springSecurityFilterChain).build();
        TenantContext.clear();
        suffix = UUID.randomUUID().toString().substring(0, 8);
        orgA = newOrganization("Acme " + suffix);
        orgB = newOrganization("Globex " + suffix);
        Department deptA = inOrg(orgA, () -> newDepartment("Sales", "SLS"));
        salesDeptA = deptA.getId();
        managerPositionA = inOrg(orgA, () -> newPosition(deptA, "Sales Manager", "manager")).getId();
        Department deptB = inOrg(orgB, () -> newDepartment("Sales", "SLS"));
        salesDeptB = deptB.getId();
        inOrg(orgB, () -> newPosition(deptB, "Sales Rep", "junior"));
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    @Test
    void linkCodeIsSingleUseAndLinksTheWorkspace() throws Exception {
        String code = inOrg(orgA, () -> crmIntegrationService.createLinkCode("hr@acme.test")).code();

        // Codes are forgiving about case and separators when typed by hand.
        String typed = code.toLowerCase().replace("-", " ");
        mockMvc.perform(signed(post(BASE + "/link/redeem"), Map.of("code", typed, "crmCompanyId", "crm-acme")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.organizationId").value(orgA.toString()))
                .andExpect(jsonPath("$.organizationName").value("Acme " + suffix));

        CrmConnection connection = connectionRepository.findByOrganizationId(orgA).orElseThrow();
        assertTrue(connection.isActive());
        assertEquals("crm-acme", connection.getCrmCompanyId());

        mockMvc.perform(signed(post(BASE + "/link/redeem"), Map.of("code", code, "crmCompanyId", "crm-evil")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").exists());
        assertEquals("crm-acme", connectionRepository.findByOrganizationId(orgA).orElseThrow().getCrmCompanyId());
    }

    @Test
    void onlyTheNewestCodeWorks() throws Exception {
        String first = inOrg(orgA, () -> crmIntegrationService.createLinkCode("hr@acme.test")).code();
        String second = inOrg(orgA, () -> crmIntegrationService.createLinkCode("hr@acme.test")).code();

        mockMvc.perform(signed(post(BASE + "/link/redeem"), Map.of("code", first, "crmCompanyId", "crm-acme")))
                .andExpect(status().isBadRequest());
        mockMvc.perform(signed(post(BASE + "/link/redeem"), Map.of("code", second, "crmCompanyId", "crm-acme")))
                .andExpect(status().isOk());
    }

    @Test
    void expiredCodeIsRejected() throws Exception {
        String code = inOrg(orgA, () -> crmIntegrationService.createLinkCode("hr@acme.test")).code();
        CrmLinkCode row = linkCodeRepository.findAll().stream()
                .filter(c -> c.getOrganizationId().equals(orgA) && c.getUsedAt() == null)
                .findFirst().orElseThrow();
        row.setExpiresAt(LocalDateTime.now().minusMinutes(1));
        linkCodeRepository.save(row);

        mockMvc.perform(signed(post(BASE + "/link/redeem"), Map.of("code", code, "crmCompanyId", "crm-acme")))
                .andExpect(status().isBadRequest());
        assertTrue(connectionRepository.findByOrganizationId(orgA).isEmpty());
    }

    @Test
    void unsignedOrTamperedRequestsAreRejected() throws Exception {
        String body = jsonMapper.writeValueAsString(Map.of("code", "ABCD-EFGH", "crmCompanyId", "crm-acme"));
        mockMvc.perform(post(BASE + "/link/redeem").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isUnauthorized());

        long ts = Instant.now().getEpochSecond();
        String signatureForOtherBody = RequestSigner.sign(SECRET, ts, "POST", BASE + "/link/redeem", "{}");
        mockMvc.perform(post(BASE + "/link/redeem")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body)
                        .header(RequestSigner.TIMESTAMP_HEADER, Long.toString(ts))
                        .header(RequestSigner.SIGNATURE_HEADER, signatureForOtherBody))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(get(BASE + "/organizations/" + orgA + "/employees")
                        .header(RequestSigner.TIMESTAMP_HEADER, Long.toString(ts))
                        .header(RequestSigner.SIGNATURE_HEADER, "v1=deadbeef"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void employeeListOnlyExposesTheLinkedWorkspace() throws Exception {
        link(orgA, "crm-acme");
        String aliceEmail = "alice-" + suffix + "@acme.test";
        String bobEmail = "bob-" + suffix + "@globex.test";
        inOrg(orgA, () -> employeeService.createEmployee(new HrDtos.CreateEmployeeRequest(
                "Alice", "Ng", aliceEmail, salesDeptA, managerPositionA, null, LocalDate.now())));
        inOrg(orgB, () -> employeeService.createEmployee(new HrDtos.CreateEmployeeRequest(
                "Bob", "Lee", bobEmail, salesDeptB, null, null, LocalDate.now())));

        String json = mockMvc.perform(signed(get(BASE + "/organizations/" + orgA + "/employees"), null)
                        .header("X-Novora-Crm-Company", "crm-acme"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        JsonNode employees = jsonMapper.readTree(json).path("employees");
        List<String> emails = employees.valueStream().map(e -> e.path("email").asString()).toList();
        assertTrue(emails.contains(aliceEmail));
        assertFalse(emails.contains(bobEmail), "another workspace's employees must never leak");
        JsonNode alice = employees.valueStream()
                .filter(e -> aliceEmail.equals(e.path("email").asString())).findFirst().orElseThrow();
        assertEquals("SLS", alice.path("departmentCode").asString());
        assertEquals("manager", alice.path("positionLevel").asString());

        // Right workspace, wrong CRM company.
        mockMvc.perform(signed(get(BASE + "/organizations/" + orgA + "/employees"), null)
                        .header("X-Novora-Crm-Company", "crm-other"))
                .andExpect(status().isForbidden());
        // A workspace that was never linked.
        mockMvc.perform(signed(get(BASE + "/organizations/" + orgB + "/employees"), null)
                        .header("X-Novora-Crm-Company", "crm-acme"))
                .andExpect(status().isForbidden());
    }

    @Test
    void employeeChangesAreQueuedOnlyForLinkedWorkspaces() throws Exception {
        inOrg(orgB, () -> employeeService.createEmployee(new HrDtos.CreateEmployeeRequest(
                "Bob", "Lee", "bob-" + suffix + "@globex.test", salesDeptB, null, null, LocalDate.now())));
        assertEquals(0, pending(orgB).size(), "unlinked workspaces must not queue anything");

        link(orgA, "crm-acme");
        String email = "carol-" + suffix + "@acme.test";
        HrDtos.EmployeeResponse carol = inOrg(orgA, () -> employeeService.createEmployee(new HrDtos.CreateEmployeeRequest(
                "Carol", "Tan", email, salesDeptA, managerPositionA, null, LocalDate.now())));

        List<IntegrationOutboxEvent> queued = pending(orgA);
        assertEquals(1, queued.size());
        JsonNode hired = jsonMapper.readTree(queued.get(0).getPayload());
        assertEquals(CrmIntegrationService.EVENT_EMPLOYEE_CHANGED, hired.path("type").asString());
        assertEquals(orgA.toString(), hired.path("organizationId").asString());
        assertEquals(email, hired.path("employee").path("email").asString());
        assertEquals("active", hired.path("employee").path("status").asString());

        inOrg(orgA, () -> {
            employeeService.deleteEmployee(carol.id(), "someone-else@acme.test");
            return null;
        });
        List<IntegrationOutboxEvent> afterTermination = pending(orgA);
        assertEquals(2, afterTermination.size());
        JsonNode terminated = afterTermination.stream()
                .map(e -> jsonMapper.readTree(e.getPayload()))
                .filter(n -> "terminated".equals(n.path("employee").path("status").asString()))
                .findFirst().orElseThrow();
        assertEquals(carol.id().toString(), terminated.path("employee").path("employeeId").asString());
        assertTrue(terminated.path("employee").path("email").isNull(), "tombstoned email must not be sent");
    }

    @Test
    void disconnectStopsDeliveryAndAccess() throws Exception {
        link(orgA, "crm-acme");
        inOrg(orgA, () -> employeeService.createEmployee(new HrDtos.CreateEmployeeRequest(
                "Dan", "Ko", "dan-" + suffix + "@acme.test", salesDeptA, null, null, LocalDate.now())));
        assertEquals(1, pending(orgA).size());

        inOrg(orgA, () -> {
            crmIntegrationService.disconnect();
            return null;
        });

        assertEquals(0, pending(orgA).size());
        assertFalse(connectionRepository.findByOrganizationId(orgA).orElseThrow().isActive());
        mockMvc.perform(signed(get(BASE + "/organizations/" + orgA + "/employees"), null)
                        .header("X-Novora-Crm-Company", "crm-acme"))
                .andExpect(status().isForbidden());
    }

    @Test
    void dispatcherSignsDeliveriesAndRetriesFailures() throws Exception {
        link(orgA, "crm-acme");
        inOrg(orgA, () -> employeeService.createEmployee(new HrDtos.CreateEmployeeRequest(
                "Eve", "Lim", "eve-" + suffix + "@acme.test", salesDeptA, null, null, LocalDate.now())));
        IntegrationOutboxEvent event = pending(orgA).get(0);

        AtomicInteger nextStatus = new AtomicInteger(503);
        AtomicBoolean signatureValid = new AtomicBoolean(false);
        AtomicReference<String> receivedBody = new AtomicReference<>();
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext(CrmOutboxDispatcher.EVENTS_PATH, exchange -> {
            String body = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
            receivedBody.set(body);
            signatureValid.set(RequestSigner.verify(SECRET,
                    exchange.getRequestHeaders().getFirst(RequestSigner.TIMESTAMP_HEADER),
                    exchange.getRequestHeaders().getFirst(RequestSigner.SIGNATURE_HEADER),
                    exchange.getRequestMethod(), exchange.getRequestURI().toString(), body, Instant.now()));
            respond(exchange, nextStatus.get());
        });
        server.start();
        try {
            CrmOutboxDispatcher dispatcher = new CrmOutboxDispatcher(
                    new CrmIntegrationSettings(true, "http://127.0.0.1:" + server.getAddress().getPort(), SECRET),
                    outboxRepository, connectionRepository);

            assertFalse(dispatcher.deliver(event), "a 503 must be retried, not dropped");
            IntegrationOutboxEvent afterFailure = outboxRepository.findById(event.getId()).orElseThrow();
            assertEquals(1, afterFailure.getAttempts());
            assertNull(afterFailure.getDeliveredAt());
            assertTrue(afterFailure.getNextAttemptAt().isAfter(LocalDateTime.now()));
            assertTrue(afterFailure.getLastError().contains("503"));

            nextStatus.set(200);
            assertTrue(dispatcher.deliver(afterFailure));
            assertNotNull(outboxRepository.findById(event.getId()).orElseThrow().getDeliveredAt());
            assertTrue(signatureValid.get(), "the CRM must be able to verify the signature");
            assertEquals(event.getPayload(), receivedBody.get());
        } finally {
            server.stop(0);
        }
    }

    @Test
    void backoffGrowsAndEventuallyParks() {
        assertEquals(1, CrmOutboxDispatcher.nextDelay(1).toMinutes());
        assertEquals(5, CrmOutboxDispatcher.nextDelay(2).toMinutes());
        assertEquals(360, CrmOutboxDispatcher.nextDelay(10).toMinutes());
        assertTrue(CrmOutboxDispatcher.nextDelay(CrmOutboxDispatcher.MAX_ATTEMPTS).toDays() > 365);
    }

    // ------------------------------------------------------------------------------------ helpers

    private MockHttpServletRequestBuilder signed(MockHttpServletRequestBuilder builder, Object body) {
        String json = body == null ? "" : jsonMapper.writeValueAsString(body);
        var request = builder.buildRequest(webApplicationContext.getServletContext());
        long ts = Instant.now().getEpochSecond();
        String sig = RequestSigner.sign(SECRET, ts, request.getMethod(), request.getRequestURI(), json);
        builder.header(RequestSigner.TIMESTAMP_HEADER, Long.toString(ts))
                .header(RequestSigner.SIGNATURE_HEADER, sig);
        if (body != null) {
            builder.contentType(MediaType.APPLICATION_JSON).content(json);
        }
        return builder;
    }

    private void link(UUID orgId, String crmCompanyId) {
        String code = inOrg(orgId, () -> crmIntegrationService.createLinkCode("hr@test")).code();
        CrmIntegrationDtos.RedeemResponse linked = crmIntegrationService.redeem(code, crmCompanyId);
        assertEquals(orgId, linked.organizationId());
    }

    private List<IntegrationOutboxEvent> pending(UUID orgId) {
        return outboxRepository.findAll().stream()
                .filter(e -> e.getOrganizationId().equals(orgId) && e.getDeliveredAt() == null)
                .toList();
    }

    private <T> T inOrg(UUID orgId, Supplier<T> action) {
        TenantContext.set(orgId);
        try {
            return action.get();
        } finally {
            TenantContext.clear();
        }
    }

    private UUID newOrganization(String name) {
        LocalDateTime now = LocalDateTime.now();
        Organization org = new Organization();
        org.setName(name);
        org.setSlug(name.toLowerCase().replaceAll("[^a-z0-9]+", "-"));
        org.setPlan(Organization.Plan.ENTERPRISE);
        org.setStatus(Organization.Status.ACTIVE);
        org.setCreatedAt(now);
        org.setUpdatedAt(now);
        return organizationRepository.save(org).getId();
    }

    private Department newDepartment(String name, String code) {
        LocalDateTime now = LocalDateTime.now();
        Department d = new Department();
        d.setName(name);
        d.setCode(code);
        d.setActive(true);
        d.setCreatedAt(now);
        d.setUpdatedAt(now);
        return departmentRepository.save(d);
    }

    private Position newPosition(Department department, String title, String level) {
        LocalDateTime now = LocalDateTime.now();
        Position p = new Position();
        p.setDepartment(department);
        p.setTitle(title);
        p.setLevel(level);
        p.setActive(true);
        p.setCreatedAt(now);
        p.setUpdatedAt(now);
        return positionRepository.save(p);
    }

    private static void respond(com.sun.net.httpserver.HttpExchange exchange, int status) throws IOException {
        byte[] bytes = "{}".getBytes(StandardCharsets.UTF_8);
        exchange.sendResponseHeaders(status, bytes.length);
        exchange.getResponseBody().write(bytes);
        exchange.close();
    }
}
