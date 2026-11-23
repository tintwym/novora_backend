package prod.tint_wym.novora_backend.controller;

import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import prod.tint_wym.novora_backend.dto.ClaimPolicyDtos;
import prod.tint_wym.novora_backend.service.ClaimPolicyService;

@RestController
@PreAuthorize("isAuthenticated()")
public class ClaimPolicyController {

    private final ClaimPolicyService claimPolicyService;

    public ClaimPolicyController(ClaimPolicyService claimPolicyService) {
        this.claimPolicyService = claimPolicyService;
    }

    @GetMapping("/api/admin/claim-policy")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN','HR_ADMIN','HR_MANAGER')")
    public ClaimPolicyDtos.ClaimPolicyResponse getPolicy() {
        return claimPolicyService.getPolicy();
    }

    @PutMapping("/api/admin/claim-policy")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN','HR_ADMIN','HR_MANAGER')")
    public ClaimPolicyDtos.ClaimPolicyResponse updatePolicy(
            @Valid @RequestBody ClaimPolicyDtos.UpdateClaimPolicyRequest request) {
        return claimPolicyService.updatePolicy(request);
    }
}
