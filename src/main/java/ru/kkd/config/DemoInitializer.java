package ru.kkd.config;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import ru.kkd.service.DemoSeedService;

@Component
@ConditionalOnProperty(name="app.seed-demo", havingValue="true", matchIfMissing=true)
public class DemoInitializer implements ApplicationRunner {
    private final DemoSeedService seed;
    public DemoInitializer(DemoSeedService seed) { this.seed=seed; }
    @Override public void run(ApplicationArguments args) { seed.seed(); }
}
