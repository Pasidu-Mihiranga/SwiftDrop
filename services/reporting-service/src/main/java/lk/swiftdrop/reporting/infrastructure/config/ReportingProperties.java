package lk.swiftdrop.reporting.infrastructure.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

import java.time.ZoneId;

/**
 * @param timeZone the operator's time zone; decides which business day an event counts for.
 *                 Changing it only affects events processed afterwards (or after a replay).
 */
@ConfigurationProperties("swiftdrop.reporting")
public record ReportingProperties(@DefaultValue("Asia/Colombo") ZoneId timeZone) {
}
