package prod.tint_wym.novora_backend.config;

import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import prod.tint_wym.novora_backend.repository.AppUserRepository;

@Service
public class AppUserDetailsService implements UserDetailsService {

    private final AppUserRepository appUserRepository;

    public AppUserDetailsService(AppUserRepository appUserRepository) {
        this.appUserRepository = appUserRepository;
    }

    @Override
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        String normalized = username == null ? "" : username.trim().toLowerCase();
        var user = appUserRepository.findByEmail(normalized)
                .orElseThrow(() -> new UsernameNotFoundException("User not found"));

        String r = user.getRole() == null ? "EMPLOYEE" : user.getRole().trim();
        var authority = new SimpleGrantedAuthority(r.startsWith("ROLE_") ? r : "ROLE_" + r);

        return User.withUsername(user.getEmail())
                .password(user.getPasswordHash())
                .disabled(!user.isActive())
                .authorities(authority)
                .build();
    }
}
