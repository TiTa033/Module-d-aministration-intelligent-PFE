package talan.pfe.rulengine.security;

import lombok.Getter;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.Collection;
import java.util.List;

@Getter
public class ApiClientPrincipal implements UserDetails {

    public static final String ROLE = "ROLE_API_CLIENT";

    private final Long tenantId;
    private final Long apiKeyId;

    public ApiClientPrincipal(Long tenantId, Long apiKeyId) {
        this.tenantId = tenantId;
        this.apiKeyId = apiKeyId;
    }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return List.of(new SimpleGrantedAuthority(ROLE));
    }

    @Override
    public String getPassword() {
        return "";
    }

    @Override
    public String getUsername() {
        return "apikey:" + apiKeyId;
    }

    @Override
    public boolean isAccountNonExpired() {
        return true;
    }

    @Override
    public boolean isAccountNonLocked() {
        return true;
    }

    @Override
    public boolean isCredentialsNonExpired() {
        return true;
    }

    @Override
    public boolean isEnabled() {
        return true;
    }
}
