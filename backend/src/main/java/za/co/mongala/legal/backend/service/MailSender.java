package za.co.mongala.legal.backend.service;

import jakarta.mail.Authenticator;
import jakarta.mail.Message;
import jakarta.mail.MessagingException;
import jakarta.mail.PasswordAuthentication;
import jakarta.mail.Session;
import jakarta.mail.Transport;
import jakarta.mail.internet.InternetAddress;
import jakarta.mail.internet.MimeMessage;

import java.util.Properties;

/**
 * Sends reminder emails over SMTP. It is switched on only when LCE_SMTP_HOST and
 * LCE_SMTP_FROM are set. Without them, reminders stay queued and nothing is sent.
 *
 *   LCE_SMTP_HOST      e.g. smtp.gmail.com or your firm's mail server
 *   LCE_SMTP_PORT      default 587 (STARTTLS)
 *   LCE_SMTP_USER      optional login name
 *   LCE_SMTP_PASSWORD  optional password or app password
 *   LCE_SMTP_FROM      sender address, e.g. reminders@yourfirm.co.za
 */
public class MailSender {

    private final String host;
    private final int port;
    private final String user;
    private final String password;
    private final String from;

    public MailSender(String host, int port, String user, String password, String from) {
        this.host = host;
        this.port = port;
        this.user = user;
        this.password = password;
        this.from = from;
    }

    public static MailSender fromEnv() {
        int port = 587;
        try {
            String p = System.getenv("LCE_SMTP_PORT");
            if (p != null && !p.isBlank()) port = Integer.parseInt(p.trim());
        } catch (NumberFormatException ignored) {
            port = 587;
        }
        return new MailSender(System.getenv("LCE_SMTP_HOST"), port,
                System.getenv("LCE_SMTP_USER"), System.getenv("LCE_SMTP_PASSWORD"),
                System.getenv("LCE_SMTP_FROM"));
    }

    public boolean isConfigured() {
        return host != null && !host.isBlank() && from != null && !from.isBlank();
    }

    public void send(String to, String subject, String body) throws MessagingException {
        if (!isConfigured()) throw new MessagingException("Mail server not configured (LCE_SMTP_HOST / LCE_SMTP_FROM)");
        Properties p = new Properties();
        p.put("mail.smtp.host", host);
        p.put("mail.smtp.port", String.valueOf(port));
        p.put("mail.smtp.starttls.enable", "true");
        p.put("mail.smtp.connectiontimeout", "10000");
        p.put("mail.smtp.timeout", "10000");
        Session session;
        if (user != null && !user.isBlank()) {
            p.put("mail.smtp.auth", "true");
            session = Session.getInstance(p, new Authenticator() {
                @Override
                protected PasswordAuthentication getPasswordAuthentication() {
                    return new PasswordAuthentication(user, password == null ? "" : password);
                }
            });
        } else {
            session = Session.getInstance(p);
        }
        MimeMessage msg = new MimeMessage(session);
        msg.setFrom(new InternetAddress(from));
        msg.setRecipients(Message.RecipientType.TO, InternetAddress.parse(to));
        msg.setSubject(subject, "UTF-8");
        msg.setText(body, "UTF-8");
        Transport.send(msg);
    }
}
