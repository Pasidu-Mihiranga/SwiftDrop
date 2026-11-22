-- Merchant order API (M7): parcel-service's own copy of each merchant's status, built only from
-- MerchantLifecycleChanged events (swiftdrop.merchant.lifecycle.v1). merchant-service owns the
-- truth; this read model lets an order be checked without a synchronous call. A merchant with no
-- row here is treated as not approved (fail closed).

CREATE TABLE merchant_status (
    merchant_id    VARCHAR(64)   PRIMARY KEY,
    status         VARCHAR(20)   NOT NULL,
    -- sender details for ParcelCreated, so notifications can name the merchant
    business_name  VARCHAR(150)  NOT NULL,
    contact_email  VARCHAR(254),
    -- when merchant-service made the change; an older event never overwrites a newer one
    changed_at     TIMESTAMPTZ   NOT NULL,
    updated_at     TIMESTAMPTZ   NOT NULL,
    CONSTRAINT ck_merchant_status_status CHECK (status IN ('PENDING', 'APPROVED', 'REJECTED', 'SUSPENDED'))
);
