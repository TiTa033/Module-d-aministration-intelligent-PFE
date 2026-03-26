package talan.pfe.rulengine.services.serviceImpl;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import talan.pfe.rulengine.dtos.request.CreateUserRequest;
import talan.pfe.rulengine.dtos.response.UserResponse;
import talan.pfe.rulengine.entites.Tenant;
import talan.pfe.rulengine.entites.User;
import talan.pfe.rulengine.enums.Role;
import talan.pfe.rulengine.mappers.UserMapper;
import talan.pfe.rulengine.repositories.TenantRepository;
import talan.pfe.rulengine.repositories.UserRepository;
import talan.pfe.rulengine.services.UserService;

import java.util.List;

@Service
@RequiredArgsConstructor
public class UserServiceImpl implements UserService {

    private final UserRepository userRepository;
    private final TenantRepository tenantRepository;
    private final UserMapper userMapper;
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

        return userMapper.toDto(userRepository.save(existingUser));
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
        userRepository.delete(getUserFromTenant(id, tenantId));
    }

    @Override
    @Transactional
    public UserResponse setActive(Long id, Long tenantId, boolean active) {
        User user = getUserFromTenant(id, tenantId);
        user.setActive(active);
        return userMapper.toDto(userRepository.save(user));
    }
    @Override
    @Transactional
    public UserResponse changeRole(Long id, Long tenantId, Role newRole) {
        User user = getUserFromTenant(id, tenantId);
        user.setRole(newRole);
        return userMapper.toDto(userRepository.save(user));
    }

    private User getUserFromTenant(Long id, Long tenantId) {
        return userRepository.findByIdAndTenantId(id, tenantId)
                .orElseThrow();
    }
}