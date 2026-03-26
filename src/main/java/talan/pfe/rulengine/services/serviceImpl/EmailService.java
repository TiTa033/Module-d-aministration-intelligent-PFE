package talan.pfe.rulengine.services.serviceImpl;

public interface EmailService {
    void sendOtpEmail(String toEmail, String otp);
}
