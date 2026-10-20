package lk.swiftdrop.parcel;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/** Parcel lifecycle: booking, tracking numbers, status state machine, proof of delivery, merchant order API, receipts. */
@SpringBootApplication
public class ParcelServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(ParcelServiceApplication.class, args);
    }
}
