package talan.pfe.rulengine.services;

import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import talan.pfe.rulengine.dtos.request.CreateUserRequest;
import talan.pfe.rulengine.entites.Tenant;
import talan.pfe.rulengine.entites.User;
import talan.pfe.rulengine.enums.Role;
import talan.pfe.rulengine.repositories.TenantRepository;
import talan.pfe.rulengine.repositories.UserRepository;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class UserService {

    private final UserRepository userRepository;
    private final TenantRepository tenantRepository;
    private final PasswordEncoder passwordEncoder;

    @Transactional
    public User create(CreateUserRequest req, UUID tenantId) {
        Tenant tenant = tenantRepository.findById(tenantId).orElseThrow();

        if (userRepository.existsByEmailAndTenantId(req.getEmail(), tenantId)) {
            throw new IllegalArgumentException("Email already used for this tenant");
        }

        User user = User.builder()
                .email(req.getEmail())
                .passwordHash(passwordEncoder.encode(req.getPassword()))
                .role(req.getRole())
                .tenant(tenant)
                .build();

        return userRepository.save(user);
    }

    public User getById(UUID id) {
        return userRepository.findById(id).orElseThrow();
    }

    public List<User> getByTenant(UUID tenantId) {
        return userRepository.findAllByTenantId(tenantId);
    }

    @Transactional
    public void delete(UUID id) {
        userRepository.deleteById(id);
    }

    @Transactional
    public User setActive(UUID id, boolean active) {
        User user = userRepository.findById(id).orElseThrow();
        user.setActive(active);
        return userRepository.save(user);
    }

    @Transactional
    public User changeRole(UUID id, Role newRole) {
        User user = userRepository.findById(id).orElseThrow();
        user.setRole(newRole);
        return userRepository.save(user);
    }
}