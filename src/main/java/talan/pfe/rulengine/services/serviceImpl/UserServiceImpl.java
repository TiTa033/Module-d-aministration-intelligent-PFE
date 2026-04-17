package talan.pfe.rulengine.services.serviceImpl;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import talan.pfe.rulengine.dtos.request.CreateUserRequest;
import talan.pfe.rulengine.dtos.response.UserResponse;
import talan.pfe.rulengine.entites.Tenant;
import talan.pfe.rulengine.entites.User;
import talan.pfe.rulengine.enums.AuditAction;
import talan.pfe.rulengine.enums.Role;
import talan.pfe.rulengine.kafka.AuditProducer;
import talan.pfe.rulengine.mappers.UserMapper;
import talan.pfe.rulengine.repositories.TenantRepository;
import talan.pfe.rulengine.repositories.UserRepository;
import talan.pfe.rulengine.security.CurrentUserResolver;
import talan.pfe.rulengine.services.UserService;

import java.util.List;

@Service
@RequiredArgsConstructor
public class UserServiceImpl implements UserService {

    private final UserRepository userRepository;
    private final TenantRepository tenantRepository;
    private final UserMapper userMapper;
    private final AuditProducer auditProducer;
    private final CurrentUserResolver currentUserResolver;

    @Override
    @Transactional
    public UserResponse create(CreateUserRequest req, Long tenantId) {
        Tenant tenant = tenantRepository.findById(tenantId).orElseThrow();

        User existingUser = userRepository.findByEmail(req.getEmail())
                .orElseThrow(() -> new IllegalArgumentException(
                        "Cet utilisateur doit deja etre inscrit avant de recevoir un acces"));

        if (existingUser.getTenant() != null &&
                !tenantId.equals(existingUser.getTenant().getId())) {
            throw new IllegalArgumentException(
                    "Cet utilisateur est deja rattache a un autre tenant");
        }

        existingUser.setName(req.getName());
        existingUser.setRole(req.getRole());
        existingUser.setTenant(tenant);
        existingUser.setActive(true);

        UserResponse saved = userMapper.toDto(userRepository.save(existingUser));

        auditProducer.publish(
                AuditAction.USER_CREATED, "USER", saved.getId(),
                null, saved.getEmail(),
                tenantId, currentUserResolver.getCurrentUserId(), null);

        return saved;
    }

    @Override
    public UserResponse getById(Long id, Long tenantId) {
        return userMapper.toDto(getUserFromTenant(id, tenantId));
    }

    @Override
    public List<UserResponse> getByTenant(Long tenantId) {
        return userMapper.toDtoList(
                userRepository.findAllByTenantId(tenantId));
    }

    @Override
    @Transactional
    public void delete(Long id, Long tenantId) {
        User user = getUserFromTenant(id, tenantId);

        auditProducer.publish(
                AuditAction.USER_DELETED, "USER", id,
                user.getEmail(), null,
                tenantId, currentUserResolver.getCurrentUserId(), null);

        userRepository.delete(user);
    }

    @Override
    @Transactional
    public UserResponse setActive(Long id, Long tenantId, boolean active) {
        User user = getUserFromTenant(id, tenantId);
        user.setActive(active);
        UserResponse saved = userMapper.toDto(userRepository.save(user));

        auditProducer.publish(
                active ? AuditAction.USER_ACTIVATED : AuditAction.USER_DEACTIVATED,
                "USER", id, null, null,
                tenantId, currentUserResolver.getCurrentUserId(), null);

        return saved;
    }

    @Override
    @Transactional
    public UserResponse changeRole(Long id, Long tenantId, Role newRole) {
        User user = getUserFromTenant(id, tenantId);
        String oldRole = user.getRole().name();
        user.setRole(newRole);
        UserResponse saved = userMapper.toDto(userRepository.save(user));

        auditProducer.publish(
                AuditAction.USER_ROLE_CHANGED, "USER", id,
                oldRole, newRole.name(),
                tenantId, currentUserResolver.getCurrentUserId(), null);

        return saved;
    }

    private User getUserFromTenant(Long id, Long tenantId) {
        return userRepository.findByIdAndTenantId(id, tenantId)
                .orElseThrow();
    }
}