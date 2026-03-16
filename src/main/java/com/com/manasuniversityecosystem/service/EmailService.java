package com.com.manasuniversityecosystem.service;

import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.util.Optional;

@Service
@Slf4j
public class EmailService {

    // Optional — null if MAIL_USERNAME is not configured
    private final Optional<JavaMailSender> mailSender;

    @Value("${app.mail.from:noreply@manas.edu}")
    private String fromAddress;

    @Value("${app.mail.from-name:ManasMezun System}")
    private String fromName;

    @Value("${spring.mail.username:}")
    private String mailUsername;

    public EmailService(Optional<JavaMailSender> mailSender) {
        this.mailSender = mailSender;
    }

    private boolean isMailConfigured() {
        return mailSender.isPresent() && mailUsername != null && !mailUsername.isBlank();
    }

    @Async
    public void sendHtml(String toEmail, String subject, String htmlBody) {
        if (!isMailConfigured()) {
            log.info("[Email] Mail not configured — skipping send to {} ({})", toEmail, subject);
            return;
        }
        try {
            MimeMessage msg = mailSender.get().createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(msg, true, "UTF-8");
            helper.setFrom(fromAddress, fromName);
            helper.setTo(toEmail);
            helper.setSubject(subject);
            helper.setText(htmlBody, true);
            mailSender.get().send(msg);
            log.info("[Email] Sent '{}' to {}", subject, toEmail);
        } catch (MessagingException | java.io.UnsupportedEncodingException e) {
            log.warn("[Email] Failed to send to {}: {}", toEmail, e.getMessage());
        } catch (Exception e) {
            log.warn("[Email] Unexpected error sending to {}: {}", toEmail, e.getMessage());
        }
    }

    @Async
    public void sendPasswordResetNotification(String toEmail, String userName, String adminName) {
        String subject = "Your password has been reset - ManasMezun";
        String html = """
            <!DOCTYPE html><html><body style="font-family:Arial,sans-serif;background:#f4f6fb;padding:32px;">
              <div style="max-width:520px;margin:0 auto;background:#fff;border-radius:16px;box-shadow:0 4px 24px rgba(27,42,107,.10);overflow:hidden;">
                <div style="background:linear-gradient(135deg,#1B2A6B,#2d4a9e);padding:28px 32px;text-align:center;">
                  <div style="font-weight:900;font-size:24px;color:#fff;">Manas<span style="color:#ef4444;">Mezun</span></div>
                </div>
                <div style="padding:32px;">
                  <div style="font-size:42px;text-align:center;margin-bottom:16px;">🔑</div>
                  <h2 style="color:#1B2A6B;text-align:center;margin:0 0 12px;">Password Reset</h2>
                  <p style="color:#475569;font-size:14px;line-height:1.7;">Hello <strong>%s</strong>,</p>
                  <p style="color:#475569;font-size:14px;line-height:1.7;">Your password on <strong>ManasMezun</strong> has been reset by administrator <strong>%s</strong>.</p>
                  <div style="background:#fef9c3;border:1.5px solid #fbbf24;border-radius:10px;padding:14px 18px;margin:16px 0;font-size:13px;color:#92400e;">
                    <strong>⚠️ Important:</strong> Please log in with your new password. If you did not request this, contact an administrator immediately.
                  </div>
                  <p style="color:#94a3b8;font-size:12px;text-align:center;">This is an automated message. Please do not reply.</p>
                </div>
              </div>
            </body></html>""".formatted(userName, adminName);
        sendHtml(toEmail, subject, html);
    }

    @Async
    public void sendPasswordResetRequestConfirmation(String toEmail, String userName) {
        String subject = "Password reset request received - ManasMezun";
        String html = """
            <!DOCTYPE html><html><body style="font-family:Arial,sans-serif;background:#f4f6fb;padding:32px;">
              <div style="max-width:520px;margin:0 auto;background:#fff;border-radius:16px;box-shadow:0 4px 24px rgba(27,42,107,.10);overflow:hidden;">
                <div style="background:linear-gradient(135deg,#1B2A6B,#2d4a9e);padding:28px 32px;text-align:center;">
                  <div style="font-weight:900;font-size:24px;color:#fff;">Manas<span style="color:#ef4444;">Mezun</span></div>
                </div>
                <div style="padding:32px;">
                  <div style="font-size:42px;text-align:center;margin-bottom:16px;">📨</div>
                  <h2 style="color:#1B2A6B;text-align:center;margin:0 0 12px;">Request Received</h2>
                  <p style="color:#475569;font-size:14px;line-height:1.7;">Hello <strong>%s</strong>,</p>
                  <p style="color:#475569;font-size:14px;line-height:1.7;">Your password reset request has been sent to our administrators. They will reset your password and contact you shortly.</p>
                  <div style="background:#eff6ff;border:1.5px solid #93c5fd;border-radius:10px;padding:14px 18px;margin:16px 0;font-size:13px;color:#1e40af;">
                    <strong>ℹ️ What happens next:</strong> An admin will reset your password and you will receive a confirmation email.
                  </div>
                  <p style="color:#94a3b8;font-size:12px;text-align:center;">This is an automated message. Please do not reply.</p>
                </div>
              </div>
            </body></html>""".formatted(userName);
        sendHtml(toEmail, subject, html);
    }
}