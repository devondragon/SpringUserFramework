package com.digitalsanctuary.spring.user.test.config;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.context.annotation.Profile;
import org.springframework.mail.MailException;
import org.springframework.mail.MailSender;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessagePreparator;

import jakarta.mail.internet.MimeMessage;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Mail configuration for testing that captures sent emails instead of sending them.
 * This allows tests to verify email content and recipients.
 */
@TestConfiguration
@Profile("test")
public class MockMailConfiguration {

    /**
     * Mock mail sender that captures emails for verification.
     */
    @Bean
    @Primary
    public JavaMailSender mockMailSender() {
        return new MockJavaMailSender();
    }

    /**
     * Test mail capture service for verifying sent emails.
     */
    @Bean
    public TestMailCapture testMailCapture() {
        return new TestMailCapture();
    }

    /**
     * Mock implementation of JavaMailSender that captures emails.
     */
    public static class MockJavaMailSender implements JavaMailSender {
        
        private final List<SimpleMailMessage> sentSimpleMessages = new CopyOnWriteArrayList<>();
        private final List<MimeMessage> sentMimeMessages = new CopyOnWriteArrayList<>();
        private final List<MimeMessagePreparator> sentPreparators = new CopyOnWriteArrayList<>();

        @Override
        public MimeMessage createMimeMessage() {
            return null; // Will be mocked when needed
        }

        @Override
        public MimeMessage createMimeMessage(InputStream contentStream) throws MailException {
            return null; // Will be mocked when needed
        }

        @Override
        public void send(MimeMessage mimeMessage) throws MailException {
            sentMimeMessages.add(mimeMessage);
        }

        @Override
        public void send(MimeMessage... mimeMessages) throws MailException {
            Collections.addAll(sentMimeMessages, mimeMessages);
        }

        @Override
        public void send(MimeMessagePreparator mimeMessagePreparator) throws MailException {
            sentPreparators.add(mimeMessagePreparator);
        }

        @Override
        public void send(MimeMessagePreparator... mimeMessagePreparators) throws MailException {
            Collections.addAll(sentPreparators, mimeMessagePreparators);
        }

        @Override
        public void send(SimpleMailMessage simpleMessage) throws MailException {
            sentSimpleMessages.add(simpleMessage);
        }

        @Override
        public void send(SimpleMailMessage... simpleMessages) throws MailException {
            Collections.addAll(sentSimpleMessages, simpleMessages);
        }

        /**
         * Get all sent simple messages.
         */
        public List<SimpleMailMessage> getSentSimpleMessages() {
            return new ArrayList<>(sentSimpleMessages);
        }

        /**
         * Get all sent MIME messages.
         */
        public List<MimeMessage> getSentMimeMessages() {
            return new ArrayList<>(sentMimeMessages);
        }

        /**
         * Clear all captured messages.
         */
        public void clear() {
            sentSimpleMessages.clear();
            sentMimeMessages.clear();
            sentPreparators.clear();
        }
    }

    /**
     * Service for capturing and verifying test emails.
     */
    public static class TestMailCapture {
        
        private final List<CapturedEmail> capturedEmails = new CopyOnWriteArrayList<>();

        /**
         * Capture an email for verification.
         */
        public void captureEmail(String to, String subject, String content) {
            capturedEmails.add(new CapturedEmail(to, subject, content));
        }

        /**
         * Get all captured emails.
         */
        public List<CapturedEmail> getCapturedEmails() {
            return new ArrayList<>(capturedEmails);
        }

        /**
         * Find emails by recipient.
         */
        public List<CapturedEmail> findEmailsByRecipient(String recipient) {
            return capturedEmails.stream()
                    .filter(email -> email.getTo().equals(recipient))
                    .toList();
        }

        /**
         * Find emails by subject.
         */
        public List<CapturedEmail> findEmailsBySubject(String subject) {
            return capturedEmails.stream()
                    .filter(email -> email.getSubject().contains(subject))
                    .toList();
        }

        /**
         * Clear all captured emails.
         */
        public void clear() {
            capturedEmails.clear();
        }

        /**
         * Check if an email was sent to a recipient.
         */
        public boolean wasEmailSentTo(String recipient) {
            return capturedEmails.stream()
                    .anyMatch(email -> email.getTo().equals(recipient));
        }

        /**
         * Get the count of captured emails.
         */
        public int getEmailCount() {
            return capturedEmails.size();
        }
    }

    /**
     * Represents a captured email for testing.
     */
    public static class CapturedEmail {
        private final String to;
        private final String subject;
        private final String content;
        private final long timestamp;

        public CapturedEmail(String to, String subject, String content) {
            this.to = to;
            this.subject = subject;
            this.content = content;
            this.timestamp = System.currentTimeMillis();
        }

        public String getTo() {
            return to;
        }

        public String getSubject() {
            return subject;
        }

        public String getContent() {
            return content;
        }

        public long getTimestamp() {
            return timestamp;
        }
    }
}