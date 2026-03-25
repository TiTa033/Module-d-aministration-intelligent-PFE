package talan.pfe.rulengine.services;

public interface OtpService {
    String generateAndStore(String email);
    void verify(String email, String otp);
    boolean hasActiveOtp(String email);
}
