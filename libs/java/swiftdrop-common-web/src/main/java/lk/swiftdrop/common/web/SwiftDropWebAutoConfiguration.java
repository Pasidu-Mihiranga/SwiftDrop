package lk.swiftdrop.common.web;

import lk.swiftdrop.common.web.correlation.CorrelationIdFilter;
import lk.swiftdrop.common.web.error.GlobalExceptionHandler;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.core.Ordered;

/** Registers SwiftDrop's web conventions in every servlet service that has this library on the classpath. */
@AutoConfiguration
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
public class SwiftDropWebAutoConfiguration {

    /** Runs before Spring Security so even 401/403 responses are correlated. */
    @Bean
    public FilterRegistrationBean<CorrelationIdFilter> swiftDropCorrelationIdFilter() {
        FilterRegistrationBean<CorrelationIdFilter> registration = new FilterRegistrationBean<>(new CorrelationIdFilter());
        registration.setOrder(Ordered.HIGHEST_PRECEDENCE);
        return registration;
    }

    @Bean
    @ConditionalOnMissingBean(GlobalExceptionHandler.class)
    public GlobalExceptionHandler swiftDropGlobalExceptionHandler() {
        return new GlobalExceptionHandler();
    }
}
