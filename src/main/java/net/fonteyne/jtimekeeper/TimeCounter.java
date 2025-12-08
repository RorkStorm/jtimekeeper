package net.fonteyne.jtimekeeper;
import java.util.*;

class TimeCounter {
    private Date Day;
    private int Minutes;
    private int DefaultMinutes;
    private Date LastLogOn;

    public void setDay(Date day) {
        this.Day = day;
    }

    public Date getDay() {
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

    public void setLastLogOn(Date lastLogOn) {
        this.LastLogOn = lastLogOn;
    }

    public Date getLastLogOn() {
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
