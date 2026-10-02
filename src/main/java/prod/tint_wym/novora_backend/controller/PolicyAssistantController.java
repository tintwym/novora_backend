package prod.tint_wym.novora_backend.controller;

import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import prod.tint_wym.novora_backend.dto.AiDtos;
import prod.tint_wym.novora_backend.service.PolicyAssistantService;

/** Self-service policy Q&A for every signed-in employee (not admin-only like {@link AiController}). */
@RestController
public class PolicyAssistantController {

    private final PolicyAssistantService policyAssistantService;

    public PolicyAssistantController(PolicyAssistantService policyAssistantService) {
        this.policyAssistantService = policyAssistantService;
    }

    @PostMapping("/api/ai/policy-qa")
    public AiDtos.PolicyQaResponse ask(@Valid @RequestBody AiDtos.PolicyQaRequest request) {
        return policyAssistantService.ask(request.question());
    }
}
