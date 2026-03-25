package talan.pfe.rulengine.services;

import talan.pfe.rulengine.dtos.request.CreateUserRequest;
import talan.pfe.rulengine.entites.User;
import talan.pfe.rulengine.enums.Role;

import java.util.List;
import java.util.UUID;

public interface UserService {
    User create(CreateUserRequest req, UUID tenantId);
    User getById(UUID id, UUID tenantId);
    List<User> getByTenant(UUID tenantId);
    void delete(UUID id, UUID tenantId);
    User setActive(UUID id, UUID tenantId, boolean active);
    User changeRole(UUID id, UUID tenantId, Role newRole);
}
