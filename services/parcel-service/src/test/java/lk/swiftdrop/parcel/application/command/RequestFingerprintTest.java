package lk.swiftdrop.parcel.application.command;

import lk.swiftdrop.events.common.Types.ServiceLevel;
import lk.swiftdrop.events.common.Types.SizeClass;
import lk.swiftdrop.parcel.ParcelFixtures;
import lk.swiftdrop.parcel.domain.model.Address;
import lk.swiftdrop.parcel.domain.model.Recipient;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import tools.jackson.databind.json.JsonMapper;

import java.math.BigDecimal;
import java.util.function.UnaryOperator;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

class RequestFingerprintTest {

    static MerchantOrderCommand order() {
        return new MerchantOrderCommand("ORD-1001", ParcelFixtures.PICKUP, ParcelFixtures.DROPOFF,
                ParcelFixtures.RECIPIENT, new BigDecimal("2.0"), SizeClass.S, ServiceLevel.STANDARD, BigDecimal.ZERO);
    }

    private static MerchantOrderCommand with(MerchantOrderCommand o, String ref, Address pickup, Address dropoff,
                                             Recipient recipient, BigDecimal weight, SizeClass size,
                                             ServiceLevel level, BigDecimal cod) {
        return new MerchantOrderCommand(ref == null ? o.merchantOrderRef() : ref, pickup == null ? o.pickup() : pickup,
                dropoff == null ? o.dropoff() : dropoff, recipient == null ? o.recipient() : recipient,
                weight == null ? o.weightKg() : weight, size == null ? o.sizeClass() : size,
                level == null ? o.serviceLevel() : level, cod == null ? o.codAmount() : cod);
    }

    private static Address address(Address a, String line1, String line2, String city) {
        return new Address(line1, line2, city, a.district(), a.postalCode(), a.lat(), a.lng());
    }

    @Test
    void theSameOrderAlwaysHasTheSameFingerprint() {
        String first = RequestFingerprint.of(order());

        assertThat(first).hasSize(64).matches("^[0-9a-f]{64}$");
        assertThat(RequestFingerprint.of(order())).isEqualTo(first);
    }

    @Test
    void fieldOrderWhitespaceAndNumberSpellingInTheJsonDoNotMatter() {
        JsonMapper json = JsonMapper.builder().build();
        String compact = """
                {"merchantOrderRef":"ORD-1001","weightKg":2,"sizeClass":"S","serviceLevel":"STANDARD",
                "pickup":{"line1":"12 Temple Road","city":"Kandy","district":"Kandy"},
                "dropoff":{"line1":"4 Galle Road","city":"Colombo 03","district":"Colombo"},
                "recipient":{"name":"Saman Perera","phone":"0712345678"}}""";
        String reordered = """
                {
                  "recipient" : { "phone" : "0712345678", "name" : "Saman Perera" },
                  "dropoff"   : { "district" : "Colombo", "city" : "Colombo 03", "line1" : "4 Galle Road" },
                  "sizeClass" : "S",
                  "pickup"    : { "district" : "Kandy", "line1" : "12 Temple Road", "city" : "Kandy" },
                  "weightKg"  : 2.00,
                  "merchantOrderRef" : "ORD-1001",
                  "codAmount" : 0.0
                }""";

        MerchantOrderCommand a = json.readValue(compact, MerchantOrderCommand.class);
        MerchantOrderCommand b = json.readValue(reordered, MerchantOrderCommand.class);

        assertThat(RequestFingerprint.of(a)).isEqualTo(RequestFingerprint.of(b));
    }

    @Test
    void anOmittedServiceLevelOrCodIsTheSameAsTheDefault() {
        MerchantOrderCommand defaults = new MerchantOrderCommand("ORD-1001", ParcelFixtures.PICKUP,
                ParcelFixtures.DROPOFF, ParcelFixtures.RECIPIENT, new BigDecimal("2"), SizeClass.S, null, null);

        assertThat(RequestFingerprint.of(defaults)).isEqualTo(RequestFingerprint.of(order()));
    }

    static Stream<Arguments> everyChange() {
        MerchantOrderCommand o = order();
        Recipient r = o.recipient();
        return Stream.<UnaryOperator<MerchantOrderCommand>>of(
                c -> with(c, "ORD-1002", null, null, null, null, null, null, null),
                c -> with(c, null, address(c.pickup(), "13 Temple Road", c.pickup().line2(), c.pickup().city()),
                        null, null, null, null, null, null),
                c -> with(c, null, null, address(c.dropoff(), c.dropoff().line1(), "Flat 3", c.dropoff().city()),
                        null, null, null, null, null),
                c -> with(c, null, null, null, new Recipient("Nimal Perera", r.phone(), r.email()),
                        null, null, null, null),
                c -> with(c, null, null, null, new Recipient(r.name(), "0719999999", r.email()),
                        null, null, null, null),
                c -> with(c, null, null, null, new Recipient(r.name(), r.phone(), null), null, null, null, null),
                c -> with(c, null, null, null, null, new BigDecimal("2.01"), null, null, null),
                c -> with(c, null, null, null, null, null, SizeClass.M, null, null),
                c -> with(c, null, null, null, null, null, null, ServiceLevel.EXPRESS, null),
                c -> with(c, null, null, null, null, null, null, null, new BigDecimal("1500")))
                .map(change -> Arguments.of(change.apply(o)));
    }

    @ParameterizedTest
    @MethodSource("everyChange")
    void anyChangeToTheOrderChangesTheFingerprint(MerchantOrderCommand changed) {
        assertThat(RequestFingerprint.of(changed)).isNotEqualTo(RequestFingerprint.of(order()));
    }

    @Test
    void valuesCannotBleedIntoTheirNeighbours() {
        MerchantOrderCommand o = order();
        // "a;b" + null and "a" + ";b"-style splits must not collide: every value carries its length
        MerchantOrderCommand joined = with(o, null, address(o.pickup(), "1 Road;line2=3:Apt", null,
                o.pickup().city()), null, null, null, null, null, null);
        MerchantOrderCommand split = with(o, null, address(o.pickup(), "1 Road", "Apt", o.pickup().city()),
                null, null, null, null, null, null);

        assertThat(RequestFingerprint.of(joined)).isNotEqualTo(RequestFingerprint.of(split));
    }

    @Test
    void aMissingValueDiffersFromAnEmptyOne() {
        MerchantOrderCommand o = order();
        MerchantOrderCommand missing = with(o, null, address(o.pickup(), o.pickup().line1(), null, o.pickup().city()),
                null, null, null, null, null, null);
        MerchantOrderCommand empty = with(o, null, address(o.pickup(), o.pickup().line1(), "", o.pickup().city()),
                null, null, null, null, null, null);

        assertThat(RequestFingerprint.of(missing)).isNotEqualTo(RequestFingerprint.of(empty));
    }
}
