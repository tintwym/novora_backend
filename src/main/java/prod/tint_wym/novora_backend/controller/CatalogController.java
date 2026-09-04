package prod.tint_wym.novora_backend.controller;

import jakarta.validation.Valid;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import prod.tint_wym.novora_backend.dto.CatalogDtos;
import prod.tint_wym.novora_backend.dto.WorkDtos;
import prod.tint_wym.novora_backend.service.CatalogService;

@RestController
@PreAuthorize("hasAnyRole('SUPER_ADMIN','HR_ADMIN','HR_MANAGER')")
public class CatalogController {

    private final CatalogService catalogService;

    public CatalogController(CatalogService catalogService) {
        this.catalogService = catalogService;
    }

    @GetMapping("/api/admin/allowance-types")
    public List<CatalogDtos.AllowanceTypeResponse> listAllowanceTypes() {
        return catalogService.listAllowanceTypes();
    }

    @PostMapping("/api/admin/allowance-types")
    @ResponseStatus(HttpStatus.CREATED)
    public CatalogDtos.AllowanceTypeResponse createAllowanceType(
            @Valid @RequestBody CatalogDtos.CreateAllowanceTypeRequest request) {
        return catalogService.createAllowanceType(request);
    }

    @GetMapping("/api/admin/shift-patterns")
    public List<CatalogDtos.ShiftPatternResponse> listShiftPatterns() {
        return catalogService.listShiftPatterns();
    }

    @PostMapping("/api/admin/shift-patterns")
    @ResponseStatus(HttpStatus.CREATED)
    public CatalogDtos.ShiftPatternResponse createShiftPattern(
            @Valid @RequestBody CatalogDtos.CreateShiftPatternRequest request) {
        return catalogService.createShiftPattern(request);
    }

    @GetMapping("/api/admin/roster")
    public List<CatalogDtos.RosterEntryResponse> listRoster(
            @RequestParam(value = "from", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
                    LocalDate from,
            @RequestParam(value = "to", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
                    LocalDate to) {
        return catalogService.listRoster(from, to);
    }

    @PostMapping("/api/admin/roster")
    @ResponseStatus(HttpStatus.CREATED)
    public CatalogDtos.RosterEntryResponse createRoster(
            @Valid @RequestBody CatalogDtos.CreateRosterEntryRequest request) {
        return catalogService.createRoster(request);
    }

    @GetMapping("/api/admin/attendance/roster")
    public List<WorkDtos.AttendanceLogResponse> attendanceRoster() {
        return catalogService.listAttendanceRoster();
    }

    @GetMapping("/api/admin/positions")
    public List<CatalogDtos.PositionResponse> listPositions() {
        return catalogService.listPositions();
    }

    @PostMapping("/api/admin/positions")
    @ResponseStatus(HttpStatus.CREATED)
    public CatalogDtos.PositionResponse createPosition(
            @Valid @RequestBody CatalogDtos.CreatePositionRequest request) {
        return catalogService.createPosition(request);
    }

    @GetMapping("/api/admin/organization")
    public CatalogDtos.OrganizationProfileResponse getOrganization() {
        return catalogService.getOrganization();
    }

    @PutMapping("/api/admin/organization")
    public CatalogDtos.OrganizationProfileResponse updateOrganization(
            @Valid @RequestBody CatalogDtos.UpdateOrganizationProfileRequest request) {
        return catalogService.updateOrganization(request);
    }

    @GetMapping("/api/admin/branches")
    public List<CatalogDtos.BranchResponse> listBranches() {
        return catalogService.listBranches();
    }

    @PostMapping("/api/admin/branches")
    @ResponseStatus(HttpStatus.CREATED)
    public CatalogDtos.BranchResponse createBranch(@Valid @RequestBody CatalogDtos.CreateBranchRequest request) {
        return catalogService.createBranch(request);
    }

    @PutMapping("/api/admin/branches/{id}")
    public CatalogDtos.BranchResponse updateBranch(
            @PathVariable UUID id, @Valid @RequestBody CatalogDtos.UpdateBranchRequest request) {
        return catalogService.updateBranch(id, request);
    }

    @GetMapping("/api/admin/assets")
    public List<CatalogDtos.AssetResponse> listAssets() {
        return catalogService.listAssets();
    }

    @PostMapping("/api/admin/assets")
    @ResponseStatus(HttpStatus.CREATED)
    public CatalogDtos.AssetResponse createAsset(@Valid @RequestBody CatalogDtos.CreateAssetRequest request) {
        return catalogService.createAsset(request);
    }

    @GetMapping("/api/admin/trainings")
    public List<CatalogDtos.TrainingResponse> listTrainings() {
        return catalogService.listTrainings();
    }

    @PostMapping("/api/admin/trainings")
    @ResponseStatus(HttpStatus.CREATED)
    public CatalogDtos.TrainingResponse createTraining(
            @Valid @RequestBody CatalogDtos.CreateTrainingRequest request) {
        return catalogService.createTraining(request);
    }

    @GetMapping("/api/admin/performance-reviews")
    public List<CatalogDtos.PerformanceReviewResponse> listPerformanceReviews() {
        return catalogService.listPerformanceReviews();
    }

    @PostMapping("/api/admin/performance-reviews")
    @ResponseStatus(HttpStatus.CREATED)
    public CatalogDtos.PerformanceReviewResponse createPerformanceReview(
            Authentication auth, @Valid @RequestBody CatalogDtos.CreatePerformanceReviewRequest request) {
        return catalogService.createPerformanceReview(auth.getName(), request);
    }

    @GetMapping("/api/admin/audit-logs")
    public List<CatalogDtos.AuditLogResponse> listAuditLogs() {
        return catalogService.listAuditLogs();
    }

    @GetMapping("/api/admin/reports/summary")
    public CatalogDtos.ReportSummaryResponse reportSummary() {
        return catalogService.reportSummary();
    }
}
