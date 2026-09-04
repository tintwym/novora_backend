package prod.tint_wym.novora_backend.controller;

import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import prod.tint_wym.novora_backend.dto.OpsDtos;
import prod.tint_wym.novora_backend.service.OpsService;

@RestController
@PreAuthorize("isAuthenticated()")
public class OpsController {

    private final OpsService opsService;

    public OpsController(OpsService opsService) {
        this.opsService = opsService;
    }

    // ---- Helpdesk ----

    @GetMapping("/api/admin/helpdesk/tickets")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN','HR_ADMIN','HR_MANAGER')")
    public List<OpsDtos.HelpdeskTicketResponse> listAdminTickets() {
        return opsService.listAdminTickets();
    }

    @PostMapping("/api/admin/helpdesk/tickets")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN','HR_ADMIN','HR_MANAGER')")
    @ResponseStatus(HttpStatus.CREATED)
    public OpsDtos.HelpdeskTicketResponse createAdminTicket(
            Authentication auth, @Valid @RequestBody OpsDtos.CreateHelpdeskTicketRequest request) {
        return opsService.createTicket(auth.getName(), request);
    }

    @GetMapping("/api/my/helpdesk/tickets")
    public List<OpsDtos.HelpdeskTicketResponse> myTickets(Authentication auth) {
        return opsService.myTickets(auth.getName());
    }

    @PostMapping("/api/my/helpdesk/tickets")
    @ResponseStatus(HttpStatus.CREATED)
    public OpsDtos.HelpdeskTicketResponse createMyTicket(
            Authentication auth, @Valid @RequestBody OpsDtos.CreateHelpdeskTicketRequest request) {
        return opsService.createTicket(auth.getName(), request);
    }

    @PostMapping("/api/admin/helpdesk/tickets/{id}/replies")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN','HR_ADMIN','HR_MANAGER')")
    @ResponseStatus(HttpStatus.CREATED)
    public OpsDtos.HelpdeskReplyResponse addReply(
            Authentication auth,
            @PathVariable UUID id,
            @Valid @RequestBody OpsDtos.CreateHelpdeskReplyRequest request) {
        return opsService.addReply(auth.getName(), id, request);
    }

    // ---- Disciplinary ----

    @GetMapping("/api/admin/disciplinary/cases")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN','HR_ADMIN','HR_MANAGER')")
    public List<OpsDtos.DisciplinaryCaseResponse> listDisciplinaryCases() {
        return opsService.listDisciplinaryCases();
    }

    @PostMapping("/api/admin/disciplinary/cases")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN','HR_ADMIN','HR_MANAGER')")
    @ResponseStatus(HttpStatus.CREATED)
    public OpsDtos.DisciplinaryCaseResponse createDisciplinaryCase(
            @Valid @RequestBody OpsDtos.CreateDisciplinaryCaseRequest request) {
        return opsService.createDisciplinaryCase(request);
    }

    // ---- Benefits ----

    @GetMapping("/api/admin/benefit-plans")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN','HR_ADMIN','HR_MANAGER')")
    public List<OpsDtos.BenefitPlanResponse> listBenefitPlans() {
        return opsService.listBenefitPlans();
    }

    @PostMapping("/api/admin/benefit-plans")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN','HR_ADMIN','HR_MANAGER')")
    @ResponseStatus(HttpStatus.CREATED)
    public OpsDtos.BenefitPlanResponse createBenefitPlan(
            @Valid @RequestBody OpsDtos.CreateBenefitPlanRequest request) {
        return opsService.createBenefitPlan(request);
    }

    @GetMapping("/api/admin/benefit-enrollments")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN','HR_ADMIN','HR_MANAGER')")
    public List<OpsDtos.BenefitEnrollmentResponse> listBenefitEnrollments() {
        return opsService.listBenefitEnrollments();
    }

    @PostMapping("/api/admin/benefit-enrollments")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN','HR_ADMIN','HR_MANAGER')")
    @ResponseStatus(HttpStatus.CREATED)
    public OpsDtos.BenefitEnrollmentResponse createBenefitEnrollment(
            @Valid @RequestBody OpsDtos.CreateBenefitEnrollmentRequest request) {
        return opsService.createBenefitEnrollment(request);
    }

    @GetMapping("/api/my/benefit-enrollments")
    public List<OpsDtos.BenefitEnrollmentResponse> myBenefitEnrollments(Authentication auth) {
        return opsService.myBenefitEnrollments(auth.getName());
    }

    // ---- Onboarding ----

    @GetMapping("/api/admin/onboarding/tasks")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN','HR_ADMIN','HR_MANAGER')")
    public List<OpsDtos.AdminOnboardingTaskResponse> listOnboardingTasks() {
        return opsService.listOnboardingTasks();
    }

    @PostMapping("/api/admin/onboarding/tasks")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN','HR_ADMIN','HR_MANAGER')")
    @ResponseStatus(HttpStatus.CREATED)
    public OpsDtos.AdminOnboardingTaskResponse createOnboardingTask(
            @Valid @RequestBody OpsDtos.CreateOnboardingTaskRequest request) {
        return opsService.createOnboardingTask(request);
    }

    // ---- Training enrollments ----

    @GetMapping("/api/admin/trainings/{id}/enrollments")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN','HR_ADMIN','HR_MANAGER')")
    public List<OpsDtos.TrainingEnrollmentResponse> listTrainingEnrollments(@PathVariable UUID id) {
        return opsService.listTrainingEnrollments(id);
    }

    @PostMapping("/api/admin/trainings/{id}/enrollments")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN','HR_ADMIN','HR_MANAGER')")
    @ResponseStatus(HttpStatus.CREATED)
    public OpsDtos.TrainingEnrollmentResponse enrollInTraining(
            @PathVariable UUID id, @Valid @RequestBody OpsDtos.CreateTrainingEnrollmentRequest request) {
        return opsService.enrollInTraining(id, request);
    }

    @PostMapping("/api/admin/trainings/{id}/enrollments/{enrollmentId}/complete")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN','HR_ADMIN','HR_MANAGER')")
    public OpsDtos.TrainingEnrollmentResponse completeTrainingEnrollment(
            @PathVariable UUID id,
            @PathVariable UUID enrollmentId,
            @RequestBody(required = false) OpsDtos.CompleteTrainingEnrollmentRequest request) {
        return opsService.completeTrainingEnrollment(id, enrollmentId, request);
    }
}
