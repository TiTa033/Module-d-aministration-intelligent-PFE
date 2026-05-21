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

    @Async
    public void sendAnomalyDetectedEmail(List<String> emails, String title, int critical, int high, int total) {
        if (emails == null || emails.isEmpty()) return;
        String subject = "RaaS – Anomalies détectées : " + title;
        String body = buildAnomalyDetectedEmailTemplate(title, critical, high, total);
        for (String email : emails) {
            try {
                sendHtml(email, subject, body);
            } catch (Exception ex) {
                log.warn("Failed to send anomaly email to {}: {}", email, ex.getMessage());
            }
        }
    }

    @Async
    public void sendExternalAnalysisReadyEmail(List<String> emails, String period, String insightTitle) {
        if (emails == null || emails.isEmpty()) return;
        String subject = "RaaS – Analyse externe disponible : " + period;
        String body = buildExternalAnalysisEmailTemplate(period, insightTitle);
        for (String email : emails) {
            try {
                sendHtml(email, subject, body);
            } catch (Exception ex) {
                log.warn("Failed to send external analysis email to {}: {}", email, ex.getMessage());
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

    private String buildAnomalyDetectedEmailTemplate(String title, int critical, int high, int total) {
        return """
            <div style="font-family: Arial, sans-serif; max-width: 640px; margin: 0 auto; background: #fff5f5; border: 1px solid #fecaca; border-radius: 14px; overflow: hidden;">
              <div style="background: linear-gradient(135deg,#dc2626,#991b1b); color: #fff; padding: 20px 22px;">
                <h2 style="margin: 0; font-size: 22px;">⚠ Anomalies détectées</h2>
                <p style="margin: 6px 0 0; opacity: 0.9;">Rapport automatique – Détection d'anomalies IA</p>
              </div>
              <div style="padding: 20px 22px; color: #1e293b;">
                <p style="margin: 0 0 12px;">Bonjour,</p>
                <p style="margin: 0 0 16px;">L'analyse automatique vient de détecter des anomalies dans votre système de règles métier.</p>
                <div style="background: #fff; border: 1px solid #e2e8f0; border-radius: 12px; padding: 14px 16px; margin-bottom: 16px;">
                  <p style="margin: 0 0 8px;"><strong>Rapport :</strong> %s</p>
                  <p style="margin: 0 0 8px;"><strong>Total anomalies :</strong> %d</p>
                  <p style="margin: 0 0 8px;"><strong style="color:#991b1b;">Critique(s) :</strong> %d &nbsp;|&nbsp; <strong style="color:#b91c1c;">Élevé(s) :</strong> %d</p>
                </div>
                <p style="margin: 0 0 14px;">Connectez-vous à la plateforme pour consulter le détail et valider ou rejeter chaque anomalie.</p>
                <a href="http://localhost:4200/ai/anomaly-detection" style="display: inline-block; background: #dc2626; color: #fff; text-decoration: none; font-weight: 600; border-radius: 10px; padding: 10px 14px;">
                  Voir les anomalies
                </a>
              </div>
              <div style="padding: 12px 22px; border-top: 1px solid #fecaca; font-size: 12px; color: #64748b; background: #ffffff;">
                Message automatique RaaS - merci de ne pas répondre à cet email.
              </div>
            </div>
            """.formatted(title, total, critical, high);
    }

    private String buildExternalAnalysisEmailTemplate(String period, String insightTitle) {
        return """
            <div style="font-family: Arial, sans-serif; max-width: 640px; margin: 0 auto; background: #f0fdf4; border: 1px solid #86efac; border-radius: 14px; overflow: hidden;">
              <div style="background: linear-gradient(135deg,#16a34a,#166534); color: #fff; padding: 20px 22px;">
                <h2 style="margin: 0; font-size: 22px;">📊 Analyse externe disponible</h2>
                <p style="margin: 6px 0 0; opacity: 0.9;">Rapport automatique – Collecte de données financières</p>
              </div>
              <div style="padding: 20px 22px; color: #1e293b;">
                <p style="margin: 0 0 12px;">Bonjour,</p>
                <p style="margin: 0 0 16px;">De nouvelles données financières ont été collectées et analysées pour la période <strong>%s</strong>.</p>
                <div style="background: #fff; border: 1px solid #e2e8f0; border-radius: 12px; padding: 14px 16px; margin-bottom: 16px;">
                  <p style="margin: 0 0 8px;"><strong>Insight :</strong> %s</p>
                  <p style="margin: 0;"><strong>Période :</strong> %s</p>
                </div>
                <p style="margin: 0 0 14px;">Consultez les insights et les suggestions de règles métier dans le module Analyse Externe IA.</p>
                <a href="http://localhost:4200/ai/external-analysis" style="display: inline-block; background: #16a34a; color: #fff; text-decoration: none; font-weight: 600; border-radius: 10px; padding: 10px 14px;">
                  Voir l'analyse
                </a>
              </div>
              <div style="padding: 12px 22px; border-top: 1px solid #86efac; font-size: 12px; color: #64748b; background: #ffffff;">
                Message automatique RaaS - merci de ne pas répondre à cet email.
              </div>
            </div>
            """.formatted(period, insightTitle, period);
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

