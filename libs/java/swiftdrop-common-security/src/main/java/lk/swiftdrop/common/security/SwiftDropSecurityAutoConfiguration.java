package lk.swiftdrop.common.security;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.servlet.HandlerExceptionResolver;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.util.List;

/**
 * Secure-by-default setup for every servlet service: stateless, every endpoint needs a valid
 * Keycloak JWT (issuer + audience checked via {@code spring.security.oauth2.resourceserver.jwt.*}),
 * realm roles become {@code ROLE_*} authorities and {@code @PreAuthorize} is enabled.
 * A service can still declare its own {@link SecurityFilterChain} bean to replace the default.
 */
@AutoConfiguration(beforeName = {
        // Register our SecurityFilterChain first so Boot's default chains back off.
        "org.springframework.boot.security.autoconfigure.web.servlet.ServletWebSecurityAutoConfiguration",
        "org.springframework.boot.security.autoconfigure.actuate.web.servlet.ManagementWebSecurityAutoConfiguration",
        "org.springframework.boot.security.oauth2.server.resource.autoconfigure.servlet.OAuth2ResourceServerAutoConfiguration"
})
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
@EnableMethodSecurity
@EnableConfigurationProperties(SwiftDropSecurityProperties.class)
public class SwiftDropSecurityAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    public JwtAuthenticationConverter jwtAuthenticationConverter() {
        JwtAuthenticationConverter converter = new JwtAuthenticationConverter();
        converter.setJwtGrantedAuthoritiesConverter(new KeycloakRealmRoleConverter());
        return converter;
    }

    @Bean
    public SecurityExceptionAdvice swiftDropSecurityExceptionAdvice() {
        return new SecurityExceptionAdvice();
    }

    /** Delegates 401/403 raised inside the filter chain to {@link SecurityExceptionAdvice} for a consistent body. */
    @Bean
    public SecurityProblemHandlers swiftDropSecurityProblemHandlers(
            @Qualifier("handlerExceptionResolver") HandlerExceptionResolver resolver) {
        return new SecurityProblemHandlers(
                (request, response, ex) -> resolver.resolveException(request, response, null, ex),
                (request, response, ex) -> resolver.resolveException(request, response, null, ex));
    }

    @Bean
    @ConditionalOnMissingBean(SecurityFilterChain.class)
    public SecurityFilterChain swiftDropSecurityFilterChain(HttpSecurity http, SwiftDropSecurityProperties properties,
                                                            JwtAuthenticationConverter jwtAuthenticationConverter,
                                                            SecurityProblemHandlers handlers) throws Exception {
        http
                // Stateless bearer-token API: no cookies, so CSRF protection is not applicable.
                .csrf(AbstractHttpConfigurer::disable)
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(properties.allPublicPaths()).permitAll()
                        .anyRequest().authenticated())
                .oauth2ResourceServer(oauth -> oauth
                        .jwt(jwt -> jwt.jwtAuthenticationConverter(jwtAuthenticationConverter))
                        .authenticationEntryPoint(handlers.entryPoint())
                        .accessDeniedHandler(handlers.accessDeniedHandler()))
                .exceptionHandling(e -> e
                        .authenticationEntryPoint(handlers.entryPoint())
                        .accessDeniedHandler(handlers.accessDeniedHandler()));
        return http.build();
    }

    @Bean
    public WebMvcConfigurer swiftDropAuthenticatedUserResolver() {
        return new WebMvcConfigurer() {
            @Override
            public void addArgumentResolvers(List<HandlerMethodArgumentResolver> resolvers) {
                resolvers.add(new AuthenticatedUserArgumentResolver());
            }
        };
    }

    /** Pair of handlers so they can be shared by the default chain and custom chains. */
    public record SecurityProblemHandlers(AuthenticationEntryPoint entryPoint, AccessDeniedHandler accessDeniedHandler) {
    }
}
