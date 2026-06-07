package com.swp391.backend.service;

import com.swp391.backend.entity.AppUser;
import jakarta.mail.internet.AddressException;
import jakarta.mail.internet.InternetAddress;
import jakarta.mail.internet.MimeMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.MailException;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;

import java.io.UnsupportedEncodingException;

@Service
public class VerificationEmailService {
    private static final Logger LOGGER = LoggerFactory.getLogger(VerificationEmailService.class);

    private final JavaMailSender mailSender;
    private final String frontendBaseUrl;
    private final String fromEmail;
    private final String fromName;
    private final String smtpUsername;

    public VerificationEmailService(
            JavaMailSender mailSender,
            @Value("${app.frontend-base-url:http://localhost:5173}") String frontendBaseUrl,
            @Value("${app.mail.from-email:}") String fromEmail,
            @Value("${app.mail.from-name:GoalZone}") String fromName,
            @Value("${spring.mail.username:}") String smtpUsername
    ) {
        this.mailSender = mailSender;
        this.frontendBaseUrl = stripTrailingSlash(frontendBaseUrl);
        this.fromEmail = blankToFallback(fromEmail, smtpUsername);
        this.fromName = blankToFallback(fromName, "GoalZone");
        this.smtpUsername = smtpUsername;
    }

    public String verificationLink(AppUser user) {
        return frontendBaseUrl
                + "/verify-email?userId=" + user.getUserId()
                + "&token=" + user.getEmailVerificationToken();
    }

    public VerificationEmailDelivery sendVerificationEmail(AppUser user) {
        if (isBlank(smtpUsername) || isBlank(fromEmail)) {
            return new VerificationEmailDelivery(
                    false,
                    "not_configured",
                    "Verification email was not sent because SMTP is not configured."
            );
        }

        String link = verificationLink(user);
        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");
            helper.setTo(user.getEmail());
            helper.setFrom(senderAddress());
            helper.setSubject("Verify your GoalZone account");
            helper.setText(plainText(user, link), htmlText(user, link));
            mailSender.send(message);
            return new VerificationEmailDelivery(
                    true,
                    "sent",
                    "Verification email sent. Please check your inbox."
            );
        } catch (MailException | AddressException | UnsupportedEncodingException exception) {
            logMailFailure(exception);
            throw new ApiException(
                    org.springframework.http.HttpStatus.BAD_GATEWAY,
                    "Could not send verification email. Check SMTP settings and try again."
            );
        } catch (Exception exception) {
            logMailFailure(exception);
            throw new ApiException(
                    org.springframework.http.HttpStatus.BAD_GATEWAY,
                    "Could not send verification email. Check SMTP settings and try again."
            );
        }
    }

    private void logMailFailure(Exception exception) {
        Throwable root = exception;
        while (root.getCause() != null) {
            root = root.getCause();
        }
        LOGGER.warn(
                "Verification email delivery failed: {} - {}",
                root.getClass().getSimpleName(),
                root.getMessage()
        );
    }

    private InternetAddress senderAddress() throws AddressException, UnsupportedEncodingException {
        if (fromEmail.contains("<")) {
            InternetAddress[] parsedAddresses = InternetAddress.parse(fromEmail, false);
            if (parsedAddresses.length > 0) {
                InternetAddress parsedAddress = parsedAddresses[0];
                String personal = blankToFallback(fromName, parsedAddress.getPersonal());
                return new InternetAddress(parsedAddress.getAddress(), personal);
            }
        }
        return new InternetAddress(fromEmail, fromName);
    }

    private String plainText(AppUser user, String link) {
        return "Hi " + user.getFullName() + ",\n\n"
                + "Please verify your GoalZone account by opening this link:\n"
                + link + "\n\n"
                + "If you did not create this account, you can ignore this email.";
    }

    private String htmlText(AppUser user, String link) {
        String name = escapeHtml(user.getFullName());
        String safeLink = escapeHtml(link);
        return "<div style=\"font-family:Arial,sans-serif;line-height:1.5;color:#17211b;max-width:560px\">"
                + "<h2 style=\"margin:0 0 12px\">Verify your GoalZone account</h2>"
                + "<p>Hi " + name + ",</p>"
                + "<p>Please confirm this email address so you can book football fields online.</p>"
                + "<p><a href=\"" + safeLink + "\" style=\"display:inline-block;background:#167a42;color:#fff;"
                + "padding:12px 18px;border-radius:6px;text-decoration:none;font-weight:700\">Verify email</a></p>"
                + "<p>If the button does not work, copy this link into your browser:</p>"
                + "<p><a href=\"" + safeLink + "\">" + safeLink + "</a></p>"
                + "<p style=\"color:#607062;font-size:13px\">If you did not create this account, ignore this email.</p>"
                + "</div>";
    }

    private String stripTrailingSlash(String value) {
        String cleanValue = blankToFallback(value, "http://localhost:5173").trim();
        while (cleanValue.endsWith("/")) {
            cleanValue = cleanValue.substring(0, cleanValue.length() - 1);
        }
        return cleanValue;
    }

    private String blankToFallback(String value, String fallback) {
        return isBlank(value) ? fallback : value.trim();
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private String escapeHtml(String value) {
        if (value == null) {
            return "";
        }
        return value
                .replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;");
    }
}
