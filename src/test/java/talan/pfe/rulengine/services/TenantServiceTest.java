package talan.pfe.rulengine.services;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.*;
import talan.pfe.rulengine.dtos.request.CreateTenantRequest;
import talan.pfe.rulengine.dtos.response.TenantResponse;
import talan.pfe.rulengine.dtos.request.UpdateTenantRequest;
import talan.pfe.rulengine.entites.Tenant;
import talan.pfe.rulengine.enums.TenantStatus;
import talan.pfe.rulengine.exception.BadRequestException;
import talan.pfe.rulengine.exception.ConflictException;
import talan.pfe.rulengine.exception.ResourceNotFoundException;
import talan.pfe.rulengine.mappers.TenantMapper;
import talan.pfe.rulengine.repositories.TenantRepository;
import talan.pfe.rulengine.services.serviceImpl.TenantServiceImpl;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class TenantServiceTest {

    @Mock
    private TenantRepository tenantRepository;

    @Mock
    private TenantMapper tenantMapper;

    private TenantServiceImpl tenantService;

    private Tenant mockTenant;

    @BeforeEach
    void setUp() {
        tenantService = new TenantServiceImpl(tenantRepository, tenantMapper);
        mockTenant = new Tenant();
        mockTenant.setId(1L);
        mockTenant.setName("BNP Paribas");
        mockTenant.setSlug("bnp");
        mockTenant.setStatus(TenantStatus.ACTIVE);
        mockTenant.setCreatedAt(LocalDateTime.now());
        mockTenant.setUpdatedAt(LocalDateTime.now());

        lenient().when(tenantMapper.toDto(any(Tenant.class)))
                .thenAnswer(invocation -> TenantResponse.from(invocation.getArgument(0)));
        lenient().when(tenantMapper.toDto(any(Tenant.class), anyLong()))
                .thenAnswer(invocation -> TenantResponse.from(
                        invocation.getArgument(0),
                        invocation.getArgument(1)
                ));
    }

    // ─── CREATE TESTS ───────────────────────────────────────

    @Test
    void create_withValidRequest_shouldReturnTenantResponse() {
        // Arrange
        CreateTenantRequest request = new CreateTenantRequest();
        request.setName("BNP Paribas");
        request.setSlug("bnp");

        when(tenantRepository.existsBySlug("bnp")).thenReturn(false);
        when(tenantRepository.save(any())).thenReturn(mockTenant);

        // Act
        TenantResponse response = tenantService.create(request);

        // Assert
        assertNotNull(response);
        assertEquals("BNP Paribas", response.getName());
        assertEquals("bnp", response.getSlug());
        verify(tenantRepository, times(1)).save(any());
    }

    @Test
    void create_withDuplicateSlug_shouldThrowConflictException() {
        // Arrange
        CreateTenantRequest request = new CreateTenantRequest();
        request.setName("BNP Paribas");
        request.setSlug("bnp");

        when(tenantRepository.existsBySlug("bnp")).thenReturn(true);

        // Act & Assert
        assertThrows(ConflictException.class,
                () -> tenantService.create(request));

        verify(tenantRepository, never()).save(any());
    }

    // ─── GET BY ID TESTS ────────────────────────────────────

    @Test
    void getById_withExistingId_shouldReturnTenantResponse() {
        // Arrange
        Long id = mockTenant.getId();
        when(tenantRepository.findById(id))
                .thenReturn(Optional.of(mockTenant));
        when(tenantRepository.countUsersByTenantId(id)).thenReturn(5L);

        // Act
        TenantResponse response = tenantService.getById(id);

        // Assert
        assertNotNull(response);
        assertEquals(id, response.getId());
        assertEquals(5L, response.getTotalUsers());
    }

    @Test
    void getById_withNonExistingId_shouldThrowResourceNotFoundException() {
        // Arrange
        Long id = 999L;
        when(tenantRepository.findById(id)).thenReturn(Optional.empty());

        // Act & Assert
        assertThrows(ResourceNotFoundException.class,
                () -> tenantService.getById(id));
    }

    // ─── UPDATE TESTS ───────────────────────────────────────

    @Test
    void update_withValidRequest_shouldReturnUpdatedTenant() {
        // Arrange
        Long id = mockTenant.getId();
        UpdateTenantRequest request = new UpdateTenantRequest();
        request.setName("BNP Paribas Updated");

        when(tenantRepository.findById(id))
                .thenReturn(Optional.of(mockTenant));
        when(tenantRepository.save(any())).thenReturn(mockTenant);

        // Act
        TenantResponse response = tenantService.update(id, request);

        // Assert
        assertNotNull(response);
        verify(tenantRepository, times(1)).save(any());
    }

    @Test
    void update_withNonExistingId_shouldThrowResourceNotFoundException() {
        // Arrange
        Long id = 999L;
        UpdateTenantRequest request = new UpdateTenantRequest();
        request.setName("Test");

        when(tenantRepository.findById(id)).thenReturn(Optional.empty());

        // Act & Assert
        assertThrows(ResourceNotFoundException.class,
                () -> tenantService.update(id, request));
    }

    // ─── ACTIVATE TESTS ─────────────────────────────────────

    @Test
    void activate_withInactiveTenant_shouldActivate() {
        // Arrange
        mockTenant.setStatus(TenantStatus.INACTIVE);
        Long id = mockTenant.getId();

        when(tenantRepository.findById(id))
                .thenReturn(Optional.of(mockTenant));
        when(tenantRepository.save(any())).thenReturn(mockTenant);

        // Act
        TenantResponse response = tenantService.activate(id);

        // Assert
        assertNotNull(response);
        verify(tenantRepository, times(1)).save(any());
    }

    @Test
    void activate_withAlreadyActiveTenant_shouldThrowBadRequestException() {
        // Arrange
        mockTenant.setStatus(TenantStatus.ACTIVE);
        Long id = mockTenant.getId();

        when(tenantRepository.findById(id))
                .thenReturn(Optional.of(mockTenant));

        // Act & Assert
        assertThrows(BadRequestException.class,
                () -> tenantService.activate(id));
    }

    // ─── DEACTIVATE TESTS ───────────────────────────────────

    @Test
    void deactivate_withActiveTenant_shouldDeactivate() {
        // Arrange
        mockTenant.setStatus(TenantStatus.ACTIVE);
        Long id = mockTenant.getId();

        when(tenantRepository.findById(id))
                .thenReturn(Optional.of(mockTenant));
        when(tenantRepository.save(any())).thenReturn(mockTenant);

        // Act
        TenantResponse response = tenantService.deactivate(id);

        // Assert
        assertNotNull(response);
        verify(tenantRepository, times(1)).save(any());
    }

    @Test
    void deactivate_withAlreadyInactiveTenant_shouldThrowBadRequestException() {
        // Arrange
        mockTenant.setStatus(TenantStatus.INACTIVE);
        Long id = mockTenant.getId();

        when(tenantRepository.findById(id))
                .thenReturn(Optional.of(mockTenant));

        // Act & Assert
        assertThrows(BadRequestException.class,
                () -> tenantService.deactivate(id));
    }

    // ─── DELETE TESTS ───────────────────────────────────────

    @Test
    void delete_withNoUsers_shouldDeleteSuccessfully() {
        // Arrange
        Long id = mockTenant.getId();

        when(tenantRepository.findById(id))
                .thenReturn(Optional.of(mockTenant));
        when(tenantRepository.countUsersByTenantId(id)).thenReturn(0L);

        // Act
        tenantService.delete(id);

        // Assert
        verify(tenantRepository, times(1)).delete(mockTenant);
    }

    @Test
    void delete_withExistingUsers_shouldThrowBadRequestException() {
        // Arrange
        Long id = mockTenant.getId();

        when(tenantRepository.findById(id))
                .thenReturn(Optional.of(mockTenant));
        when(tenantRepository.countUsersByTenantId(id)).thenReturn(3L);

        // Act & Assert
        assertThrows(BadRequestException.class,
                () -> tenantService.delete(id));

        verify(tenantRepository, never()).delete(any());
    }

    @Test
    void delete_withNonExistingId_shouldThrowResourceNotFoundException() {
        // Arrange
        Long id = 999L;
        when(tenantRepository.findById(id)).thenReturn(Optional.empty());

        // Act & Assert
        assertThrows(ResourceNotFoundException.class,
                () -> tenantService.delete(id));
    }

    // ─── GET ALL TESTS ──────────────────────────────────────

    @Test
    void getAll_shouldReturnPagedResults() {
        // Arrange
        Page<Tenant> mockPage = new PageImpl<>(
                List.of(mockTenant),
                PageRequest.of(0, 10),
                1
        );

        when(tenantRepository.findAllWithFilters(any(), any(), any()))
                .thenReturn(mockPage);
        when(tenantRepository.countUsersByTenantId(any())).thenReturn(0L);

        // Act
        var response = tenantService.getAll("", "", 0, 10, "createdAt", "desc");

        // Assert
        assertNotNull(response);
        assertEquals(1, response.getTotalElements());
        assertEquals(1, response.getContent().size());
    }
}