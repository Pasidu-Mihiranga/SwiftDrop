package lk.swiftdrop.merchant;

import com.networknt.schema.InputFormat;
import com.networknt.schema.JsonSchema;
import com.networknt.schema.JsonSchemaFactory;
import com.networknt.schema.SchemaLocation;
import com.networknt.schema.SchemaValidatorsConfig;
import com.networknt.schema.SpecVersion;
import com.networknt.schema.ValidationMessage;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Checks real response bodies against the schemas in contracts/openapi, so the code and the
 * published contract cannot drift apart unnoticed. OpenAPI 3.1 schemas are JSON Schema 2020-12.
 */
public final class OpenApiContract {

    private static final String BASE = "https://swiftdrop.lk/contracts/openapi/";
    private static final JsonSchemaFactory FACTORY = JsonSchemaFactory.getInstance(SpecVersion.VersionFlag.V202012,
            builder -> builder.schemaMappers(m -> m.mapPrefix(BASE, "classpath:contracts/openapi/")));
    private static final SchemaValidatorsConfig CONFIG = SchemaValidatorsConfig.builder()
            .formatAssertionsEnabled(true).build();

    private OpenApiContract() {
    }

    /** @param schema a schema name under merchant-service.v1.yaml#/components/schemas, e.g. "Merchant" */
    public static void assertMerchantSchema(String schema, String json) {
        assertMatches("merchant-service.v1.yaml", schema, json);
    }

    /** Every error body must be the shared problem+json shape. */
    public static void assertProblem(String json) {
        assertMatches("common.v1.yaml", "ProblemDetail", json);
    }

    private static void assertMatches(String file, String schema, String json) {
        JsonSchema compiled = FACTORY.getSchema(SchemaLocation.of(BASE + file + "#/components/schemas/" + schema), CONFIG);
        Set<ValidationMessage> errors = compiled.validate(json, InputFormat.JSON);
        assertThat(errors).as("%s does not match %s#%s", json, file, schema).isEmpty();
    }
}
