package lk.swiftdrop.reporting.infrastructure.config;

import lk.swiftdrop.reporting.domain.model.BusinessCalendar;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(ReportingProperties.class)
class ReportingConfiguration {

    @Bean
    BusinessCalendar businessCalendar(ReportingProperties properties) {
        return new BusinessCalendar(properties.timeZone());
    }
}
