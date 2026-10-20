package lk.swiftdrop.common.logging;

import ch.qos.logback.classic.pattern.ThrowableProxyConverter;
import ch.qos.logback.classic.spi.ILoggingEvent;

/** Logback {@code %maskedEx} conversion word: stack traces with PII masked. */
public class MaskingThrowableConverter extends ThrowableProxyConverter {

    @Override
    public String convert(ILoggingEvent event) {
        return PiiMasker.mask(super.convert(event));
    }
}
