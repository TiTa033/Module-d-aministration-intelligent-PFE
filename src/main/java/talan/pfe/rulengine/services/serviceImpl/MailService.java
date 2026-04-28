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
import talan.pfe.rulengine.kafka.AuditProducer;
import talan.pfe.rulengine.security.CurrentUserResolver;

import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j

public class MailService {

    private final JavaMailSender mailSender;
    private final AuditProducer auditProducer;
    private final CurrentUserResolver currentUserResolver;

    public void send(String to, String subject, String text) {
        SimpleMailMessage message = new SimpleMailMessage();
        message.setTo(to);
        message.setSubject(subject);
        message.setText(text);
        mailSender.send(message);
    }

    @Async
    public void sendRuleSetActivatedEmail(List<String> recipients, String tenantName, String ruleSetName, String activatedBy) {
        if (recipients == null || recipients.isEmpty()) {
            return;
        }
        String subject = "RaaS - RuleSet activé : " + ruleSetName;
        String body = buildRuleSetActivatedEmailTemplate(tenantName, ruleSetName, activatedBy);

        for (String email : recipients) {
            try {
                sendHtml(email, subject, body);
            } catch (Exception ex) {
                log.warn("Failed to send RuleSet activation email to {}: {}", email, ex.getMessage());
            }
        }
    }

    private void sendHtml(String to, String subject, String html) throws MessagingException {
        MimeMessage message = mailSender.createMimeMessage();
        MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");
        helper.setTo(to);
        helper.setSubject(subject);
        helper.setText(html, true);
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

    private String buildRuleSetActivatedEmailTemplate(String tenantName, String ruleSetName, String activatedBy) {
        String actor = (activatedBy == null || activatedBy.isBlank()) ? "système" : activatedBy;
        String tenant = (tenantName == null || tenantName.isBlank()) ? "N/A" : tenantName;
        String ruleSet = (ruleSetName == null || ruleSetName.isBlank()) ? "N/A" : ruleSetName;

        return """
            <div style="font-family: Arial, sans-serif; max-width: 640px; margin: 0 auto; background: #f8fbff; border: 1px solid #dbeafe; border-radius: 14px; overflow: hidden;">
              <div style="background: linear-gradient(135deg,#1d4ed8,#1e40af); color: #fff; padding: 20px 22px;">
                <h2 style="margin: 0; font-size: 22px;">RaaS - RuleSet activé</h2>
                <p style="margin: 6px 0 0; opacity: 0.95;">Notification automatique de la plateforme</p>
              </div>
              <div style="padding: 20px 22px; color: #1e293b;">
                <p style="margin: 0 0 12px;">Bonjour,</p>
                <p style="margin: 0 0 16px;">
                  Le RuleSet suivant vient d'être activé et est désormais en production.
                </p>
                <div style="background: #fff; border: 1px solid #e2e8f0; border-radius: 12px; padding: 14px 16px; margin-bottom: 16px;">
                  <p style="margin: 0 0 8px;"><strong>Tenant:</strong> %s</p>
                  <p style="margin: 0 0 8px;"><strong>RuleSet:</strong> %s</p>
                  <p style="margin: 0;"><strong>Activé par:</strong> %s</p>
                </div>
                <p style="margin: 0 0 14px;">
                  Vous pouvez consulter les détails (règles, versions, documentation IA) directement dans le module RuleSets.
                </p>
                <a href="http://localhost:4200" style="display: inline-block; background: #2563eb; color: #fff; text-decoration: none; font-weight: 600; border-radius: 10px; padding: 10px 14px;">
                  Ouvrir la plateforme
                </a>
              </div>
              <div style="padding: 12px 22px; border-top: 1px solid #e2e8f0; font-size: 12px; color: #64748b; background: #ffffff;">
                Message automatique RaaS - merci de ne pas répondre à cet email.
              </div>
            </div>
            """.formatted(tenant, ruleSet, actor);
    }
}

