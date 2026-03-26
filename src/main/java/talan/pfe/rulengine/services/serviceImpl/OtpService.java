package talan.pfe.rulengine.services.serviceImpl;

public interface OtpService {
    String generateAndStore(String email);
    void verify(String email, String otp);
    boolean hasActiveOtp(String email);
}
