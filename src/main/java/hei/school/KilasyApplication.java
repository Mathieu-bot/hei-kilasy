package hei.school;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;

@SpringBootApplication
public class KilasyApplication {

    public static void main(String[] args) {
        SpringApplication.run(KilasyApplication.class, args);
    }

    // Simulated SMS provider: replace this bean to change provider, grading code stays untouched
    @Bean
    SubmissionController.SmsSender smsSender() {
        return (phone, message) -> System.out.println("[SMS] " + phone + " : " + message);
    }
}
