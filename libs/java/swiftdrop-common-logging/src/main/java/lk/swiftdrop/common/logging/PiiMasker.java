package lk.swiftdrop.common.logging;

import java.util.List;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Masks personal data (emails, phone numbers) and credentials (passwords, bearer tokens)
 * before text reaches a log sink. Applied to every log message and stack trace by
 * {@link JsonLogLayout} and {@link MaskingMessageConverter}.
 */
public final class PiiMasker {

    private record Rule(Pattern pattern, Function<Matcher, String> replacement) { }

    private static final List<Rule> RULES = List.of(
            // "password":"secret"  /  password=secret  (also secret, token, apiKey variants)
            new Rule(Pattern.compile("(?i)(\"?(?:password|passwd|secret|client_secret|api[_-]?key|access_token|refresh_token)\"?\\s*[:=]\\s*\"?)([^\"\\s,&}]+)"),
                    m -> m.group(1) + "***"),
            // Authorization: Bearer eyJ...
            new Rule(Pattern.compile("(?i)(bearer\\s+)[A-Za-z0-9._~+/=-]+"),
                    m -> m.group(1) + "***"),
            // jane.doe@example.com -> j***@example.com
            new Rule(Pattern.compile("([A-Za-z0-9._%+-])[A-Za-z0-9._%+-]*@([A-Za-z0-9.-]+\\.[A-Za-z]{2,})"),
                    m -> m.group(1) + "***@" + m.group(2)),
            // Sri Lankan / international phone numbers: +94771234567, 0771234567, +44 20 7946 0958
            new Rule(Pattern.compile("(?<![\\w+])(?:\\+\\d{1,3}[ -]?\\d{2,4}[ -]?\\d{3,4}[ -]?\\d{3,4}|0\\d{9})(?![\\w])"),
                    m -> maskDigits(m.group()))
    );

    private PiiMasker() {
    }

    /** Returns {@code text} with personal data and credentials replaced. Null-safe. */
    public static String mask(String text) {
        if (text == null || text.isEmpty()) {
            return text;
        }
        String result = text;
        for (Rule rule : RULES) {
            Matcher matcher = rule.pattern().matcher(result);
            StringBuilder sb = new StringBuilder(result.length());
            while (matcher.find()) {
                matcher.appendReplacement(sb, Matcher.quoteReplacement(rule.replacement().apply(matcher)));
            }
            matcher.appendTail(sb);
            result = sb.toString();
        }
        return result;
    }

    /** Keeps the first two and last two digits: +94771234567 -> +9********67. */
    private static String maskDigits(String phone) {
        char[] chars = phone.toCharArray();
        int totalDigits = (int) phone.chars().filter(Character::isDigit).count();
        int seen = 0;
        for (int i = 0; i < chars.length; i++) {
            if (Character.isDigit(chars[i])) {
                seen++;
                if (seen > 2 && seen <= totalDigits - 2) {
                    chars[i] = '*';
                }
            }
        }
        return new String(chars);
    }
}
