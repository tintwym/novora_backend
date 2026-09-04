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
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import prod.tint_wym.novora_backend.dto.RecruitmentDtos;
import prod.tint_wym.novora_backend.service.RecruitmentService;

@RestController
@PreAuthorize("hasAnyRole('SUPER_ADMIN','HR_ADMIN','HR_MANAGER')")
public class RecruitmentController {

    private final RecruitmentService recruitmentService;

    public RecruitmentController(RecruitmentService recruitmentService) {
        this.recruitmentService = recruitmentService;
    }

    @GetMapping("/api/admin/recruitment/jobs")
    public List<RecruitmentDtos.JobPostingResponse> listJobs() {
        return recruitmentService.listJobs();
    }

    @PostMapping("/api/admin/recruitment/jobs")
    @ResponseStatus(HttpStatus.CREATED)
    public RecruitmentDtos.JobPostingResponse createJob(
            Authentication auth, @Valid @RequestBody RecruitmentDtos.CreateJobPostingRequest request) {
        return recruitmentService.createJob(auth.getName(), request);
    }

    @PostMapping("/api/admin/recruitment/jobs/{jobId}/status")
    public RecruitmentDtos.JobPostingResponse updateJobStatus(
            @PathVariable UUID jobId, @Valid @RequestBody RecruitmentDtos.UpdateJobStatusRequest request) {
        return recruitmentService.updateJobStatus(jobId, request);
    }

    @GetMapping("/api/admin/recruitment/candidates")
    public List<RecruitmentDtos.CandidateResponse> listCandidates(
            @RequestParam(value = "jobId", required = false) UUID jobId) {
        return recruitmentService.listCandidates(jobId);
    }

    @PostMapping("/api/admin/recruitment/candidates")
    @ResponseStatus(HttpStatus.CREATED)
    public RecruitmentDtos.CandidateResponse createCandidate(
            @Valid @RequestBody RecruitmentDtos.CreateCandidateRequest request) {
        return recruitmentService.createCandidate(request);
    }

    @PostMapping("/api/admin/recruitment/candidates/{candidateId}/stage")
    public RecruitmentDtos.CandidateResponse updateCandidateStage(
            @PathVariable UUID candidateId,
            @Valid @RequestBody RecruitmentDtos.UpdateCandidateStageRequest request) {
        return recruitmentService.updateCandidateStage(candidateId, request);
    }

    @GetMapping("/api/admin/recruitment/interviews")
    public List<RecruitmentDtos.InterviewResponse> listInterviews() {
        return recruitmentService.listInterviews();
    }

    @PostMapping("/api/admin/recruitment/interviews")
    @ResponseStatus(HttpStatus.CREATED)
    public RecruitmentDtos.InterviewResponse createInterview(
            @Valid @RequestBody RecruitmentDtos.CreateInterviewRequest request) {
        return recruitmentService.createInterview(request);
    }

    @GetMapping("/api/admin/recruitment/offers")
    public List<RecruitmentDtos.JobOfferResponse> listOffers() {
        return recruitmentService.listOffers();
    }

    @PostMapping("/api/admin/recruitment/offers")
    @ResponseStatus(HttpStatus.CREATED)
    public RecruitmentDtos.JobOfferResponse createOffer(
            @Valid @RequestBody RecruitmentDtos.CreateJobOfferRequest request) {
        return recruitmentService.createOffer(request);
    }

    @PostMapping("/api/admin/recruitment/offers/{offerId}/status")
    public RecruitmentDtos.JobOfferResponse updateOfferStatus(
            @PathVariable UUID offerId, @Valid @RequestBody RecruitmentDtos.UpdateOfferStatusRequest request) {
        return recruitmentService.updateOfferStatus(offerId, request);
    }
}
