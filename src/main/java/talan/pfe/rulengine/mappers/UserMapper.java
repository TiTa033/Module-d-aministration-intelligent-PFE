package talan.pfe.rulengine.mappers;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import talan.pfe.rulengine.dtos.response.UserResponse;
import talan.pfe.rulengine.entites.User;

import java.util.List;

@Mapper(componentModel = "spring")
public interface UserMapper {

    @Mapping(source = "tenant.id",   target = "tenantId")
    @Mapping(source = "tenant.name", target = "tenantName")
    UserResponse toDto(User user);

    List<UserResponse> toDtoList(List<User> users);
}