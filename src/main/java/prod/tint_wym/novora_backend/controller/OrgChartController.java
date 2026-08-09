package prod.tint_wym.novora_backend.controller;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import prod.tint_wym.novora_backend.dto.MyProfileDtos;
import prod.tint_wym.novora_backend.service.MyProfileService;

@RestController
@PreAuthorize("isAuthenticated()")
public class OrgChartController {

    private final MyProfileService myProfileService;

    public OrgChartController(MyProfileService myProfileService) {
        this.myProfileService = myProfileService;
    }

    @GetMapping("/api/org-chart")
    public MyProfileDtos.OrgChartResponse orgChart() {
        return myProfileService.orgChart();
    }
}

