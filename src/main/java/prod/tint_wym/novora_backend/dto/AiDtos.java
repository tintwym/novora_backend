package prod.tint_wym.novora_backend.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.List;

public final class AiDtos {

    private AiDtos() {}

    public record DashboardInsightRequest(
            List<KpiHint> kpis,
            Double attendanceRate,
            Integer openRoles,
            Integer pendingLeave,
            Integer upcomingInterviews,
            Integer onboardingIncomplete
    ) {}

    public record KpiHint(
            @Size(max = 80) String label,
            @Size(max = 40) String value,
            @Size(max = 40) String delta
    ) {}

    public record DashboardInsightResponse(
            List<String> insights,
            String source,
            String disclaimer
    ) {}

    public record HelpdeskDraftRequest(
            @NotBlank @Size(max = 200) String subject,
            @Size(max = 4000) String description,
            @Size(max = 80) String category,
            @Size(max = 40) String priority,
            @Size(max = 120) String requesterName,
            List<@Size(max = 1000) String> recentReplies
    ) {}

    public record HelpdeskDraftResponse(
            String draft,
            String source,
            String disclaimer
    ) {}

    public record RecruitmentJdDraftRequest(
            @NotBlank @Size(max = 200) String title,
            @Size(max = 120) String department,
            @Size(max = 80) String employmentType,
            @Size(max = 120) String location,
            @Size(max = 80) String experience,
            @Size(max = 120) String education,
            @Size(max = 500) String skills,
            @Size(max = 2000) String existingResponsibilities,
            @Size(max = 1000) String niceToHave,
            @Size(max = 40) String salaryMin,
            @Size(max = 40) String salaryMax
    ) {}

    public record RecruitmentJdDraftResponse(
            String draft,
            String source,
            String disclaimer
    ) {}

    public record CandidateSummaryRequest(
            @NotBlank @Size(max = 120) String fullName,
            @Size(max = 200) String jobTitle,
            @Size(max = 80) String stage,
            @Size(max = 80) String source,
            @Size(max = 2000) String notes,
            @Size(max = 40) String rating,
            @Size(max = 120) String email,
            @Size(max = 40) String phone
    ) {}

    public record CandidateSummaryResponse(
            String summary,
            List<String> strengths,
            List<String> risks,
            List<String> interviewQuestions,
            String source,
            String disclaimer
    ) {}

    public record PerformanceReviewDraftRequest(
            @NotBlank @Size(max = 120) String employeeName,
            @Size(max = 80) String reviewType,
            @Size(max = 80) String reviewPeriod,
            @Size(max = 40) String reviewDate,
            @Size(max = 40) String codeQuality,
            @Size(max = 40) String problemSolving,
            @Size(max = 40) String systemDesign,
            @Size(max = 40) String sprintsCompleted,
            @Size(max = 40) String bugsSla,
            @Size(max = 40) String attendance,
            @Size(max = 2000) String existingNote
    ) {}

    public record PerformanceReviewDraftResponse(
            String draft,
            String source,
            String disclaimer
    ) {}

    public record CourseRecommendationRequest(
            @Size(max = 120) String department,
            @Size(max = 120) String roleOrFocus,
            @Size(max = 500) String skillsGap,
            List<@Size(max = 200) String> catalogTitles,
            List<@Size(max = 80) String> categories
    ) {}

    public record CourseRecommendationResponse(
            List<String> recommendations,
            String rationale,
            String source,
            String disclaimer
    ) {}

    public record EngagementThemeRequest(
            List<EngagementComment> comments
    ) {}

    public record EngagementComment(
            @Size(max = 80) String category,
            @NotBlank @Size(max = 2000) String text,
            @Size(max = 40) String vibe
    ) {}

    public record EngagementThemeResponse(
            List<String> themes,
            String summary,
            List<String> suggestedActions,
            String source,
            String disclaimer
    ) {}

    public record DisciplinaryLetterRequest(
            @NotBlank @Size(max = 120) String employeeName,
            @Size(max = 120) String department,
            @Size(max = 200) String reason,
            @Size(max = 40) String warningLevel,
            @Size(max = 40) String incidentDate,
            @Size(max = 120) String location,
            @Size(max = 3000) String description,
            @Size(max = 1000) String existingExpectation,
            @Size(max = 120) String issuedBy
    ) {}

    public record DisciplinaryLetterResponse(
            String letter,
            String chronology,
            String source,
            String disclaimer
    ) {}
}
