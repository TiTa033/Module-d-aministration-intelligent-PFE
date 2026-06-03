package talan.pfe.rulengine.services.serviceImpl;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import talan.pfe.rulengine.exception.BadRequestException;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("CaptchaService")
class CaptchaServiceTest {

    private CaptchaService service;

    @BeforeEach
    void setUp() {
        service = new CaptchaService();
        ReflectionTestUtils.setField(service, "enabled", false);
    }

    @Test
    @DisplayName("verify() ignoré si captcha désactivé")
    void verify_disabled() {
        assertThatCode(() -> service.verify(null)).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("verify() exige un token si activé")
    void verify_enabledBlankToken() {
        ReflectionTestUtils.setField(service, "enabled", true);

        assertThatThrownBy(() -> service.verify(" "))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("Captcha token");
    }
}
