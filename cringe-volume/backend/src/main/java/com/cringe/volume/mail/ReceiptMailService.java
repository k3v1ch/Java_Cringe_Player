package com.cringe.volume.mail;

import com.cringe.volume.config.MailConfig;
import com.cringe.volume.payment.Payment;
import jakarta.mail.internet.MimeMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

@Service
public class ReceiptMailService {

    private static final Logger log = LoggerFactory.getLogger(ReceiptMailService.class);
    private static final DateTimeFormatter FMT =
            DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm").withZone(ZoneId.systemDefault());

    private final JavaMailSender mailSender;
    private final MailConfig mailConfig;

    public ReceiptMailService(JavaMailSender mailSender, MailConfig mailConfig) {
        this.mailSender = mailSender;
        this.mailConfig = mailConfig;
    }

    /**
     * Отправляет шуточный «чек» на указанный email.
     * Сбой SMTP — только лог, на статус платежа НЕ влияет.
     */
    @Async
    public void sendReceipt(String recipientEmail, Payment payment) {
        if (recipientEmail == null || recipientEmail.isBlank()) {
            return;
        }
        if (!mailConfig.isConfigured()) {
            log.warn("SMTP не настроен — чек не отправлен для {}", payment.getToken());
            return;
        }

        try {
            MimeMessage msg = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(msg, "UTF-8");

            String from = mailConfig.getFromName() + " <" + mailConfig.getFromEmail() + ">";
            helper.setFrom(from);
            helper.setTo(recipientEmail);
            helper.setSubject("Чек об оплате — Cringe Volume Player 🤡");
            helper.setText(buildReceiptBody(payment), true);

            mailSender.send(msg);
            log.info("Чек отправлен на {} для платежа {}", recipientEmail, payment.getToken());
        } catch (Exception e) {
            log.error("Не удалось отправить чек на {}: {}", recipientEmail, e.getMessage());
        }
    }

    private String buildReceiptBody(Payment payment) {
        return """
                <div style="font-family:sans-serif;max-width:480px;margin:0 auto;padding:24px;
                            background:#1a1a2e;color:#e0e0e0;border-radius:12px">
                  <h2 style="text-align:center;color:#e94560">
                    🤡 Cringe Volume Player
                  </h2>
                  <p style="text-align:center;color:#a0a0b0;font-size:13px">
                    Чек об оплате изменения громкости
                  </p>
                  <hr style="border-color:#333"/>
                  <table style="width:100%%;font-size:14px;color:#ccc">
                    <tr><td>Описание</td><td style="text-align:right">%s</td></tr>
                    <tr><td>Базовая цена</td><td style="text-align:right">%d ₽</td></tr>
                    <tr><td>Комиссия сервиса</td><td style="text-align:right">%d ₽</td></tr>
                    <tr><td style="font-weight:bold;color:#e94560">Итого</td>
                        <td style="text-align:right;font-weight:bold;color:#e94560">%d ₽</td></tr>
                  </table>
                  <hr style="border-color:#333"/>
                  <p style="font-size:12px;color:#666">
                    Payment ID: %s<br/>
                    Дата: %s
                  </p>
                  <p style="font-size:11px;color:#555;text-align:center">
                    Это шуточный чек лабораторной работы. Реальные деньги не списывались.
                  </p>
                </div>
                """.formatted(
                payment.getDescription(),
                payment.getBasePrice(),
                payment.getCommission(),
                payment.getTotalAmount(),
                payment.getToken(),
                FMT.format(payment.getCreatedAt())
        );
    }
}
