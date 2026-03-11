package talan.pfe.rulengine.services;

import lombok.RequiredArgsConstructor;
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

    @Transactional
    public User create(CreateUserRequest req, UUID tenantId) {
        Tenant tenant = tenantRepository.findById(tenantId).orElseThrow();

        User existingUser = userRepository.findByEmail(req.getEmail())
                .orElseThrow(() -> new IllegalArgumentException("Cet utilisateur doit deja etre inscrit avant de recevoir un acces"));

        if (existingUser.getTenant() != null && !tenantId.equals(existingUser.getTenant().getId())) {
            throw new IllegalArgumentException("Cet utilisateur est deja rattache a un autre tenant");
        }

        existingUser.setName(req.getName());
        existingUser.setRole(req.getRole());
        existingUser.setTenant(tenant);
        existingUser.setActive(true);

        return userRepository.save(existingUser);
    }

    public User getById(UUID id, UUID tenantId) {
        return getUserFromTenant(id, tenantId);
    }

    public List<User> getByTenant(UUID tenantId) {
        return userRepository.findAllByTenantId(tenantId);
    }

    @Transactional
    public void delete(UUID id, UUID tenantId) {
        userRepository.delete(getUserFromTenant(id, tenantId));
    }

    @Transactional
    public User setActive(UUID id, UUID tenantId, boolean active) {
        User user = getUserFromTenant(id, tenantId);
        user.setActive(active);
        return userRepository.save(user);
    }

    @Transactional
    public User changeRole(UUID id, UUID tenantId, Role newRole) {
        User user = getUserFromTenant(id, tenantId);
        user.setRole(newRole);
        return userRepository.save(user);
    }

    private User getUserFromTenant(UUID id, UUID tenantId) {
        return userRepository.findByIdAndTenantId(id, tenantId).orElseThrow();
    }
}