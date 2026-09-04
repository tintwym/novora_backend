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
import prod.tint_wym.novora_backend.dto.ClaimDtos;
import prod.tint_wym.novora_backend.service.ClaimService;

@RestController
@PreAuthorize("isAuthenticated()")
public class ClaimController {

    private final ClaimService claimService;

    public ClaimController(ClaimService claimService) {
        this.claimService = claimService;
    }

    @GetMapping("/api/my/claims")
    public List<ClaimDtos.ClaimResponse> myClaims(Authentication auth) {
        return claimService.myClaims(auth.getName());
    }

    @PostMapping("/api/my/claims")
    @ResponseStatus(HttpStatus.CREATED)
    public ClaimDtos.ClaimResponse createMyClaim(
            Authentication auth, @Valid @RequestBody ClaimDtos.CreateClaimRequest request) {
        return claimService.createMyClaim(auth.getName(), request);
    }

    @GetMapping("/api/admin/claims")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN','HR_ADMIN','HR_MANAGER')")
    public List<ClaimDtos.ClaimResponse> listClaims(
            @RequestParam(value = "status", required = false) String status) {
        if (status != null && status.equalsIgnoreCase("pending")) {
            return claimService.listPendingClaims();
        }
        return claimService.listAllClaims();
    }

    @PostMapping("/api/admin/claims/{claimId}/decide")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN','HR_ADMIN','HR_MANAGER')")
    public ClaimDtos.ClaimResponse decideClaim(
            Authentication auth,
            @PathVariable UUID claimId,
            @Valid @RequestBody ClaimDtos.DecideClaimRequest request) {
        return claimService.decideClaim(auth.getName(), claimId, request);
    }
}
