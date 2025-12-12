package net.fonteyne.jtimekeeper;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Objects;

/**
 * Tracks daily time allowance and usage for a user session.
 * <p>
 * This class maintains the state of a user's time tracking, including:
 * <ul>
 *   <li>The current day being tracked</li>
 *   <li>Remaining minutes for the current session</li>
 *   <li>Default daily time allowance in minutes</li>
 *   <li>Timestamp of the last logon event</li>
 * </ul>
 * <p>
 * Time counters are reset daily to the default allowance.
 */
public class TimeCounter {
    private LocalDate day;
    private int remainingMinutes;
    private int defaultMinutes;
    private LocalDateTime lastLogOn;

    /**
     * Creates a new TimeCounter with default values.
     */
    public TimeCounter() {
        this.day = LocalDate.now();
        this.remainingMinutes = 0;
        this.defaultMinutes = 0;
        this.lastLogOn = LocalDateTime.now();
    }

    /**
     * Creates a new TimeCounter with specified values.
     *
     * @param defaultMinutes the daily time allowance in minutes
     */
    public TimeCounter(int defaultMinutes) {
        this.day = LocalDate.now();
        this.remainingMinutes = defaultMinutes;
        this.defaultMinutes = defaultMinutes;
        this.lastLogOn = LocalDateTime.now();
    }

    /**
     * Sets the day for this time counter.
     *
     * @param day the date to set for this counter
     * @throws NullPointerException if day is null
     */
    public void setDay(LocalDate day) {
        this.day = Objects.requireNonNull(day, "Day cannot be null");
    }

    /**
     * Gets the day associated with this time counter.
     *
     * @return the date for this counter
     */
    public LocalDate getDay() {
        return this.day;
    }

    /**
     * Sets the remaining minutes for the current session.
     *
     * @param minutes the number of minutes to set (must be non-negative)
     * @throws IllegalArgumentException if minutes is negative
     */
    public void setMinutes(int minutes) {
        if (minutes < 0) {
            throw new IllegalArgumentException("Minutes cannot be negative: " + minutes);
        }
        this.remainingMinutes = minutes;
    }

    /**
     * Gets the remaining minutes for the current session.
     *
     * @return the number of remaining minutes
     */
    public int getMinutes() {
        return this.remainingMinutes;
    }

    /**
     * Sets the default daily time allowance in minutes.
     *
     * @param defaultMinutes the default number of minutes to set (must be non-negative)
     * @throws IllegalArgumentException if defaultMinutes is negative
     */
    public void setDefaultMinutes(int defaultMinutes) {
        if (defaultMinutes < 0) {
            throw new IllegalArgumentException("Default minutes cannot be negative: " + defaultMinutes);
        }
        this.defaultMinutes = defaultMinutes;
    }

    /**
     * Gets the default daily time allowance in minutes.
     *
     * @return the default number of minutes
     */
    public int getDefaultMinutes() {
        return this.defaultMinutes;
    }

    /**
     * Sets the last logon timestamp.
     *
     * @param lastLogOn the date and time of the last logon
     * @throws NullPointerException if lastLogOn is null
     */
    public void setLastLogOn(LocalDateTime lastLogOn) {
        this.lastLogOn = Objects.requireNonNull(lastLogOn, "Last logon time cannot be null");
    }

    /**
     * Gets the last logon timestamp.
     *
     * @return the date and time of the last logon
     */
    public LocalDateTime getLastLogOn() {
        return this.lastLogOn;
    }

    /**
     * Checks if the time counter has expired (no remaining minutes).
     *
     * @return true if remaining minutes is zero, false otherwise
     */
    public boolean hasExpired() {
        return this.remainingMinutes == 0;
    }

    /**
     * Checks if the time counter has remaining time.
     *
     * @return true if remaining minutes is greater than zero, false otherwise
     */
    public boolean hasRemainingTime() {
        return this.remainingMinutes > 0;
    }

    /**
     * Resets the remaining minutes to the default daily allowance.
     */
    public void resetToDefault() {
        this.remainingMinutes = this.defaultMinutes;
        this.day = LocalDate.now();
    }

    /**
     * Checks if this time counter is for today.
     *
     * @return true if the day matches today's date, false otherwise
     */
    public boolean isToday() {
        return this.day.equals(LocalDate.now());
    }

    /**
     * Returns a string representation of this TimeCounter.
     *
     * @return a string containing all field values of this TimeCounter
     */
    @Override
    public String toString() {
        return String.format("TimeCounter{day=%s, remainingMinutes=%d, defaultMinutes=%d, lastLogOn=%s}",
                day, remainingMinutes, defaultMinutes, lastLogOn);
    }

    /**
     * Checks if this TimeCounter is equal to another object.
     *
     * @param obj the object to compare with
     * @return true if the objects are equal, false otherwise
     */
    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (obj == null || getClass() != obj.getClass()) return false;
        
        TimeCounter that = (TimeCounter) obj;
        return remainingMinutes == that.remainingMinutes &&
               defaultMinutes == that.defaultMinutes &&
               Objects.equals(day, that.day) &&
               Objects.equals(lastLogOn, that.lastLogOn);
    }

    /**
     * Returns a hash code value for this TimeCounter.
     *
     * @return a hash code value
     */
    @Override
    public int hashCode() {
        return Objects.hash(day, remainingMinutes, defaultMinutes, lastLogOn);
    }
}
