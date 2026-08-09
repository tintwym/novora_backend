package prod.tint_wym.novora_backend.service;

import jakarta.transaction.Transactional;
import java.util.List;
import org.springframework.stereotype.Service;

@Service
public class RoleService {

    @Transactional(Transactional.TxType.SUPPORTS)
    public List<String> listRoles() {
        return List.of("SUPER_ADMIN", "HR_ADMIN", "HR_MANAGER", "MANAGER", "EMPLOYEE");
    }
}
