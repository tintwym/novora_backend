package prod.tint_wym.novora_backend.service;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import prod.tint_wym.novora_backend.dto.OpsDtos;
import prod.tint_wym.novora_backend.dto.WorkDtos;
import prod.tint_wym.novora_backend.entity.BenefitEnrollment;
import prod.tint_wym.novora_backend.entity.BenefitPlan;
import prod.tint_wym.novora_backend.entity.DisciplinaryCase;
import prod.tint_wym.novora_backend.entity.Employee;
import prod.tint_wym.novora_backend.entity.HelpdeskReply;
import prod.tint_wym.novora_backend.entity.HelpdeskTicket;
import prod.tint_wym.novora_backend.entity.OnboardingTask;
import prod.tint_wym.novora_backend.entity.Training;
import prod.tint_wym.novora_backend.entity.TrainingEnrollment;
import prod.tint_wym.novora_backend.repository.BenefitEnrollmentRepository;
import prod.tint_wym.novora_backend.repository.BenefitPlanRepository;
import prod.tint_wym.novora_backend.repository.DisciplinaryCaseRepository;
import prod.tint_wym.novora_backend.repository.EmployeeRepository;
import prod.tint_wym.novora_backend.repository.HelpdeskReplyRepository;
import prod.tint_wym.novora_backend.repository.HelpdeskTicketRepository;
import prod.tint_wym.novora_backend.repository.OnboardingTaskRepository;
import prod.tint_wym.novora_backend.repository.TrainingEnrollmentRepository;
import prod.tint_wym.novora_backend.repository.TrainingRepository;
import prod.tint_wym.novora_backend.tenancy.TenantContext;

@Service
@Transactional(readOnly = true)
public class OpsService {

    private static final ZoneId ZONE = ZoneId.systemDefault();

    private final HelpdeskTicketRepository helpdeskTicketRepository;
    private final HelpdeskReplyRepository helpdeskReplyRepository;
    private final DisciplinaryCaseRepository disciplinaryCaseRepository;
    private final BenefitPlanRepository benefitPlanRepository;
    private final BenefitEnrollmentRepository benefitEnrollmentRepository;
    private final OnboardingTaskRepository onboardingTaskRepository;
    private final TrainingRepository trainingRepository;
    private final TrainingEnrollmentRepository trainingEnrollmentRepository;
    private final EmployeeRepository employeeRepository;

    public OpsService(
            HelpdeskTicketRepository helpdeskTicketRepository,
            HelpdeskReplyRepository helpdeskReplyRepository,
            DisciplinaryCaseRepository disciplinaryCaseRepository,
            BenefitPlanRepository benefitPlanRepository,
            BenefitEnrollmentRepository benefitEnrollmentRepository,
            OnboardingTaskRepository onboardingTaskRepository,
            TrainingRepository trainingRepository,
            TrainingEnrollmentRepository trainingEnrollmentRepository,
            EmployeeRepository employeeRepository) {
        this.helpdeskTicketRepository = helpdeskTicketRepository;
        this.helpdeskReplyRepository = helpdeskReplyRepository;
        this.disciplinaryCaseRepository = disciplinaryCaseRepository;
        this.benefitPlanRepository = benefitPlanRepository;
        this.benefitEnrollmentRepository = benefitEnrollmentRepository;
        this.onboardingTaskRepository = onboardingTaskRepository;
        this.trainingRepository = trainingRepository;
        this.trainingEnrollmentRepository = trainingEnrollmentRepository;
        this.employeeRepository = employeeRepository;
    }

    private UUID requireOrganizationId() {
        UUID orgId = TenantContext.get();
        if (orgId == null) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "No organization context");
        }
        return orgId;
    }

    private Employee employeeForEmail(String email) {
        String normalized = email == null ? "" : email.trim().toLowerCase(Locale.US);
        return employeeRepository
                .findByAppUser_EmailIgnoreCase(normalized)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Employee not found for user"));
    }

    private Employee requireEmployeeInOrg(UUID employeeId) {
        UUID orgId = requireOrganizationId();
        return employeeRepository
                .findByIdAndOrganizationId(employeeId, orgId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Employee not found"));
    }

    private static String name(Employee e) {
        return e == null ? null : (e.getFirstName() + " " + e.getLastName()).trim();
    }

    private static Instant toInstant(LocalDateTime t) {
        return t == null ? null : t.atZone(ZONE).toInstant();
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    // ---- Helpdesk ----

    public List<OpsDtos.HelpdeskTicketResponse> listAdminTickets() {
        return helpdeskTicketRepository.findAllByOrderByCreatedAtDesc().stream()
                .map(t -> toTicket(t, true))
                .toList();
    }

    public List<OpsDtos.HelpdeskTicketResponse> myTickets(String email) {
        Employee e = employeeForEmail(email);
        return helpdeskTicketRepository.findAllByRequester_IdOrderByCreatedAtDesc(e.getId()).stream()
                .map(t -> toTicket(t, true))
                .toList();
    }

    @Transactional
    public OpsDtos.HelpdeskTicketResponse createTicket(String email, OpsDtos.CreateHelpdeskTicketRequest request) {
        UUID orgId = requireOrganizationId();
        LocalDateTime now = LocalDateTime.now();
        HelpdeskTicket t = new HelpdeskTicket();
        t.setOrganizationId(orgId);
        t.setSubject(request.subject().trim());
        t.setDescription(blankToNull(request.description()));
        t.setCategory(blankToNull(request.category()));
        t.setPriority(blankToNull(request.priority()));
        t.setStatus(
                request.status() == null || request.status().isBlank()
                        ? "open"
                        : request.status().trim().toLowerCase(Locale.US));
        if (request.requesterEmployeeId() != null) {
            t.setRequester(requireEmployeeInOrg(request.requesterEmployeeId()));
        } else {
            t.setRequester(employeeForEmail(email));
        }
        if (request.assigneeEmployeeId() != null) {
            t.setAssignee(requireEmployeeInOrg(request.assigneeEmployeeId()));
        }
        t.setCreatedAt(now);
        t.setUpdatedAt(now);
        return toTicket(helpdeskTicketRepository.save(t), false);
    }

    @Transactional
    public OpsDtos.HelpdeskReplyResponse addReply(
            String adminEmail, UUID ticketId, OpsDtos.CreateHelpdeskReplyRequest request) {
        UUID orgId = requireOrganizationId();
        HelpdeskTicket ticket = helpdeskTicketRepository
                .findByIdAndOrganizationId(ticketId, orgId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Ticket not found"));
        Employee author = employeeRepository
                .findByAppUser_EmailIgnoreCase(adminEmail.trim().toLowerCase(Locale.US))
                .orElse(null);
        HelpdeskReply reply = new HelpdeskReply();
        reply.setTicket(ticket);
        reply.setAuthor(author);
        reply.setBody(request.body().trim());
        reply.setCreatedAt(LocalDateTime.now());
        HelpdeskReply saved = helpdeskReplyRepository.save(reply);
        ticket.setUpdatedAt(LocalDateTime.now());
        helpdeskTicketRepository.save(ticket);
        return toReply(saved);
    }

    private OpsDtos.HelpdeskTicketResponse toTicket(HelpdeskTicket t, boolean includeReplies) {
        List<OpsDtos.HelpdeskReplyResponse> replies =
                includeReplies
                        ? helpdeskReplyRepository.findAllByTicket_IdOrderByCreatedAtAsc(t.getId()).stream()
                                .map(this::toReply)
                                .toList()
                        : List.of();
        Employee requester = t.getRequester();
        Employee assignee = t.getAssignee();
        return new OpsDtos.HelpdeskTicketResponse(
                t.getId(),
                t.getSubject(),
                t.getDescription(),
                t.getCategory(),
                t.getPriority(),
                t.getStatus(),
                requester != null ? requester.getId() : null,
                name(requester),
                assignee != null ? assignee.getId() : null,
                name(assignee),
                toInstant(t.getCreatedAt()),
                toInstant(t.getUpdatedAt()),
                replies);
    }

    private OpsDtos.HelpdeskReplyResponse toReply(HelpdeskReply r) {
        Employee author = r.getAuthor();
        return new OpsDtos.HelpdeskReplyResponse(
                r.getId(),
                author != null ? author.getId() : null,
                name(author),
                r.getBody(),
                toInstant(r.getCreatedAt()));
    }

    // ---- Disciplinary ----

    public List<OpsDtos.DisciplinaryCaseResponse> listDisciplinaryCases() {
        return disciplinaryCaseRepository.findAllByOrderByCreatedAtDesc().stream()
                .map(this::toDisciplinary)
                .toList();
    }

    @Transactional
    public OpsDtos.DisciplinaryCaseResponse createDisciplinaryCase(OpsDtos.CreateDisciplinaryCaseRequest request) {
        UUID orgId = requireOrganizationId();
        Employee employee = requireEmployeeInOrg(request.employeeId());
        LocalDateTime now = LocalDateTime.now();
        DisciplinaryCase c = new DisciplinaryCase();
        c.setOrganizationId(orgId);
        c.setEmployee(employee);
        c.setReason(request.reason().trim());
        c.setActionType(blankToNull(request.actionType()));
        c.setSeverity(blankToNull(request.severity()));
        c.setStatus(
                request.status() == null || request.status().isBlank()
                        ? "open"
                        : request.status().trim().toLowerCase(Locale.US));
        c.setNotes(blankToNull(request.notes()));
        c.setIncidentDate(request.incidentDate());
        c.setCreatedAt(now);
        c.setUpdatedAt(now);
        return toDisciplinary(disciplinaryCaseRepository.save(c));
    }

    private OpsDtos.DisciplinaryCaseResponse toDisciplinary(DisciplinaryCase c) {
        return new OpsDtos.DisciplinaryCaseResponse(
                c.getId(),
                c.getEmployee().getId(),
                name(c.getEmployee()),
                c.getReason(),
                c.getActionType(),
                c.getSeverity(),
                c.getStatus(),
                c.getNotes(),
                c.getIncidentDate(),
                toInstant(c.getCreatedAt()));
    }

    // ---- Benefits ----

    public List<OpsDtos.BenefitPlanResponse> listBenefitPlans() {
        return benefitPlanRepository.findAllByOrderByNameAsc().stream().map(this::toPlan).toList();
    }

    @Transactional
    public OpsDtos.BenefitPlanResponse createBenefitPlan(OpsDtos.CreateBenefitPlanRequest request) {
        UUID orgId = requireOrganizationId();
        BenefitPlan p = new BenefitPlan();
        p.setOrganizationId(orgId);
        p.setName(request.name().trim());
        p.setCategory(blankToNull(request.category()));
        p.setProvider(blankToNull(request.provider()));
        p.setCoverageSummary(blankToNull(request.coverageSummary()));
        p.setEmployeeCost(request.employeeCost());
        p.setEmployerCost(request.employerCost());
        p.setStatus(
                request.status() == null || request.status().isBlank()
                        ? "active"
                        : request.status().trim().toLowerCase(Locale.US));
        p.setCreatedAt(LocalDateTime.now());
        return toPlan(benefitPlanRepository.save(p));
    }

    private OpsDtos.BenefitPlanResponse toPlan(BenefitPlan p) {
        return new OpsDtos.BenefitPlanResponse(
                p.getId(),
                p.getName(),
                p.getCategory(),
                p.getProvider(),
                p.getCoverageSummary(),
                p.getEmployeeCost(),
                p.getEmployerCost(),
                p.getStatus(),
                toInstant(p.getCreatedAt()));
    }

    public List<OpsDtos.BenefitEnrollmentResponse> listBenefitEnrollments() {
        return benefitEnrollmentRepository.findAllByOrderByEnrolledAtDesc().stream()
                .map(this::toEnrollment)
                .toList();
    }

    public List<OpsDtos.BenefitEnrollmentResponse> myBenefitEnrollments(String email) {
        Employee e = employeeForEmail(email);
        return benefitEnrollmentRepository.findAllByEmployee_IdOrderByEnrolledAtDesc(e.getId()).stream()
                .map(this::toEnrollment)
                .toList();
    }

    @Transactional
    public OpsDtos.BenefitEnrollmentResponse createBenefitEnrollment(OpsDtos.CreateBenefitEnrollmentRequest request) {
        UUID orgId = requireOrganizationId();
        BenefitPlan plan = benefitPlanRepository
                .findByIdAndOrganizationId(request.planId(), orgId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Benefit plan not found"));
        Employee employee = requireEmployeeInOrg(request.employeeId());
        BenefitEnrollment en = new BenefitEnrollment();
        en.setOrganizationId(orgId);
        en.setPlan(plan);
        en.setEmployee(employee);
        en.setStatus(
                request.status() == null || request.status().isBlank()
                        ? "enrolled"
                        : request.status().trim().toLowerCase(Locale.US));
        en.setEnrolledAt(LocalDateTime.now());
        en.setNotes(blankToNull(request.notes()));
        return toEnrollment(benefitEnrollmentRepository.save(en));
    }

    private OpsDtos.BenefitEnrollmentResponse toEnrollment(BenefitEnrollment en) {
        return new OpsDtos.BenefitEnrollmentResponse(
                en.getId(),
                en.getPlan().getId(),
                en.getPlan().getName(),
                en.getEmployee().getId(),
                name(en.getEmployee()),
                en.getStatus(),
                toInstant(en.getEnrolledAt()),
                en.getNotes());
    }

    // ---- Onboarding (admin) ----

    public List<OpsDtos.AdminOnboardingTaskResponse> listOnboardingTasks() {
        return onboardingTaskRepository.findAllByOrderByCreatedAtDesc().stream()
                .map(this::toAdminOnboarding)
                .toList();
    }

    @Transactional
    public OpsDtos.AdminOnboardingTaskResponse createOnboardingTask(OpsDtos.CreateOnboardingTaskRequest request) {
        UUID orgId = requireOrganizationId();
        Employee employee = requireEmployeeInOrg(request.employeeId());
        OnboardingTask task = new OnboardingTask();
        task.setOrganizationId(orgId);
        task.setEmployee(employee);
        task.setTitle(request.title().trim());
        task.setDescription(blankToNull(request.description()));
        task.setDueDate(request.dueDate());
        task.setStatus("pending");
        task.setSortOrder(request.sortOrder() != null ? request.sortOrder() : 0);
        task.setCreatedAt(LocalDateTime.now());
        return toAdminOnboarding(onboardingTaskRepository.save(task));
    }

    private OpsDtos.AdminOnboardingTaskResponse toAdminOnboarding(OnboardingTask t) {
        return new OpsDtos.AdminOnboardingTaskResponse(
                t.getId(),
                t.getEmployee().getId(),
                name(t.getEmployee()),
                t.getTitle(),
                t.getDescription(),
                t.getDueDate(),
                t.getStatus(),
                t.getSortOrder(),
                toInstant(t.getCompletedAt()),
                toInstant(t.getCreatedAt()));
    }

    /** Shared mapper for employee self-service onboarding responses. */
    public static WorkDtos.OnboardingTaskResponse toMyOnboarding(OnboardingTask t) {
        boolean completed = "completed".equalsIgnoreCase(t.getStatus()) || t.getCompletedAt() != null;
        return new WorkDtos.OnboardingTaskResponse(t.getId(), t.getTitle(), t.getDueDate(), completed);
    }

    // ---- Training enrollments ----

    public List<OpsDtos.TrainingEnrollmentResponse> listTrainingEnrollments(UUID trainingId) {
        requireOrganizationId();
        Training training = trainingRepository
                .findById(trainingId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Training not found"));
        return trainingEnrollmentRepository.findAllByTraining_Id(training.getId()).stream()
                .map(this::toTrainingEnrollment)
                .toList();
    }

    @Transactional
    public OpsDtos.TrainingEnrollmentResponse enrollInTraining(
            UUID trainingId, OpsDtos.CreateTrainingEnrollmentRequest request) {
        requireOrganizationId();
        Training training = trainingRepository
                .findById(trainingId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Training not found"));
        Employee employee = requireEmployeeInOrg(request.employeeId());
        TrainingEnrollment en = new TrainingEnrollment();
        en.setTraining(training);
        en.setEmployee(employee);
        en.setStatus("enrolled");
        en.setEnrolledAt(LocalDateTime.now());
        return toTrainingEnrollment(trainingEnrollmentRepository.save(en));
    }

    @Transactional
    public OpsDtos.TrainingEnrollmentResponse completeTrainingEnrollment(
            UUID trainingId, UUID enrollmentId, OpsDtos.CompleteTrainingEnrollmentRequest request) {
        requireOrganizationId();
        TrainingEnrollment en = trainingEnrollmentRepository
                .findByIdAndTraining_Id(enrollmentId, trainingId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Enrollment not found"));
        en.setStatus("completed");
        en.setCompletedAt(LocalDateTime.now());
        if (request != null) {
            if (request.score() != null) {
                en.setScore(request.score());
            }
            if (request.feedback() != null && !request.feedback().isBlank()) {
                en.setFeedback(request.feedback().trim());
            }
        }
        return toTrainingEnrollment(trainingEnrollmentRepository.save(en));
    }

    private OpsDtos.TrainingEnrollmentResponse toTrainingEnrollment(TrainingEnrollment en) {
        return new OpsDtos.TrainingEnrollmentResponse(
                en.getId(),
                en.getTraining().getId(),
                en.getTraining().getTitle(),
                en.getEmployee().getId(),
                name(en.getEmployee()),
                en.getStatus(),
                toInstant(en.getEnrolledAt()),
                toInstant(en.getCompletedAt()),
                en.getScore());
    }
}
