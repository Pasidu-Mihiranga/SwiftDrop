package lk.swiftdrop.common.logging;

import ch.qos.logback.classic.pattern.ClassicConverter;
import ch.qos.logback.classic.spi.ILoggingEvent;

/** Logback {@code %maskedMsg} conversion word: the formatted message with PII masked. */
public class MaskingMessageConverter extends ClassicConverter {

    @Override
    public String convert(ILoggingEvent event) {
        return PiiMasker.mask(event.getFormattedMessage());
    }
}
