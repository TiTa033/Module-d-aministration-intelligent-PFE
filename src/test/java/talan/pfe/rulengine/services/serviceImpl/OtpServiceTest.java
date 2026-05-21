package talan.pfe.rulengine.services.serviceImpl;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.test.util.ReflectionTestUtils;
import talan.pfe.rulengine.exception.BadRequestException;

import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class OtpServiceTest {

    @Mock RedisTemplate<String, String> redisTemplate;
    @Mock ValueOperations<String, String> valueOps;

    @InjectMocks OtpService otpService;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(otpService, "expirationMinutes", 5L);
        ReflectionTestUtils.setField(otpService, "otpLength", 6);
        lenient().when(redisTemplate.opsForValue()).thenReturn(valueOps);
    }

    // ─── generateAndStore ─────────────────────────────────────

    @Test
    void generateAndStore_storesOtpInRedisAndDeletesAttempts() {
        String otp = otpService.generateAndStore("user@test.com");

        verify(valueOps).set(eq("otp:user@test.com"), eq(otp), eq(5L), eq(TimeUnit.MINUTES));
        verify(redisTemplate).delete("otp_attempts:user@test.com");
        assertThat(otp).hasSize(6).containsPattern("[0-9]{6}");
    }

    @Test
    void generateAndStore_eachCallProducesDigitsOnly() {
        String otp = otpService.generateAndStore("a@b.com");

        assertThat(otp).matches("[0-9]+");
    }

    // ─── verify ───────────────────────────────────────────────

    @Test
    void verify_whenTooManyAttempts_deletesKeysAndThrows() {
        when(valueOps.get("otp_attempts:user@test.com")).thenReturn("3");

        assertThatThrownBy(() -> otpService.verify("user@test.com", "123456"))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("Too many");

        verify(redisTemplate).delete("otp:user@test.com");
        verify(redisTemplate).delete("otp_attempts:user@test.com");
    }

    @Test
    void verify_whenOtpNotInRedis_throws() {
        when(valueOps.get("otp_attempts:user@test.com")).thenReturn(null);
        when(valueOps.get("otp:user@test.com")).thenReturn(null);

        assertThatThrownBy(() -> otpService.verify("user@test.com", "123456"))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("expired");
    }

    @Test
    void verify_whenWrongOtp_incrementsAttemptsAndThrows() {
        when(valueOps.get("otp_attempts:user@test.com")).thenReturn("1");
        when(valueOps.get("otp:user@test.com")).thenReturn("999999");

        assertThatThrownBy(() -> otpService.verify("user@test.com", "123456"))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("Invalid");

        verify(valueOps).increment("otp_attempts:user@test.com");
        verify(redisTemplate).expire("otp_attempts:user@test.com", 5L, TimeUnit.MINUTES);
    }

    @Test
    void verify_whenCorrectOtp_deletesBothKeys() {
        when(valueOps.get("otp_attempts:user@test.com")).thenReturn(null);
        when(valueOps.get("otp:user@test.com")).thenReturn("123456");

        assertThatCode(() -> otpService.verify("user@test.com", "123456")).doesNotThrowAnyException();

        verify(redisTemplate).delete("otp:user@test.com");
        verify(redisTemplate).delete("otp_attempts:user@test.com");
    }

    // ─── hasActiveOtp ─────────────────────────────────────────

    @Test
    void hasActiveOtp_returnsTrueWhenKeyExists() {
        when(redisTemplate.hasKey("otp:user@test.com")).thenReturn(Boolean.TRUE);

        assertThat(otpService.hasActiveOtp("user@test.com")).isTrue();
    }

    @Test
    void hasActiveOtp_returnsFalseWhenKeyAbsent() {
        when(redisTemplate.hasKey("otp:user@test.com")).thenReturn(Boolean.FALSE);

        assertThat(otpService.hasActiveOtp("user@test.com")).isFalse();
    }
}
