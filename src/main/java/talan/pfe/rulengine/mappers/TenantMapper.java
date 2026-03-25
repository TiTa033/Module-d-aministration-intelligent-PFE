package talan.pfe.rulengine.mappers;

import org.mapstruct.*;
import talan.pfe.rulengine.dtos.response.TenantResponse;
import talan.pfe.rulengine.entites.Tenant;

import java.util.List;

@Mapper(componentModel = "spring")
public interface TenantMapper {

    @Mapping(source = "status", target = "status",
            qualifiedByName = "statusToString")
    @Mapping(target = "totalUsers", constant = "0L")
    TenantResponse toDto(Tenant tenant);

    List<TenantResponse> toDtoList(List<Tenant> tenants);

    @Named("statusToString")
    default String statusToString(
            talan.pfe.rulengine.enums.TenantStatus status) {
        return status != null ? status.name() : null;
    }
}