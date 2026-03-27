package talan.pfe.rulengine.services.serviceImpl;

import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Slf4j

public class MailService {

    private final JavaMailSender mailSender;

    public void send(String to, String subject, String text) {
        SimpleMailMessage message = new SimpleMailMessage();
        message.setTo(to);
        message.setSubject(subject);
        message.setText(text);
        mailSender.send(message);
    }
    @Async
    public void sendOtpEmail(String toEmail, String otp) {
        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(
                    message, true, "UTF-8");

            helper.setTo(toEmail);
            helper.setSubject("RaaS Platform — Code de vérification");
            helper.setText(buildOtpEmailTemplate(otp), true);

            mailSender.send(message);
            log.info("OTP email sent to {}", toEmail);

        } catch (MessagingException e) {
            log.error("Failed to send OTP email to {}: {}", toEmail, e.getMessage());
            throw new RuntimeException("Failed to send OTP email");
        }
    }

    private String buildOtpEmailTemplate(String otp) {
        return """
            <div style="font-family: Arial, sans-serif; max-width: 500px; margin: 0 auto;">
                <div style="background-color: #1976d2; padding: 20px; text-align: center;">
                    <h1 style="color: white; margin: 0;">RaaS Platform</h1>
                </div>
                <div style="padding: 30px; background-color: #f9f9f9;">
                    <h2 style="color: #333;">Code de vérification</h2>
                    <p style="color: #666;">
                        Utilisez ce code pour vous connecter à votre compte.
                        Il expire dans <strong>5 minutes</strong>.
                    </p>
                    <div style="text-align: center; margin: 30px 0;">
                        <span style="font-size: 36px; font-weight: bold;
                                     letter-spacing: 8px; color: #1976d2;
                                     background: #e3f2fd; padding: 15px 25px;
                                     border-radius: 8px;">
                            %s
                        </span>
                    </div>
                    <p style="color: #999; font-size: 12px;">
                        Si vous n'avez pas demandé ce code, ignorez cet email.
                    </p>
                </div>
            </div>
            """.formatted(otp);
    }
}

