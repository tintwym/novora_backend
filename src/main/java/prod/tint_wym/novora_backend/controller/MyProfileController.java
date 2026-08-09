package prod.tint_wym.novora_backend.controller;

import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import prod.tint_wym.novora_backend.dto.MyProfileDtos;
import prod.tint_wym.novora_backend.service.MyProfileService;

@RestController
@PreAuthorize("isAuthenticated()")
public class MyProfileController {

    private final MyProfileService myProfileService;

    public MyProfileController(MyProfileService myProfileService) {
        this.myProfileService = myProfileService;
    }

    @GetMapping("/api/my/profile")
    public MyProfileDtos.MyProfileResponse myProfile(Authentication authentication) {
        return myProfileService.getMyProfile(authentication.getName());
    }

    /** HR-only full profile edit for the signed-in admin's own employee row. Prefer `/api/admin/employees/{id}` for other people. */
    @PutMapping("/api/my/profile")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN','HR_ADMIN','HR_MANAGER')")
    public MyProfileDtos.MyProfileResponse updateMyProfile(
            Authentication authentication,
            @Valid @RequestBody MyProfileDtos.UpdateMyProfileRequest request
    ) {
        return myProfileService.updateMyProfile(authentication.getName(), request);
    }

    @PostMapping("/api/my/profile/personal/otp")
    public MyProfileDtos.RequestPersonalOtpResponse requestPersonalOtp(Authentication authentication) {
        return myProfileService.requestPersonalOtp(authentication.getName());
    }

    @PutMapping("/api/my/profile/personal")
    public MyProfileDtos.MyProfileResponse updatePersonal(
            Authentication authentication,
            @Valid @RequestBody MyProfileDtos.UpdatePersonalRequest request
    ) {
        return myProfileService.updatePersonalWithOtp(authentication.getName(), request);
    }

    @GetMapping("/api/my/family")
    public List<MyProfileDtos.FamilyResponse> myFamily(Authentication authentication) {
        return myProfileService.listMyFamily(authentication.getName());
    }

    @PostMapping("/api/my/family")
    public MyProfileDtos.FamilyResponse createMyFamily(
            Authentication authentication,
            @Valid @RequestBody MyProfileDtos.CreateFamilyRequest request
    ) {
        return myProfileService.createMyFamily(authentication.getName(), request);
    }

    @PutMapping("/api/my/family/{familyId}")
    public MyProfileDtos.FamilyResponse updateMyFamily(
            Authentication authentication,
            @PathVariable UUID familyId,
            @Valid @RequestBody MyProfileDtos.UpdateFamilyRequest request
    ) {
        return myProfileService.updateMyFamily(authentication.getName(), familyId, request);
    }

    @DeleteMapping("/api/my/family/{familyId}")
    public void deleteMyFamily(Authentication authentication, @PathVariable UUID familyId) {
        myProfileService.deleteMyFamily(authentication.getName(), familyId);
    }

    @GetMapping("/api/my/education")
    public List<MyProfileDtos.EducationResponse> myEducation(Authentication authentication) {
        return myProfileService.listMyEducation(authentication.getName());
    }

    @PostMapping("/api/my/education")
    public MyProfileDtos.EducationResponse createMyEducation(
            Authentication authentication,
            @Valid @RequestBody MyProfileDtos.CreateEducationRequest request
    ) {
        return myProfileService.createMyEducation(authentication.getName(), request);
    }

    @PutMapping("/api/my/education/{educationId}")
    public MyProfileDtos.EducationResponse updateMyEducation(
            Authentication authentication,
            @PathVariable UUID educationId,
            @Valid @RequestBody MyProfileDtos.UpdateEducationRequest request
    ) {
        return myProfileService.updateMyEducation(authentication.getName(), educationId, request);
    }

    @DeleteMapping("/api/my/education/{educationId}")
    public void deleteMyEducation(Authentication authentication, @PathVariable UUID educationId) {
        myProfileService.deleteMyEducation(authentication.getName(), educationId);
    }
}

