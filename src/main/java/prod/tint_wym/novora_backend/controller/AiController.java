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
}
