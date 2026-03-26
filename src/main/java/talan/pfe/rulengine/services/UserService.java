package talan.pfe.rulengine.services;

import talan.pfe.rulengine.dtos.request.CreateUserRequest;
import talan.pfe.rulengine.dtos.response.UserResponse;
import talan.pfe.rulengine.enums.Role;

import java.util.List;

public interface UserService {

    UserResponse create(CreateUserRequest req, Long tenantId);

    UserResponse getById(Long id, Long tenantId);

    List<UserResponse> getByTenant(Long tenantId);

    void delete(Long id, Long tenantId);

    UserResponse setActive(Long id, Long tenantId, boolean active);

    UserResponse changeRole(Long id, Long tenantId, Role newRole);
}