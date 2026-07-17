package com.mtriet.tamlottery.identity.config;

import com.mtriet.tamlottery.identity.application.BootstrapService;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

@Component
public class BootstrapRunner implements ApplicationRunner {

    private final BootstrapService bootstrapService;

    public BootstrapRunner(BootstrapService bootstrapService) {
        this.bootstrapService = bootstrapService;
    }

    @Override
    public void run(ApplicationArguments args) {
        bootstrapService.initialize();
    }
}
