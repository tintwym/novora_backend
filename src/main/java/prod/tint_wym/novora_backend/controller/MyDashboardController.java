package prod.tint_wym.novora_backend.controller;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import prod.tint_wym.novora_backend.dto.MyDashboardDtos;
import prod.tint_wym.novora_backend.service.MyDashboardService;

@RestController
@PreAuthorize("isAuthenticated()")
public class MyDashboardController {

    private final MyDashboardService myDashboardService;

    public MyDashboardController(MyDashboardService myDashboardService) {
        this.myDashboardService = myDashboardService;
    }

    @GetMapping("/api/my/dashboard")
    public MyDashboardDtos.MyDashboardResponse myDashboard(Authentication auth) {
        return myDashboardService.myDashboard(auth.getName());
    }
}

