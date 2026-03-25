package talan.pfe.rulengine.services.serviceImpl;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import talan.pfe.rulengine.exception.BadRequestException;

import java.util.HashMap;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Slf4j
public class CaptchaServiceImpl implements talan.pfe.rulengine.services.CaptchaService {

    @Value("${recaptcha.secret-key}")
    private String secretKey;

    @Value("${recaptcha.verify-url}")
    private String verifyUrl;

    @Value("${recaptcha.min-score}")
    private double minScore;
    @Value("${recaptcha.enabled:true}")
    private boolean enabled;
    public void verify(String captchaToken) {
        if (!enabled) return;
        if (captchaToken == null || captchaToken.isBlank()) {
            throw new BadRequestException("Captcha token is required");
        }

        RestTemplate restTemplate = new RestTemplate();

        String url = verifyUrl + "?secret=" + secretKey
                + "&response=" + captchaToken;

        Map<String, Object> response = restTemplate.postForObject(
                url, null, HashMap.class);

        if (response == null) {
            throw new BadRequestException("Captcha verification failed");
        }

        boolean success = (boolean) response.getOrDefault("success", false);
        double score = ((Number) response.getOrDefault("score", 0.0))
                .doubleValue();

        log.info("reCAPTCHA score: {}", score);

        if (!success || score < minScore) {
            throw new BadRequestException(
                    "Captcha verification failed. Please try again.");
        }
    }
}