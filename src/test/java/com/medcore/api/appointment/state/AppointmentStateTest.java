package com.medcore.api.appointment.state;

import com.medcore.api.appointment.Appointment;
import com.medcore.api.appointment.AppointmentStatus;
import com.medcore.api.support.TestFixtures;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AppointmentStateTest {

    private static final Instant TOMORROW = Instant.now().plus(Duration.ofDays(1));
    private static final Instant YESTERDAY = Instant.now().minus(Duration.ofDays(1));

    @Nested
    @DisplayName("PENDING")
    class Pending {

        @Test
        void canBeConfirmed() {
            Appointment appointment = TestFixtures.appointment(TOMORROW);
            appointment.confirm();
            assertThat(appointment.getStatus()).isEqualTo(AppointmentStatus.CONFIRMED);
        }

        @Test
        void canBeCancelledWithReason() {
            Appointment appointment = TestFixtures.appointment(TOMORROW);
            appointment.cancel("Travelling");
            assertThat(appointment.getStatus()).isEqualTo(AppointmentStatus.CANCELLED);
            assertThat(appointment.getCancellationReason()).isEqualTo("Travelling");
        }

        @Test
        void cannotBeCompleted() {
            Appointment appointment = TestFixtures.appointment(TOMORROW);
            assertThatThrownBy(appointment::complete).isInstanceOf(InvalidStateTransitionException.class);
        }

        @Test
        void rescheduleUpdatesTimeWindow() {
            Appointment appointment = TestFixtures.appointment(TOMORROW);
            Instant newStart = TOMORROW.plus(Duration.ofHours(2));
            appointment.reschedule(newStart, 45);
            assertThat(appointment.getScheduledAt()).isEqualTo(newStart);
            assertThat(appointment.getEndsAt()).isEqualTo(newStart.plus(Duration.ofMinutes(45)));
            assertThat(appointment.getStatus()).isEqualTo(AppointmentStatus.PENDING);
        }
    }

    @Nested
    @DisplayName("CONFIRMED")
    class Confirmed {

        @Test
        void completesOnlyOnceStarted() {
            Appointment future = TestFixtures.appointment(TOMORROW);
            future.confirm();
            assertThatThrownBy(future::complete).isInstanceOf(InvalidStateTransitionException.class);

            Appointment past = TestFixtures.appointment(YESTERDAY);
            past.confirm();
            past.complete();
            assertThat(past.getStatus()).isEqualTo(AppointmentStatus.COMPLETED);
        }

        @Test
        void rescheduleReturnsToPending() {
            Appointment appointment = TestFixtures.appointment(TOMORROW);
            appointment.confirm();
            appointment.reschedule(TOMORROW.plus(Duration.ofDays(1)), 30);
            assertThat(appointment.getStatus()).isEqualTo(AppointmentStatus.PENDING);
        }

        @Test
        void cannotBeConfirmedTwice() {
            Appointment appointment = TestFixtures.appointment(TOMORROW);
            appointment.confirm();
            assertThatThrownBy(appointment::confirm)
                    .isInstanceOf(InvalidStateTransitionException.class)
                    .hasMessageContaining("CONFIRMED");
        }
    }

    @Nested
    @DisplayName("terminal states")
    class Terminal {

        @Test
        void cancelledRejectsEveryTransition() {
            Appointment appointment = TestFixtures.appointment(TOMORROW);
            appointment.cancel("No longer needed");

            assertThatThrownBy(appointment::confirm).isInstanceOf(InvalidStateTransitionException.class);
            assertThatThrownBy(() -> appointment.cancel("again")).isInstanceOf(InvalidStateTransitionException.class);
            assertThatThrownBy(() -> appointment.reschedule(TOMORROW, 30)).isInstanceOf(InvalidStateTransitionException.class);
        }

        @Test
        void completedRejectsCancellation() {
            Appointment appointment = TestFixtures.appointment(YESTERDAY);
            appointment.confirm();
            appointment.complete();
            assertThatThrownBy(() -> appointment.cancel("late")).isInstanceOf(InvalidStateTransitionException.class);
        }
    }

    @Test
    void everyStatusHasAState() {
        for (AppointmentStatus status : AppointmentStatus.values()) {
            assertThat(AppointmentStates.of(status)).isNotNull()
                    .extracting(AppointmentState::status).isEqualTo(status);
        }
    }
}
