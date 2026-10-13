package com.medcore.api.appointment;

import com.medcore.api.config.MedcoreProperties;
import com.medcore.api.integration.sms.SmsSender;
import com.medcore.api.messaging.AppointmentEvent;
import com.medcore.api.messaging.KafkaTopics;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * Periodically sends reminders for confirmed appointments starting within the configured lead time.
 * A short Redis lock ensures only one instance runs the job when the API is scaled horizontally.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AppointmentReminderJob {

    private static final String LOCK_KEY = "medcore:lock:appointment-reminders";
    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("dd/MM HH:mm");

    private final AppointmentRepository appointmentRepository;
    private final ApplicationEventPublisher events;
    private final SmsSender smsSender;
    private final StringRedisTemplate redis;
    private final MedcoreProperties properties;
    private final Clock clock;

    @Scheduled(cron = "${medcore.appointments.reminder-cron}")
    @Transactional
    public void sendReminders() {
        Boolean acquired = redis.opsForValue().setIfAbsent(LOCK_KEY, "1", Duration.ofMinutes(10));
        if (!Boolean.TRUE.equals(acquired)) {
            return;
        }
        try {
            Instant now = clock.instant();
            List<Appointment> due = appointmentRepository.findDueForReminder(now, now.plus(properties.appointments().reminderLeadTime()));
            for (Appointment appointment : due) {
                appointment.markReminderSent(now);
                events.publishEvent(AppointmentEvent.of(KafkaTopics.APPOINTMENT_REMINDER, appointment));
                String phone = appointment.getPatient().getPhone();
                if (phone != null) {
                    ZoneId zone = ZoneId.of(appointment.getDoctor().getClinic().getTimezone());
                    smsSender.send(phone, "MedCore: reminder of your appointment with %s on %s."
                            .formatted(appointment.getDoctor().fullName(), TIME.format(appointment.getScheduledAt().atZone(zone))));
                }
            }
            if (!due.isEmpty()) {
                log.info("Sent {} appointment reminders", due.size());
            }
        } finally {
            redis.delete(LOCK_KEY);
        }
    }
}
