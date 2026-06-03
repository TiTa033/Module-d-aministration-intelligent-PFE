package talan.pfe.rulengine.services.serviceImpl;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.test.util.ReflectionTestUtils;
import talan.pfe.rulengine.exception.BadRequestException;

import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.mockito.Mockito.lenient;

@ExtendWith(MockitoExtension.class)
@DisplayName("OtpService")
class OtpServiceTest {

    @Mock StringRedisTemplate redisTemplate;
    @Mock ValueOperations<String, String> valueOps;

    @InjectMocks OtpService service;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(service, "expirationMinutes", 5L);
        ReflectionTestUtils.setField(service, "otpLength", 6);
        lenient().when(redisTemplate.opsForValue()).thenReturn(valueOps);
    }

    @Test
    @DisplayName("generateAndStore() enregistre l'OTP dans Redis")
    void generateAndStore_savesOtp() {
        String otp = service.generateAndStore("user@test.com");

        assertThat(otp).hasSize(6);
        verify(valueOps).set(eq("otp:user@test.com"), eq(otp), eq(5L), eq(TimeUnit.MINUTES));
        verify(redisTemplate).delete("otp_attempts:user@test.com");
    }

    @Test
    @DisplayName("verify() accepte un OTP valide")
    void verify_validOtp() {
        when(valueOps.get("otp_attempts:user@test.com")).thenReturn(null);
        when(valueOps.get("otp:user@test.com")).thenReturn("123456");

        service.verify("user@test.com", "123456");

        verify(redisTemplate).delete("otp:user@test.com");
        verify(redisTemplate).delete("otp_attempts:user@test.com");
    }

    @Test
    @DisplayName("verify() rejette un OTP invalide")
    void verify_invalidOtp() {
        when(valueOps.get("otp_attempts:user@test.com")).thenReturn(null);
        when(valueOps.get("otp:user@test.com")).thenReturn("123456");

        assertThatThrownBy(() -> service.verify("user@test.com", "000000"))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("Invalid OTP");

        verify(valueOps).increment("otp_attempts:user@test.com");
    }

    @Test
    @DisplayName("verify() rejette si OTP expiré")
    void verify_expiredOtp() {
        when(valueOps.get("otp_attempts:user@test.com")).thenReturn(null);
        when(valueOps.get("otp:user@test.com")).thenReturn(null);

        assertThatThrownBy(() -> service.verify("user@test.com", "123456"))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("expired");
    }

    @Test
    @DisplayName("hasActiveOtp() reflète la présence de clé Redis")
    void hasActiveOtp() {
        when(redisTemplate.hasKey("otp:user@test.com")).thenReturn(true);
        assertThat(service.hasActiveOtp("user@test.com")).isTrue();
    }
}
