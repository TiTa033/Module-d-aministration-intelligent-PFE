package talan.pfe.rulengine.services;

import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import talan.pfe.rulengine.dtos.request.CreateUserRequest;
import talan.pfe.rulengine.entites.Tenant;
import talan.pfe.rulengine.repositories.UserRepository;
import talan.pfe.rulengine.entites.User;
import talan.pfe.rulengine.enums.Role;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class UserService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    public User create(CreateUserRequest req, Tenant tenant) {
        return userRepository.save(
                User.builder()
                        .email(req.getEmail())
                        .passwordHash(passwordEncoder.encode(req.getPassword()))
                        .role(req.getRole())
                        .tenant(tenant) // pass Tenant entity, not ID
                        .build()
        );
    }

    public User getById(UUID id) {
        return userRepository.findById(id).orElseThrow();
    }

    public List<User> getByTenant(UUID tenantId) {
        return userRepository.findAllByTenantId(tenantId);
    }

    public void delete(UUID id) {
        userRepository.deleteById(id);
    }
}