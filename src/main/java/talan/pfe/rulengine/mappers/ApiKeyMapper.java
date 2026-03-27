package talan.pfe.rulengine.mappers;

import org.mapstruct.*;
import talan.pfe.rulengine.dtos.response.ApiKeyResponse;
import talan.pfe.rulengine.entites.ApiKey;

import java.util.List;

@Mapper(componentModel = "spring")
public interface ApiKeyMapper {

    @Mapping(source = "tenant.id",   target = "tenantId")
    @Mapping(source = "tenant.name", target = "tenantName")
    @Mapping(target = "expired",
            expression = "java(apiKey.isExpired())")
    @Mapping(target = "keyPrefix",
            expression = "java(apiKey.getKeyHash().substring(0, Math.min(8, apiKey.getKeyHash().length())))")
    ApiKeyResponse toDto(ApiKey apiKey);

    List<ApiKeyResponse> toDtoList(List<ApiKey> apiKeys);
}