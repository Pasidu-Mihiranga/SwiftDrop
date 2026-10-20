package lk.swiftdrop.merchant;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/** Merchant registration, admin approval workflow and Keycloak client-credentials provisioning. */
@SpringBootApplication
public class MerchantServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(MerchantServiceApplication.class, args);
    }
}
