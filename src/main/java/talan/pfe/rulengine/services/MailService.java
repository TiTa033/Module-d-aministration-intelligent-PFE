package talan.pfe.rulengine.services;

public interface MailService {
    void send(String to, String subject, String text);
}
