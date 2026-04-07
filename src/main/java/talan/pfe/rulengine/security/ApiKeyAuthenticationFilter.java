package talan.pfe.rulengine.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.lang.NonNull;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.security.web.util.matcher.AntPathRequestMatcher;
import org.springframework.web.filter.OncePerRequestFilter;
import talan.pfe.rulengine.entites.ApiKey;
import talan.pfe.rulengine.repositories.ApiKeyRepository;

import java.io.IOException;
import java.util.List;

@Component
@RequiredArgsConstructor
public class ApiKeyAuthenticationFilter extends OncePerRequestFilter {

    public static final String HEADER = "X-API-Key";

    private static final int PREFIX_LEN = 12;
    private static final AntPathRequestMatcher EVALUATE =
            new AntPathRequestMatcher("/api/evaluate", "POST");

    private final ApiKeyRepository apiKeyRepository;
    private final PasswordEncoder passwordEncoder;

    @Override
    protected void doFilterInternal(
            @NonNull HttpServletRequest request,
            @NonNull HttpServletResponse response,
            @NonNull FilterChain filterChain
    ) throws ServletException, IOException {

        if (!EVALUATE.matches(request)) {
            filterChain.doFilter(request, response);
            return;
        }

        String rawKey = request.getHeader(HEADER);
        if (rawKey == null || rawKey.isBlank()) {
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            response.setContentType("application/json");
            response.getWriter().write(
                    "{\"error\":\"Unauthorized\","
                            + "\"message\":\"Missing X-API-Key header\"}");
            return;
        }

        String prefix = rawKey.substring(0, Math.min(PREFIX_LEN, rawKey.length()));
        List<ApiKey> candidates = apiKeyRepository.findAllByKeyPrefixAndActiveTrue(prefix);
        ApiKey matched = null;
        for (ApiKey key : candidates) {
            if (passwordEncoder.matches(rawKey, key.getKeyHash())
                    && key.isValid()) {
                matched = key;
                break;
            }
        }

        if (matched == null) {
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            response.setContentType("application/json");
            response.getWriter().write(
                    "{\"error\":\"Unauthorized\",\"message\":\"Invalid or expired API key\"}");
            return;
        }

        if (SecurityContextHolder.getContext().getAuthentication() == null) {
            ApiClientPrincipal principal = new ApiClientPrincipal(
                    matched.getTenant().getId(),
                    matched.getId());
            UsernamePasswordAuthenticationToken auth =
                    new UsernamePasswordAuthenticationToken(
                            principal,
                            null,
                            principal.getAuthorities());
            auth.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
            SecurityContextHolder.getContext().setAuthentication(auth);
        }

        filterChain.doFilter(request, response);
    }
}
