package talan.pfe.rulengine.services;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.ActiveProfiles;
import talan.pfe.rulengine.dtos.request.RollbackRuleSetRequest;
import talan.pfe.rulengine.dtos.request.RuleSetImportRequest;
import talan.pfe.rulengine.dtos.response.RuleSetResponse;
import talan.pfe.rulengine.dtos.rulesetexport.RuleSetExportPackage;
import talan.pfe.rulengine.entites.RuleSet;
import talan.pfe.rulengine.entites.Tenant;
import talan.pfe.rulengine.entites.User;
import talan.pfe.rulengine.enums.RuleSetStatus;
import talan.pfe.rulengine.enums.Role;
import talan.pfe.rulengine.enums.TenantStatus;
import talan.pfe.rulengine.repositories.RuleSetRepository;
import talan.pfe.rulengine.repositories.RuleSetVersionRepository;
import talan.pfe.rulengine.repositories.TenantRepository;
import talan.pfe.rulengine.repositories.UserRepository;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@ActiveProfiles("test")
class RuleSetImportExportServiceNoMockTest {

    @Autowired private RuleSetImportExportService ruleSetImportExportService;
    @Autowired private ObjectMapper objectMapper;

    @Autowired private TenantRepository tenantRepository;
    @Autowired private UserRepository userRepository;
    @Autowired private RuleSetRepository ruleSetRepository;
    @Autowired private RuleSetVersionRepository ruleSetVersionRepository;

    private Tenant tenant;

    @BeforeEach
    void setup() {
        tenant = tenantRepository.save(Tenant.builder()
                .name("T1")
                .slug("t1-" + System.nanoTime())
                .status(TenantStatus.ACTIVE)
                .build());

        User user = userRepository.save(User.builder()
                .name("Admin")
                .email("admin+" + System.nanoTime() + "@ex.com")
                .passwordHash("x")
                .role(Role.ADMIN)
                .tenant(tenant)
                .active(true)
                .build());

        var principal = org.springframework.security.core.userdetails.User
                .withUsername(user.getEmail())
                .password("x")
                .authorities(List.of(new SimpleGrantedAuthority("ROLE_ADMIN")))
                .build();
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(
                        principal,
                        null,
                        principal.getAuthorities()
                )
        );
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void import_then_export_then_restore_createsVersions() {
        var pkg = RuleSetExportPackage.builder()
                .schemaVersion(1)
                .name("RS-IMP")
                .evaluationStrategy("FIRST_MATCH")
                .status("DRAFT")
                .rules(List.of())
                .build();

        RuleSetResponse created = ruleSetImportExportService.importPackage(
                RuleSetImportRequest.builder()
                        .packageJson(objectMapper.valueToTree(pkg))
                        .failIfNameExists(true)
                        .build(),
                tenant.getId()
        );

        assertNotNull(created.getId());
        assertFalse(ruleSetVersionRepository
                .findByRuleSetIdOrderByVersionNumberDesc(created.getId())
                .isEmpty());

        RuleSet rs = ruleSetRepository.findById(created.getId()).orElseThrow();
        rs.setStatus(RuleSetStatus.ACTIVE);
        ruleSetRepository.save(rs);

        String exported = ruleSetImportExportService.exportJson(created.getId(), tenant.getId());
        assertTrue(exported.contains("\"name\""));

        int latest = ruleSetVersionRepository
                .findByRuleSetIdOrderByVersionNumberDesc(created.getId())
                .get(0)
                .getVersionNumber();

        RuleSetResponse restored = ruleSetImportExportService.restoreVersion(
                created.getId(),
                tenant.getId(),
                latest,
                RollbackRuleSetRequest.builder().changeNote("restore").build()
        );

        assertEquals(created.getId(), restored.getId());
        assertTrue(ruleSetRepository.findById(created.getId()).isPresent());
        assertTrue(ruleSetVersionRepository
                .findByRuleSetIdOrderByVersionNumberDesc(created.getId())
                .size() >= 2);
    }
}

