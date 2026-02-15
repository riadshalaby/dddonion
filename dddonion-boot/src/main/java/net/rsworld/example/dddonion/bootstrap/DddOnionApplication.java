package net.rsworld.example.dddonion.bootstrap;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@EnableScheduling
@SpringBootApplication(scanBasePackages = "net.rsworld.example.dddonion")
public class DddOnionApplication {
    static void main(String[] args) {
        SpringApplication app = new SpringApplication(DddOnionApplication.class);
        app.addInitializers(new DatabaseBootstrapInitializer());
        app.run(args);
    }
}
