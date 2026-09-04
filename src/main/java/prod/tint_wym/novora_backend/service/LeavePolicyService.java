package prod.tint_wym.novora_backend.service;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import prod.tint_wym.novora_backend.dto.LeavePolicyDtos;
import prod.tint_wym.novora_backend.entity.Holiday;
import prod.tint_wym.novora_backend.entity.LeaveType;
import prod.tint_wym.novora_backend.repository.HolidayRepository;
import prod.tint_wym.novora_backend.repository.LeaveTypeRepository;
import prod.tint_wym.novora_backend.tenancy.TenantContext;

@Service
@Transactional(readOnly = true)
public class LeavePolicyService {

    private static final ZoneId ZONE = ZoneId.systemDefault();

    private final LeaveTypeRepository leaveTypeRepository;
    private final HolidayRepository holidayRepository;

    public LeavePolicyService(LeaveTypeRepository leaveTypeRepository, HolidayRepository holidayRepository) {
        this.leaveTypeRepository = leaveTypeRepository;
        this.holidayRepository = holidayRepository;
    }

    private UUID requireOrganizationId() {
        UUID orgId = TenantContext.get();
        if (orgId == null) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "No organization context");
        }
        return orgId;
    }

    private static Instant toInstant(LocalDateTime t) {
        return t == null ? null : t.atZone(ZONE).toInstant();
    }

    private LeavePolicyDtos.LeaveTypeResponse toLeaveType(LeaveType lt) {
        return new LeavePolicyDtos.LeaveTypeResponse(
                lt.getId(),
                lt.getName(),
                lt.getCode(),
                lt.getDaysAllowed(),
                lt.isPaid(),
                lt.isCarryForward(),
                lt.getMaxCarryDays(),
                lt.getDescription(),
                lt.isActive(),
                toInstant(lt.getCreatedAt()));
    }

    private LeavePolicyDtos.HolidayResponse toHoliday(Holiday h) {
        return new LeavePolicyDtos.HolidayResponse(
                h.getId(),
                h.getName(),
                h.getHolidayDate(),
                h.getType(),
                h.getDescription(),
                toInstant(h.getCreatedAt()));
    }

    public List<LeavePolicyDtos.LeaveTypeResponse> listLeaveTypes(boolean activeOnly) {
        List<LeaveType> rows = activeOnly
                ? leaveTypeRepository.findAllByActiveTrueOrderBySortOrderAscNameAsc()
                : leaveTypeRepository.findAllByOrderBySortOrderAscNameAsc();
        return rows.stream().map(this::toLeaveType).toList();
    }

    @Transactional
    public LeavePolicyDtos.LeaveTypeResponse createLeaveType(LeavePolicyDtos.CreateLeaveTypeRequest request) {
        UUID orgId = requireOrganizationId();
        LocalDateTime now = LocalDateTime.now();
        LeaveType lt = new LeaveType();
        lt.setOrganizationId(orgId);
        lt.setName(request.name().trim());
        lt.setCode(request.code().trim().toUpperCase(Locale.US));
        lt.setLabel(request.name().trim());
        lt.setDaysAllowed(request.daysAllowed() != null ? request.daysAllowed() : 0);
        lt.setCompanyPoolDays(lt.getDaysAllowed());
        lt.setPaid(request.paid() == null || request.paid());
        lt.setCarryForward(Boolean.TRUE.equals(request.carryForward()));
        lt.setMaxCarryDays(request.maxCarryDays() != null ? request.maxCarryDays() : 0);
        lt.setDescription(blankToNull(request.description()));
        lt.setActive(request.active() == null || request.active());
        lt.setSortOrder(0);
        lt.setCreatedAt(now);
        return toLeaveType(leaveTypeRepository.save(lt));
    }

    @Transactional
    public LeavePolicyDtos.LeaveTypeResponse updateLeaveType(UUID id, LeavePolicyDtos.UpdateLeaveTypeRequest request) {
        LeaveType lt = leaveTypeRepository
                .findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Leave type not found"));
        lt.setName(request.name().trim());
        lt.setCode(request.code().trim().toUpperCase(Locale.US));
        lt.setLabel(request.name().trim());
        if (request.daysAllowed() != null) {
            lt.setDaysAllowed(request.daysAllowed());
            lt.setCompanyPoolDays(request.daysAllowed());
        }
        if (request.paid() != null) {
            lt.setPaid(request.paid());
        }
        if (request.carryForward() != null) {
            lt.setCarryForward(request.carryForward());
        }
        if (request.maxCarryDays() != null) {
            lt.setMaxCarryDays(request.maxCarryDays());
        }
        lt.setDescription(blankToNull(request.description()));
        if (request.active() != null) {
            lt.setActive(request.active());
        }
        return toLeaveType(leaveTypeRepository.save(lt));
    }

    public List<LeavePolicyDtos.HolidayResponse> listHolidays() {
        return holidayRepository.findAllByOrderByHolidayDateAsc().stream()
                .map(this::toHoliday)
                .toList();
    }

    @Transactional
    public LeavePolicyDtos.HolidayResponse createHoliday(LeavePolicyDtos.CreateHolidayRequest request) {
        UUID orgId = requireOrganizationId();
        Holiday h = new Holiday();
        h.setOrganizationId(orgId);
        h.setName(request.name().trim());
        h.setHolidayDate(request.holidayDate());
        h.setType(blankToNull(request.type()));
        h.setDescription(blankToNull(request.description()));
        h.setCreatedAt(LocalDateTime.now());
        return toHoliday(holidayRepository.save(h));
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
