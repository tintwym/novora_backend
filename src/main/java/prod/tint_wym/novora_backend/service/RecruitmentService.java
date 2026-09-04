package prod.tint_wym.novora_backend.service;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import prod.tint_wym.novora_backend.dto.RecruitmentDtos;
import prod.tint_wym.novora_backend.entity.Candidate;
import prod.tint_wym.novora_backend.entity.Department;
import prod.tint_wym.novora_backend.entity.Employee;
import prod.tint_wym.novora_backend.entity.Interview;
import prod.tint_wym.novora_backend.entity.JobOffer;
import prod.tint_wym.novora_backend.entity.JobPosting;
import prod.tint_wym.novora_backend.entity.Position;
import prod.tint_wym.novora_backend.repository.AppUserRepository;
import prod.tint_wym.novora_backend.repository.CandidateRepository;
import prod.tint_wym.novora_backend.repository.DepartmentRepository;
import prod.tint_wym.novora_backend.repository.EmployeeRepository;
import prod.tint_wym.novora_backend.repository.InterviewRepository;
import prod.tint_wym.novora_backend.repository.JobOfferRepository;
import prod.tint_wym.novora_backend.repository.JobPostingRepository;
import prod.tint_wym.novora_backend.repository.PositionRepository;
import prod.tint_wym.novora_backend.tenancy.TenantContext;

@Service
@Transactional(readOnly = true)
public class RecruitmentService {

    private static final ZoneId ZONE = ZoneId.systemDefault();
    private static final Set<String> JOB_STATUSES = Set.of("draft", "open", "closed", "on_hold", "filled");
    private static final Set<String> CANDIDATE_STAGES = Set.of(
            "applied", "screening", "interview", "technical", "hr_interview", "offer", "hired", "rejected");
    private static final Set<String> CANDIDATE_STATUSES = Set.of("active", "withdrawn", "rejected", "hired");
    private static final Set<String> SOURCES = Set.of(
            "website", "linkedin", "referral", "agency", "walk_in", "other");
    private static final Set<String> OFFER_STATUSES = Set.of("draft", "sent", "accepted", "declined");
    private static final Set<String> INTERVIEW_MODES = Set.of("in_person", "video", "phone");
    private static final Set<String> INTERVIEW_ROUNDS = Set.of("screening", "technical", "hr", "final", "other");

    private final JobPostingRepository jobPostingRepository;
    private final CandidateRepository candidateRepository;
    private final DepartmentRepository departmentRepository;
    private final PositionRepository positionRepository;
    private final AppUserRepository appUserRepository;
    private final InterviewRepository interviewRepository;
    private final JobOfferRepository jobOfferRepository;
    private final EmployeeRepository employeeRepository;

    public RecruitmentService(
            JobPostingRepository jobPostingRepository,
            CandidateRepository candidateRepository,
            DepartmentRepository departmentRepository,
            PositionRepository positionRepository,
            AppUserRepository appUserRepository,
            InterviewRepository interviewRepository,
            JobOfferRepository jobOfferRepository,
            EmployeeRepository employeeRepository) {
        this.jobPostingRepository = jobPostingRepository;
        this.candidateRepository = candidateRepository;
        this.departmentRepository = departmentRepository;
        this.positionRepository = positionRepository;
        this.appUserRepository = appUserRepository;
        this.interviewRepository = interviewRepository;
        this.jobOfferRepository = jobOfferRepository;
        this.employeeRepository = employeeRepository;
    }

    private UUID requireOrganizationId() {
        UUID orgId = TenantContext.get();
        if (orgId == null) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "No organization context");
        }
        return orgId;
    }

    private static Instant toInstant(LocalDateTime t) {
        return t == null ? null : t.atZone(ZONE).toInstant();
    }

    private RecruitmentDtos.JobPostingResponse toJob(JobPosting j) {
        String dept = null;
        if (j.getPosition() != null && j.getPosition().getDepartment() != null) {
            dept = j.getPosition().getDepartment().getName();
        }
        long applicants = candidateRepository.countByJobPosting_Id(j.getId());
        return new RecruitmentDtos.JobPostingResponse(
                j.getId(),
                j.getTitle(),
                dept,
                j.getLocation(),
                j.getEmploymentType(),
                j.getSalaryMin(),
                j.getSalaryMax(),
                j.getOpenDate(),
                j.getCloseDate(),
                j.getOpenings(),
                j.isPublished(),
                j.getStatus(),
                applicants,
                toInstant(j.getCreatedAt()));
    }

    private RecruitmentDtos.CandidateResponse toCandidate(Candidate c) {
        JobPosting job = c.getJobPosting();
        return new RecruitmentDtos.CandidateResponse(
                c.getId(),
                job != null ? job.getId() : null,
                job != null ? job.getTitle() : null,
                c.getFullName(),
                c.getEmail(),
                c.getPhone(),
                c.getSource(),
                c.getStage(),
                c.getStatus(),
                c.getRating(),
                c.getNotes(),
                toInstant(c.getAppliedAt()));
    }

    public List<RecruitmentDtos.JobPostingResponse> listJobs() {
        return jobPostingRepository.findAllByOrderByCreatedAtDesc().stream()
                .map(this::toJob)
                .toList();
    }

    @Transactional
    public RecruitmentDtos.JobPostingResponse createJob(String email, RecruitmentDtos.CreateJobPostingRequest request) {
        UUID orgId = requireOrganizationId();
        LocalDateTime now = LocalDateTime.now();

        Department department = resolveDepartment(request.departmentName(), orgId);
        Position position = positionRepository
                .findFirstByDepartment_IdAndTitleIgnoreCase(department.getId(), request.title().trim())
                .orElseGet(() -> {
                    Position p = new Position();
                    p.setOrganizationId(orgId);
                    p.setTitle(request.title().trim());
                    p.setDepartment(department);
                    p.setActive(true);
                    p.setCreatedAt(now);
                    p.setUpdatedAt(now);
                    return positionRepository.save(p);
                });

        JobPosting job = new JobPosting();
        job.setOrganizationId(orgId);
        job.setPosition(position);
        job.setTitle(request.title().trim());
        job.setDescription(blankToNull(request.description()));
        job.setLocation(blankToNull(request.location()));
        job.setEmploymentType(blankToNull(request.employmentType()));
        job.setSalaryMin(request.salaryMin());
        job.setSalaryMax(request.salaryMax());
        job.setOpenDate(request.openDate() != null ? request.openDate() : LocalDate.now());
        job.setCloseDate(request.closeDate());
        job.setOpenings(request.openings() != null && request.openings() > 0 ? request.openings() : 1);
        job.setPublished(request.publish());
        job.setStatus(request.publish() ? "open" : "draft");
        appUserRepository.findByEmail(email.trim().toLowerCase(Locale.US)).ifPresent(job::setCreatedBy);
        job.setCreatedAt(now);
        job.setUpdatedAt(now);
        return toJob(jobPostingRepository.save(job));
    }

    @Transactional
    public RecruitmentDtos.JobPostingResponse updateJobStatus(UUID jobId, RecruitmentDtos.UpdateJobStatusRequest request) {
        JobPosting job = jobPostingRepository
                .findById(jobId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Job posting not found"));
        String status = request.status().trim().toLowerCase(Locale.US);
        if (!JOB_STATUSES.contains(status)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid job status");
        }
        job.setStatus(status);
        job.setPublished("open".equals(status));
        job.setUpdatedAt(LocalDateTime.now());
        return toJob(jobPostingRepository.save(job));
    }

    public List<RecruitmentDtos.CandidateResponse> listCandidates(UUID jobPostingId) {
        List<Candidate> rows = jobPostingId == null
                ? candidateRepository.findAllByOrderByAppliedAtDesc()
                : candidateRepository.findAllByJobPosting_IdOrderByAppliedAtDesc(jobPostingId);
        return rows.stream().map(this::toCandidate).toList();
    }

    @Transactional
    public RecruitmentDtos.CandidateResponse createCandidate(RecruitmentDtos.CreateCandidateRequest request) {
        if (request.jobPostingId() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "jobPostingId is required");
        }
        JobPosting job = jobPostingRepository
                .findById(request.jobPostingId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Job posting not found"));

        LocalDateTime now = LocalDateTime.now();
        Candidate c = new Candidate();
        c.setOrganizationId(requireOrganizationId());
        c.setJobPosting(job);
        c.setFullName(request.fullName().trim());
        c.setEmail(request.email().trim().toLowerCase(Locale.US));
        c.setPhone(blankToNull(request.phone()));
        c.setSource(normalizeSource(request.source()));
        c.setNotes(blankToNull(request.notes()));
        c.setStage("applied");
        c.setStatus("active");
        c.setAppliedAt(now);
        c.setUpdatedAt(now);
        return toCandidate(candidateRepository.save(c));
    }

    @Transactional
    public RecruitmentDtos.CandidateResponse updateCandidateStage(
            UUID candidateId, RecruitmentDtos.UpdateCandidateStageRequest request) {
        Candidate c = candidateRepository
                .findById(candidateId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Candidate not found"));
        String stage = request.stage().trim().toLowerCase(Locale.US).replace(' ', '_');
        if (!CANDIDATE_STAGES.contains(stage)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid candidate stage");
        }
        c.setStage(stage);
        if (request.status() != null && !request.status().isBlank()) {
            String status = request.status().trim().toLowerCase(Locale.US);
            if (!CANDIDATE_STATUSES.contains(status)) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid candidate status");
            }
            c.setStatus(status);
        } else if ("hired".equals(stage)) {
            c.setStatus("hired");
        } else if ("rejected".equals(stage)) {
            c.setStatus("rejected");
        }
        c.setUpdatedAt(LocalDateTime.now());
        return toCandidate(candidateRepository.save(c));
    }

    public List<RecruitmentDtos.InterviewResponse> listInterviews() {
        return interviewRepository.findAllByOrderByScheduledAtDesc().stream()
                .map(this::toInterview)
                .toList();
    }

    @Transactional
    public RecruitmentDtos.InterviewResponse createInterview(RecruitmentDtos.CreateInterviewRequest request) {
        UUID orgId = requireOrganizationId();
        Candidate candidate = candidateRepository
                .findById(request.candidateId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Candidate not found"));

        Employee interviewer;
        if (request.interviewerEmployeeId() != null) {
            interviewer = employeeRepository
                    .findByIdAndOrganizationId(request.interviewerEmployeeId(), orgId)
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Interviewer not found"));
        } else {
            interviewer = employeeRepository.findAllByStatusNotIgnoreCase("terminated").stream()
                    .findFirst()
                    .orElseThrow(() -> new ResponseStatusException(
                            HttpStatus.BAD_REQUEST, "No employee available to assign as interviewer"));
        }

        String mode = blankToNull(request.mode());
        if (mode != null) {
            mode = mode.trim().toLowerCase(Locale.US).replace('-', '_').replace(' ', '_');
            if (!INTERVIEW_MODES.contains(mode)) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid interview mode");
            }
        }
        String round = blankToNull(request.round());
        if (round != null) {
            round = round.trim().toLowerCase(Locale.US).replace('-', '_').replace(' ', '_');
            if (!INTERVIEW_ROUNDS.contains(round)) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid interview round");
            }
        }

        LocalDateTime now = LocalDateTime.now();
        Interview interview = new Interview();
        interview.setOrganizationId(orgId);
        interview.setCandidate(candidate);
        interview.setInterviewer(interviewer);
        interview.setScheduledAt(LocalDateTime.ofInstant(request.scheduledAt(), ZONE));
        interview.setDurationMins(request.durationMins() != null && request.durationMins() > 0
                ? request.durationMins()
                : 60);
        interview.setMode(mode);
        interview.setLocation(blankToNull(request.location()));
        interview.setRound(round);
        interview.setStatus("scheduled");
        interview.setCreatedAt(now);
        interview.setUpdatedAt(now);
        return toInterview(interviewRepository.save(interview));
    }

    public List<RecruitmentDtos.JobOfferResponse> listOffers() {
        return jobOfferRepository.findAllByOrderByCreatedAtDesc().stream()
                .map(this::toOffer)
                .toList();
    }

    @Transactional
    public RecruitmentDtos.JobOfferResponse createOffer(RecruitmentDtos.CreateJobOfferRequest request) {
        UUID orgId = requireOrganizationId();
        Candidate candidate = candidateRepository
                .findById(request.candidateId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Candidate not found"));

        String status = request.status() == null || request.status().isBlank()
                ? "draft"
                : request.status().trim().toLowerCase(Locale.US);
        if (!status.equals("draft") && !status.equals("sent")) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "status must be draft or sent");
        }

        LocalDateTime now = LocalDateTime.now();
        JobOffer offer = new JobOffer();
        offer.setOrganizationId(orgId);
        offer.setCandidate(candidate);
        offer.setSalary(request.salary());
        offer.setCurrency("SGD");
        offer.setAllowance(request.allowance());
        offer.setGrade(blankToNull(request.grade()));
        offer.setProbation(blankToNull(request.probation()));
        offer.setStatus(status);
        if ("sent".equals(status)) {
            offer.setSentAt(now);
        }
        offer.setExpiryDate(request.expiryDate());
        offer.setNotes(blankToNull(request.notes()));
        offer.setCreatedAt(now);
        offer.setUpdatedAt(now);
        return toOffer(jobOfferRepository.save(offer));
    }

    @Transactional
    public RecruitmentDtos.JobOfferResponse updateOfferStatus(
            UUID offerId, RecruitmentDtos.UpdateOfferStatusRequest request) {
        UUID orgId = requireOrganizationId();
        JobOffer offer = jobOfferRepository
                .findByIdAndOrganizationId(offerId, orgId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Offer not found"));
        String status = request.status().trim().toLowerCase(Locale.US);
        if (!OFFER_STATUSES.contains(status)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid offer status");
        }
        offer.setStatus(status);
        if ("sent".equals(status) && offer.getSentAt() == null) {
            offer.setSentAt(LocalDateTime.now());
        }
        offer.setUpdatedAt(LocalDateTime.now());
        return toOffer(jobOfferRepository.save(offer));
    }

    private RecruitmentDtos.InterviewResponse toInterview(Interview i) {
        Candidate c = i.getCandidate();
        Employee interviewer = i.getInterviewer();
        return new RecruitmentDtos.InterviewResponse(
                i.getId(),
                c.getId(),
                c.getFullName(),
                interviewer.getId(),
                (interviewer.getFirstName() + " " + interviewer.getLastName()).trim(),
                toInstant(i.getScheduledAt()),
                i.getDurationMins(),
                i.getMode(),
                i.getLocation(),
                i.getRound(),
                i.getStatus(),
                toInstant(i.getCreatedAt()));
    }

    private RecruitmentDtos.JobOfferResponse toOffer(JobOffer o) {
        Candidate c = o.getCandidate();
        return new RecruitmentDtos.JobOfferResponse(
                o.getId(),
                c.getId(),
                c.getFullName(),
                o.getSalary(),
                o.getCurrency(),
                o.getAllowance(),
                o.getGrade(),
                o.getProbation(),
                o.getStatus(),
                toInstant(o.getSentAt()),
                o.getExpiryDate(),
                o.getNotes(),
                toInstant(o.getCreatedAt()));
    }

    private Department resolveDepartment(String departmentName, UUID orgId) {
        if (departmentName != null && !departmentName.isBlank()) {
            return departmentRepository
                    .findByNameIgnoreCase(departmentName.trim())
                    .orElseGet(() -> createDepartment(departmentName.trim(), orgId));
        }
        return departmentRepository.findAll().stream()
                .findFirst()
                .orElseGet(() -> createDepartment("General", orgId));
    }

    private Department createDepartment(String name, UUID orgId) {
        LocalDateTime now = LocalDateTime.now();
        Department d = new Department();
        d.setOrganizationId(orgId);
        d.setName(name);
        String code = name.replaceAll("[^A-Za-z0-9]", "").toUpperCase(Locale.US);
        if (code.length() > 8) {
            code = code.substring(0, 8);
        }
        if (code.isBlank()) {
            code = "DEPT";
        }
        d.setCode(code);
        d.setActive(true);
        d.setCreatedAt(now);
        d.setUpdatedAt(now);
        return departmentRepository.save(d);
    }

    private static String normalizeSource(String source) {
        if (source == null || source.isBlank()) {
            return "other";
        }
        String normalized = source.trim().toLowerCase(Locale.US).replace('-', '_').replace(' ', '_');
        if ("jobstreet".equals(normalized) || "indeed".equals(normalized) || "internal".equals(normalized)) {
            return "website";
        }
        if (SOURCES.contains(normalized)) {
            return normalized;
        }
        if (normalized.contains("linkedin")) {
            return "linkedin";
        }
        if (normalized.contains("refer")) {
            return "referral";
        }
        if (normalized.contains("agency")) {
            return "agency";
        }
        return "other";
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
