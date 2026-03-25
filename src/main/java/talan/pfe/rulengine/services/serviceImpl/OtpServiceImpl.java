package talan.pfe.rulengine.services.serviceImpl;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
import talan.pfe.rulengine.exception.BadRequestException;

import java.security.SecureRandom;
import java.util.concurrent.TimeUnit;

@Service
@RequiredArgsConstructor
public class OtpServiceImpl implements talan.pfe.rulengine.services.OtpService {

    private final RedisTemplate<String, String> redisTemplate;

    @Value("${otp.expiration-minutes}")
    private long expirationMinutes;

    @Value("${otp.length}")
    private int otpLength;

    private static final String OTP_PREFIX = "otp:";
    private static final String ATTEMPTS_PREFIX = "otp_attempts:";
    private static final int MAX_ATTEMPTS = 3;

    // ─── GENERATE ───────────────────────────────────────────
    public String generateAndStore(String email) {
        String otp = generateOtp();
        String key = OTP_PREFIX + email;

        // Store in Redis with TTL
        redisTemplate.opsForValue().set(
                key, otp, expirationMinutes, TimeUnit.MINUTES);

        // Reset attempts
        redisTemplate.delete(ATTEMPTS_PREFIX + email);

        return otp;
    }

    // ─── VERIFY ─────────────────────────────────────────────
    public void verify(String email, String otp) {
        String key = OTP_PREFIX + email;
        String attemptsKey = ATTEMPTS_PREFIX + email;

        // Check attempts
        String attemptsStr = redisTemplate.opsForValue().get(attemptsKey);
        int attempts = attemptsStr != null ? Integer.parseInt(attemptsStr) : 0;

        if (attempts >= MAX_ATTEMPTS) {
            redisTemplate.delete(key);
            redisTemplate.delete(attemptsKey);
            throw new BadRequestException(
                    "Too many failed attempts. Please request a new OTP.");
        }

        String storedOtp = redisTemplate.opsForValue().get(key);

        if (storedOtp == null) {
            throw new BadRequestException(
                    "OTP expired or not found. Please request a new one.");
        }

        if (!storedOtp.equals(otp)) {
            // Increment attempts
            redisTemplate.opsForValue().increment(attemptsKey);
            redisTemplate.expire(attemptsKey, expirationMinutes, TimeUnit.MINUTES);
            throw new BadRequestException("Invalid OTP code.");
        }

        // OTP is valid — delete it (single use)
        redisTemplate.delete(key);
        redisTemplate.delete(attemptsKey);
    }

    // ─── CHECK IF OTP EXISTS ────────────────────────────────
    public boolean hasActiveOtp(String email) {
        return Boolean.TRUE.equals(
                redisTemplate.hasKey(OTP_PREFIX + email));
    }

    // ─── PRIVATE ────────────────────────────────────────────
    private String generateOtp() {
        SecureRandom random = new SecureRandom();
        StringBuilder otp = new StringBuilder();
        for (int i = 0; i < otpLength; i++) {
            otp.append(random.nextInt(10));
        }
        return otp.toString();
    }
}