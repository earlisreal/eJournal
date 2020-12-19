package io.earlisreal.ejournal.dto;

import java.time.Instant;

public class EmailLastSync {

    private String email;
    private Instant lastSync;

    public EmailLastSync() {}

    public EmailLastSync(String email, Instant lastSync) {
        this.email = email;
        this.lastSync = lastSync;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public Instant getLastSync() {
        return lastSync;
    }

    public void setLastSync(Instant lastSync) {
        this.lastSync = lastSync;
    }

}
