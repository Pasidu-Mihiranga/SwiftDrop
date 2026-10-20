package lk.swiftdrop.dispatch;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/** Unassigned parcel pool, courier assignment, run sheets and courier workload projection. */
@SpringBootApplication
public class DispatchServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(DispatchServiceApplication.class, args);
    }
}
