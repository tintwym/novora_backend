package prod.tint_wym.novora_backend.service;

import jakarta.transaction.Transactional;
import java.time.LocalDateTime;
import java.util.List;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;
import prod.tint_wym.novora_backend.entity.Department;
import prod.tint_wym.novora_backend.dto.HrDtos;
import prod.tint_wym.novora_backend.repository.DepartmentRepository;

@Service
public class DepartmentService {

    private final DepartmentRepository departmentRepository;

    public DepartmentService(DepartmentRepository departmentRepository) {
        this.departmentRepository = departmentRepository;
    }

    @Transactional(Transactional.TxType.SUPPORTS)
    public List<HrDtos.DepartmentResponse> listDepartments() {
        return departmentRepository.findAll().stream()
                .map(d -> new HrDtos.DepartmentResponse(
                        d.getId(),
                        d.getName(),
                        d.getCode(),
                        d.getDescription(),
                        d.isActive()
                ))
                .toList();
    }

    @Transactional
    public HrDtos.DepartmentResponse createDepartment(HrDtos.CreateDepartmentRequest request) {
        String code = request.code().trim().toUpperCase();
        if (departmentRepository.findByCodeIgnoreCase(code).isPresent()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Department code already exists");
        }

        LocalDateTime now = LocalDateTime.now();
        Department department = new Department();
        department.setName(request.name().trim());
        department.setCode(code);
        department.setDescription(request.description() == null ? null : request.description().trim());
        department.setActive(true);
        department.setCreatedAt(now);
        department.setUpdatedAt(now);
        Department saved;
        try {
            saved = departmentRepository.save(department);
        } catch (DataIntegrityViolationException ex) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT, "Department code already exists", ex);
        }
        return new HrDtos.DepartmentResponse(
                saved.getId(), saved.getName(), saved.getCode(), saved.getDescription(), saved.isActive());
    }
}
