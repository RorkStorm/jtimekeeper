
/**
 * Represents a time counter that tracks daily time usage and logon information.
 */
class TimeCounter {
    private LocalDate Day;
    private int Minutes;
    private int DefaultMinutes;
    private LocalDateTime LastLogOn;

    /**
     * Sets the day for this time counter.
     *
     * @param day the date to set for this counter
     */
    public void setDay(LocalDate day) {
        this.Day = day;
    }

    /**
     * Gets the day associated with this time counter.
     *
     * @return the date for this counter
     */
    public LocalDate getDay() {
        return this.Day;
    }

    /**
     * Sets the number of minutes tracked.
     *
     * @param minutes the number of minutes to set
     */
    public void setMinutes(int minutes) {
        this.Minutes = minutes;
    }

    /**
     * Gets the number of minutes tracked.
     *
     * @return the number of minutes
     */
    public int getMinutes() {
        return this.Minutes;
    }

    /**
     * Sets the default number of minutes allowed.
     *
     * @param defaultMinutes the default number of minutes to set
     */
    public void setDefaultMinutes(int defaultMinutes) {
        this.DefaultMinutes = defaultMinutes;
    }

    /**
     * Gets the default number of minutes allowed.
     *
     * @return the default number of minutes
     */
    public int getDefaultMinutes() {
        return this.DefaultMinutes;
    }

    /**
     * Sets the last logon timestamp.
     *
     * @param lastLogOn the date and time of the last logon
     */
    public void setLastLogOn(LocalDateTime lastLogOn) {
        this.LastLogOn = lastLogOn;
    }

    /**
     * Gets the last logon timestamp.
     *
     * @return the date and time of the last logon
     */
    public LocalDateTime getLastLogOn() {
        return this.LastLogOn;
    }

    /**
     * Returns a string representation of this TimeCounter.
     *
     * @return a string containing all field values of this TimeCounter
     */
    @Override
    public String toString() {
        return "TimeCounter.Day = " + Day +
                ", TimeCounter.Minutes = " + Minutes +
                ", TimeCounter.DefaultMinutes = " + DefaultMinutes +
                ", TimeCounter.LastLogOn = " + LastLogOn;
    }
}
