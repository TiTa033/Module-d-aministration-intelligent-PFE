package talan.pfe.rulengine.services;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import talan.pfe.rulengine.dtos.request.CreateTenantRequest;
import talan.pfe.rulengine.dtos.response.TenantResponse;
import talan.pfe.rulengine.entites.Tenant;
import talan.pfe.rulengine.enums.TenantStatus;
import talan.pfe.rulengine.mappers.TenantMapper;
import talan.pfe.rulengine.repositories.TenantRepository;
import talan.pfe.rulengine.services.serviceImpl.TenantServiceImpl;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TenantServiceTest {

    @Mock private TenantRepository tenantRepository;
    @Mock private TenantMapper tenantMapper;

    private TenantServiceImpl tenantService;

    @BeforeEach
    void setUp() {
        tenantService = new TenantServiceImpl(tenantRepository, tenantMapper);
    }

    @Test
    void create_shouldReturnTenantResponse() {
        CreateTenantRequest request = new CreateTenantRequest();
        request.setName("BNP");
        request.setSlug("bnp");

        Tenant tenant = Tenant.builder().id(1L).name("BNP").slug("bnp").status(TenantStatus.ACTIVE).build();
        TenantResponse response = TenantResponse.builder().id(1L).name("BNP").slug("bnp").status("ACTIVE").build();

        when(tenantRepository.existsBySlug("bnp")).thenReturn(false);
        when(tenantRepository.save(any(Tenant.class))).thenReturn(tenant);
        when(tenantMapper.toDto(tenant)).thenReturn(response);

        assertNotNull(tenantService.create(request));
    }

    @Test
    void getById_shouldReturnTenantResponse() {
        Tenant tenant = Tenant.builder().id(1L).name("BNP").slug("bnp").status(TenantStatus.ACTIVE).build();
        TenantResponse response = TenantResponse.builder().id(1L).name("BNP").slug("bnp").status("ACTIVE").build();

        when(tenantRepository.findById(1L)).thenReturn(Optional.of(tenant));
        when(tenantRepository.countUsersByTenantId(1L)).thenReturn(0L);
        when(tenantMapper.toDto(tenant)).thenReturn(response);

        assertNotNull(tenantService.getById(1L));
    }
}

