package talan.pfe.rulengine.services.serviceImpl;

public interface MailService {
    void send(String to, String subject, String text);
}
