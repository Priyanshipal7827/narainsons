package com.narainsons.service;

import java.util.Properties;
import java.util.logging.Level;
import java.util.logging.Logger;

import com.narainsons.config.SecretsConfig;

import jakarta.mail.Authenticator;
import jakarta.mail.Message;
import jakarta.mail.PasswordAuthentication;
import jakarta.mail.Session;
import jakarta.mail.Transport;
import jakarta.mail.internet.InternetAddress;
import jakarta.mail.internet.MimeMessage;

public class MailService {

    private static final Logger LOG = Logger.getLogger(MailService.class.getName());

    public boolean sendOtpEmail(String toEmail, String otp) {
        try {
            Properties props = new Properties();
            props.put("mail.smtp.auth", "true");
            props.put("mail.smtp.starttls.enable", "true");
            props.put("mail.smtp.host", SecretsConfig.get("mail.host"));
            props.put("mail.smtp.port", SecretsConfig.get("mail.port"));

            String username = SecretsConfig.get("mail.username");
            String password = SecretsConfig.get("mail.password");
            Session session = Session.getInstance(props, new Authenticator() {
                @Override
                protected PasswordAuthentication getPasswordAuthentication() {
                    return new PasswordAuthentication(username, password);
                }
            });

            String fromName = SecretsConfig.get("mail.from.name");
            String fromAddress = SecretsConfig.get("mail.from.address");
            Message message = new MimeMessage(session);
            message.setFrom(new InternetAddress(fromAddress, fromName));
            message.setRecipients(Message.RecipientType.TO, InternetAddress.parse(toEmail));
            message.setSubject(fromName + " — Email OTP");
            message.setText("Aapka OTP: " + otp + "\n\nValid for 10 minutes.");

            Transport.send(message);
            return true;
        } catch (Exception e) {
            LOG.log(Level.WARNING, "Email OTP send fail", e);
            return false;
        }
    }
}
