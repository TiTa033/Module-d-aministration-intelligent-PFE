package talan.pfe.rulengine.mappers;

import org.springframework.stereotype.Component;
import talan.pfe.rulengine.dtos.response.UserResponse;
import talan.pfe.rulengine.entites.User;

import java.util.List;
import java.util.stream.Collectors;

@Component
public class UserMapper {

    public UserResponse toDto(User user) {
        if (user == null) return null;
        return UserResponse.builder()
                .id(user.getId())
                .email(user.getEmail())
                .role(user.getRole())
                .tenantId(user.getTenant() != null ? user.getTenant().getId() : null)
                .active(user.isActive())
                .build();
    }

    public List<UserResponse> toDtoList(List<User> users) {
        if (users == null) return List.of();
        return users.stream()
                .map(this::toDto)
                .collect(Collectors.toList());
    }
}
