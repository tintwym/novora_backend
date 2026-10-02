package prod.tint_wym.novora_backend.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.server.ResponseStatusException;
import prod.tint_wym.novora_backend.dto.AiDtos;
import prod.tint_wym.novora_backend.entity.Announcement;
import prod.tint_wym.novora_backend.entity.LeaveType;
import prod.tint_wym.novora_backend.repository.AnnouncementRepository;
import prod.tint_wym.novora_backend.repository.HolidayRepository;
import prod.tint_wym.novora_backend.repository.LeaveTypeRepository;
import prod.tint_wym.novora_backend.repository.OtPolicyRepository;
import prod.tint_wym.novora_backend.tenancy.TenantContext;

class PolicyAssistantServiceTest {

    private final LeaveTypeRepository leaveTypes = mock(LeaveTypeRepository.class);
    private final OtPolicyRepository otPolicies = mock(OtPolicyRepository.class);
    private final HolidayRepository holidays = mock(HolidayRepository.class);
    private final AnnouncementRepository announcements = mock(AnnouncementRepository.class);
    private final AiService aiWithoutKey = new AiService(true, "", "", "gemini-2.5-flash");

    @BeforeEach
    void setUp() {
        TenantContext.set(UUID.randomUUID());
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
                "staff@example.com", null, List.of(new SimpleGrantedAuthority("ROLE_EMPLOYEE"))));

        LeaveType annual = new LeaveType();
        annual.setName("Annual Leave");
        annual.setCode("AL");
        annual.setDaysAllowed(14);
        annual.setPaid(true);
        annual.setCarryForward(true);
        annual.setMaxCarryDays(5);
        annual.setActive(true);
        when(leaveTypes.findAllByActiveTrueOrderBySortOrderAscNameAsc()).thenReturn(List.of(annual));
        when(otPolicies.findAllByOrderByNameAsc()).thenReturn(List.of());
        when(holidays.findAllByOrderByHolidayDateAsc()).thenReturn(List.of());

        Announcement hrOnly = new Announcement();
        hrOnly.setTitle("Salary review budget");
        hrOnly.setContent("Confidential annual leave encashment budget for HR.");
        hrOnly.setTargetRole("HR_ADMIN");
        hrOnly.setPublishedAt(LocalDateTime.now().minusDays(1));
        when(announcements.findAllByOrderByCreatedAtDesc()).thenReturn(List.of(hrOnly));
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
        SecurityContextHolder.clearContext();
    }

    private PolicyAssistantService service(int maxPerHour) {
        return new PolicyAssistantService(aiWithoutKey, leaveTypes, otPolicies, holidays, announcements, maxPerHour);
    }

    @Test
    void answersFromCompanySettingsWithoutAiKey() {
        AiDtos.PolicyQaResponse reply = service(20).ask("How many annual leave days do I get?");

        assertEquals("heuristic", reply.source());
        assertTrue(reply.answer().contains("14 days"), reply.answer());
        assertTrue(reply.answer().contains("carry forward up to 5"), reply.answer());
    }

    @Test
    void hidesAnnouncementsTargetedAtOtherRoles() {
        AiDtos.PolicyQaResponse reply = service(20).ask("annual leave encashment budget");

        assertFalse(reply.answer().contains("Confidential"), reply.answer());
    }

    @Test
    void suggestsTicketWhenNothingMatches() {
        AiDtos.PolicyQaResponse reply = service(20).ask("parking permit");

        assertTrue(reply.answer().contains("helpdesk ticket"), reply.answer());
        assertTrue(reply.sources().isEmpty());
    }

    @Test
    void enforcesHourlyLimitPerUser() {
        PolicyAssistantService svc = service(2);
        svc.ask("annual leave");
        svc.ask("annual leave");

        ResponseStatusException ex = assertThrows(ResponseStatusException.class, () -> svc.ask("annual leave"));
        assertEquals(HttpStatus.TOO_MANY_REQUESTS, ex.getStatusCode());
    }

    @Test
    void requiresOrganizationContext() {
        TenantContext.clear();

        ResponseStatusException ex = assertThrows(ResponseStatusException.class, () -> service(20).ask("leave"));
        assertEquals(HttpStatus.FORBIDDEN, ex.getStatusCode());
    }
}
