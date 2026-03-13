package talan.pfe.rulengine;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableAsync;

@SpringBootApplication
@EnableAsync
public class ModuleAdministrationApplication {
    public static void main(String[] args) {
        SpringApplication.run(ModuleAdministrationApplication.class, args);
    }
}
