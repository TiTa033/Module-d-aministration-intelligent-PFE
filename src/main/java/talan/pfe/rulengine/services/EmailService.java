package talan.pfe.rulengine.services;

public interface EmailService {
    void sendOtpEmail(String toEmail, String otp);
}
