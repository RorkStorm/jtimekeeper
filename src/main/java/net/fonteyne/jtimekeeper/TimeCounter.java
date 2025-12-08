package net.fonteyne.jtimekeeper;

import java.time.LocalDate;
import java.time.LocalDateTime;

class TimeCounter {
    private LocalDate Day;
    private int Minutes;
    private int DefaultMinutes;
    private LocalDateTime LastLogOn;

    public void setDay(LocalDate day) {
        this.Day = day;
    }

    public LocalDate getDay() {
        return this.Day;
    }

    public void setMinutes(int minutes) {
        this.Minutes = minutes;
    }

    public int getMinutes() {
        return this.Minutes;
    }

    public void setDefaultMinutes(int defaultMinutes) {
        this.DefaultMinutes = defaultMinutes;
    }

    public int getDefaultMinutes() {
        return this.DefaultMinutes;
    }

    public void setLastLogOn(LocalDateTime lastLogOn) {
        this.LastLogOn = lastLogOn;
    }

    public LocalDateTime getLastLogOn() {
        return this.LastLogOn;
    }

    @Override
    public String toString() {
        return "TimeCounter.Day = " + Day +
                ", TimeCounter.Minutes = " + Minutes +
                ", TimeCounter.DefaultMinutes = " + DefaultMinutes +
                ", TimeCounter.LastLogOn = " + LastLogOn;
    }
}
