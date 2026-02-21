package com.smartstock.backend.service;

import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

@Service
public class EmailService {

    private final JavaMailSender mailSender;

    public EmailService(JavaMailSender mailSender) {
        this.mailSender = mailSender;
    }

    public void sendEmail(String to, String topic, String content) {
        SimpleMailMessage email = new SimpleMailMessage();

        email.setFrom("projectstock77@gmail.com");
        email.setTo(to);
        email.setSubject(topic);
        email.setText(content);

        mailSender.send(email);
    }
}
