package prod.tint_wym.novora_backend.controller;

import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import prod.tint_wym.novora_backend.dto.AiDtos;
import prod.tint_wym.novora_backend.service.AiService;

@RestController
@PreAuthorize("hasAnyRole('SUPER_ADMIN','HR_ADMIN','HR_MANAGER')")
public class AiController {

    private final AiService aiService;

    public AiController(AiService aiService) {
        this.aiService = aiService;
    }

    @PostMapping("/api/admin/ai/dashboard-insights")
    public AiDtos.DashboardInsightResponse dashboardInsights(
            @RequestBody(required = false) AiDtos.DashboardInsightRequest request) {
        return aiService.dashboardInsights(
                request == null
                        ? new AiDtos.DashboardInsightRequest(null, null, null, null, null, null)
                        : request);
    }

    @PostMapping("/api/admin/ai/helpdesk-draft")
    public AiDtos.HelpdeskDraftResponse helpdeskDraft(@Valid @RequestBody AiDtos.HelpdeskDraftRequest request) {
        return aiService.helpdeskDraft(request);
    }

    @PostMapping("/api/admin/ai/recruitment-jd-draft")
    public AiDtos.RecruitmentJdDraftResponse recruitmentJdDraft(
            @Valid @RequestBody AiDtos.RecruitmentJdDraftRequest request) {
        return aiService.recruitmentJdDraft(request);
    }

    @PostMapping("/api/admin/ai/candidate-summary")
    public AiDtos.CandidateSummaryResponse candidateSummary(
            @Valid @RequestBody AiDtos.CandidateSummaryRequest request) {
        return aiService.candidateSummary(request);
    }

    @PostMapping("/api/admin/ai/performance-review-draft")
    public AiDtos.PerformanceReviewDraftResponse performanceReviewDraft(
            @Valid @RequestBody AiDtos.PerformanceReviewDraftRequest request) {
        return aiService.performanceReviewDraft(request);
    }

    @PostMapping("/api/admin/ai/course-recommendations")
    public AiDtos.CourseRecommendationResponse courseRecommendations(
            @Valid @RequestBody AiDtos.CourseRecommendationRequest request) {
        return aiService.courseRecommendations(request);
    }

    @PostMapping("/api/admin/ai/engagement-themes")
    public AiDtos.EngagementThemeResponse engagementThemes(
            @Valid @RequestBody AiDtos.EngagementThemeRequest request) {
        return aiService.engagementThemes(request);
    }

    @PostMapping("/api/admin/ai/disciplinary-letter")
    public AiDtos.DisciplinaryLetterResponse disciplinaryLetter(
            @Valid @RequestBody AiDtos.DisciplinaryLetterRequest request) {
        return aiService.disciplinaryLetter(request);
    }

    @PostMapping("/api/admin/ai/payroll-anomalies")
    public AiDtos.PayrollAnomalyResponse payrollAnomalies(
            @RequestBody(required = false) AiDtos.PayrollAnomalyRequest request) {
        return aiService.payrollAnomalies(
                request == null
                        ? new AiDtos.PayrollAnomalyRequest(null, null, null, null, null, null, null, null, null)
                        : request);
    }

    @PostMapping("/api/admin/ai/benefits-tip")
    public AiDtos.BenefitsTipResponse benefitsTip(@Valid @RequestBody AiDtos.BenefitsTipRequest request) {
        return aiService.benefitsTip(request);
    }

    @PostMapping("/api/admin/ai/assets-insights")
    public AiDtos.AssetsInsightResponse assetsInsights(
            @RequestBody(required = false) AiDtos.AssetsInsightRequest request) {
        return aiService.assetsInsights(
                request == null
                        ? new AiDtos.AssetsInsightRequest(null, null, null, null, null)
                        : request);
    }

    @PostMapping("/api/admin/ai/claims-review")
    public AiDtos.ClaimsReviewResponse claimsReview(
            @RequestBody(required = false) AiDtos.ClaimsReviewRequest request) {
        return aiService.claimsReview(
                request == null ? new AiDtos.ClaimsReviewRequest(null, null, null, null) : request);
    }

    @PostMapping("/api/admin/ai/leave-review")
    public AiDtos.LeaveReviewResponse leaveReview(
            @RequestBody(required = false) AiDtos.LeaveReviewRequest request) {
        return aiService.leaveReview(
                request == null ? new AiDtos.LeaveReviewRequest(null, null, null) : request);
    }

    @PostMapping("/api/admin/ai/approval-note")
    public AiDtos.ApprovalNoteResponse approvalNote(@Valid @RequestBody AiDtos.ApprovalNoteRequest request) {
        return aiService.approvalNote(request);
    }

    @PostMapping("/api/admin/ai/attendance-review")
    public AiDtos.AttendanceReviewResponse attendanceReview(
            @RequestBody(required = false) AiDtos.AttendanceReviewRequest request) {
        return aiService.attendanceReview(
                request == null
                        ? new AiDtos.AttendanceReviewRequest(null, null, null, null, null, null)
                        : request);
    }

    @PostMapping("/api/admin/ai/reports-brief")
    public AiDtos.ReportsBriefResponse reportsBrief(
            @RequestBody(required = false) AiDtos.ReportsBriefRequest request) {
        return aiService.reportsBrief(
                request == null
                        ? new AiDtos.ReportsBriefRequest(null, null, null, null, null, null, null)
                        : request);
    }

    @PostMapping("/api/admin/ai/onboarding-assist")
    public AiDtos.OnboardingAssistResponse onboardingAssist(
            @Valid @RequestBody AiDtos.OnboardingAssistRequest request) {
        return aiService.onboardingAssist(request);
    }
}
