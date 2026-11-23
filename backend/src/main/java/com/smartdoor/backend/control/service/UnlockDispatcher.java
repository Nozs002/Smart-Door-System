package com.smartdoor.backend.control.service;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class UnlockDispatcher {
    private final UnlockService service;
    public UnlockDispatcher(UnlockService service) { this.service=service; }
    @Scheduled(fixedDelay=1000,initialDelay=2000)
    public void dispatch() {
        for (String id:service.waiting()) {
            try { service.dispatch(id); }
            catch (RuntimeException ignored) { /* Next pass expires unfinished commands; never replay SENT. */ }
        }
    }
}
