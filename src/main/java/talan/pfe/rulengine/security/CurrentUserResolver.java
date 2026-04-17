package talan.pfe.rulengine.security;

import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Component;
import talan.pfe.rulengine.entites.User;
import talan.pfe.rulengine.exception.BadRequestException;
import talan.pfe.rulengine.repositories.UserRepository;

@Component
@RequiredArgsConstructor
public class CurrentUserResolver {

    private final UserRepository userRepository;

    public User requireUser() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated()) {
            throw new BadRequestException("Authentication required");
        }
        Object principal = auth.getPrincipal();
        if (principal instanceof ApiClientPrincipal) {
            throw new BadRequestException(
                    "This operation cannot be performed with an API key");
        }
        if (!(principal instanceof UserDetails userDetails)) {
            throw new BadRequestException("Invalid security principal");
        }
        return userRepository.findByEmail(userDetails.getUsername())
                .orElseThrow(() -> new BadRequestException("User not found"));
    }
    public Long getCurrentUserId() {
        try {
            return requireUser().getId();
        } catch (Exception e) {
            return null;
        }
    }
}
