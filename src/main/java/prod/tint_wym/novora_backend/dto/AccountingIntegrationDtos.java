package prod.tint_wym.novora_backend.dto;

import jakarta.validation.constraints.NotNull;
import java.time.Instant;
import java.util.UUID;

public final class AccountingIntegrationDtos {
    private AccountingIntegrationDtos() {
    }

    public record ConnectionStatus(
            boolean serverReady,
            boolean connected,
            UUID accountingCompanyId,
            String lastPayrollRunId,
            Instant lastSyncedAt,
            String lastError
    ) {
    }

    public record ConnectRequest(@NotNull UUID accountingCompanyId) {
    }
}
