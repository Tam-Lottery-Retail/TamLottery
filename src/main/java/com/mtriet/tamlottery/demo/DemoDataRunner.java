package com.mtriet.tamlottery.demo;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(prefix = "app.demo", name = "enabled", havingValue = "true")
public class DemoDataRunner implements ApplicationRunner {

    private final DemoDataService demoDataService;
    private final DemoDataProperties properties;
    private final ConfigurableApplicationContext applicationContext;

    public DemoDataRunner(DemoDataService demoDataService,
                          DemoDataProperties properties,
                          ConfigurableApplicationContext applicationContext) {
        this.demoDataService = demoDataService;
        this.properties = properties;
        this.applicationContext = applicationContext;
    }

    @Override
    public void run(ApplicationArguments args) {
        demoDataService.seed();
        if (properties.exitAfterSeed()) {
            System.exit(SpringApplication.exit(applicationContext));
        }
    }
}
