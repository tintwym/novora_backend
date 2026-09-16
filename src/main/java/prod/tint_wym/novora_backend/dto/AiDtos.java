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
}
