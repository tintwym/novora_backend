package prod.tint_wym.novora_backend.repository;

import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import prod.tint_wym.novora_backend.entity.ClaimPolicy;

public interface ClaimPolicyRepository extends JpaRepository<ClaimPolicy, UUID> {
}
