package talan.pfe.rulengine.services;

public interface CaptchaService {
    void verify(String captchaToken);
}
