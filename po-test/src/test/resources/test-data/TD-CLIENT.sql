-- ═══════════════════════════════════════════════════════════════════════════
-- TD-CLIENT.sql - Client-Specific Test Data for Plugin Testing
-- ═══════════════════════════════════════════════════════════════════════════
-- Test Data Set: TD-CLIENT
-- Purpose: Client-specific POs/Receipts for plugin execution tests
-- Entities: ORDERS, ORDERDETAIL, RECEIPT, RECEIPTDETAIL, CLIENT_CONFIG
-- Used By: Plugin tests (ispPRREC*, ispASNFZ*, ispCOMMIT*)
-- ═══════════════════════════════════════════════════════════════════════════

SET search_path TO dbo, public;

-- ═══════════════════════════════════════════════════════════════════════════
-- Client Configuration Table (for plugin routing)
-- ═══════════════════════════════════════════════════════════════════════════

CREATE TABLE IF NOT EXISTS client_config (
    storerkey VARCHAR(50) NOT NULL,
    configkey VARCHAR(50) NOT NULL,
    configvalue VARCHAR(500),
    description VARCHAR(200),
    status VARCHAR(5) DEFAULT '1',
    PRIMARY KEY (storerkey, configkey)
);

-- Nike Korea Client Config
INSERT INTO client_config (storerkey, configkey, configvalue, description) VALUES
('NIKE_KR', 'PLUGIN_PRE_RECEIPT', 'ispPRREC_NIKE', 'Pre-receipt plugin for Nike'),
('NIKE_KR', 'PLUGIN_POST_FINALIZE', 'ispASNFZ_NIKE', 'Post-finalize plugin for Nike'),
('NIKE_KR', 'PLUGIN_COMMIT', 'ispCOMMIT_NIKE', 'Commit plugin for Nike'),
('NIKE_KR', 'LOT_TRACKING', 'STYLE,COLOR,SEASON', 'Lottable fields for Nike'),
('NIKE_KR', 'QC_REQUIRED', 'Y', 'QC required for all Nike receipts'),
('NIKE_KR', 'CUSTOMS_REQ', 'Y', 'Korea customs tracking required')
ON CONFLICT (storerkey, configkey) DO NOTHING;

-- H&M Korea Client Config
INSERT INTO client_config (storerkey, configkey, configvalue, description) VALUES
('HM_KR', 'PLUGIN_PRE_RECEIPT', 'ispPRREC_HM', 'Pre-receipt plugin for H&M'),
('HM_KR', 'PLUGIN_POST_FINALIZE', 'ispASNFZ_HM', 'Post-finalize plugin for H&M'),
('HM_KR', 'FAST_FASHION_MODE', 'Y', 'Enable fast fashion processing'),
('HM_KR', 'SKIP_LOT_VALIDATION', 'Y', 'Skip lot validation for fast fashion')
ON CONFLICT (storerkey, configkey) DO NOTHING;

-- Adidas Korea Client Config
INSERT INTO client_config (storerkey, configkey, configvalue, description) VALUES
('ADIDAS_KR', 'PLUGIN_PRE_RECEIPT', 'ispPRREC_ADIDAS', 'Pre-receipt plugin for Adidas'),
('ADIDAS_KR', 'PLUGIN_POST_FINALIZE', 'ispASNFZ_ADIDAS', 'Post-finalize plugin for Adidas'),
('ADIDAS_KR', 'SERIAL_TRACKING', 'Y', 'Serial number tracking for Adidas')
ON CONFLICT (storerkey, configkey) DO NOTHING;

-- Unilever India Client Config (GST compliance)
INSERT INTO client_config (storerkey, configkey, configvalue, description) VALUES
('UNILEVER_IN', 'PLUGIN_PRE_RECEIPT', 'ispPRREC_GST', 'Pre-receipt GST validation'),
('UNILEVER_IN', 'PLUGIN_POST_FINALIZE', 'ispASNFZ_GST', 'Post-finalize GST invoice'),
('UNILEVER_IN', 'GST_REQUIRED', 'Y', 'GST invoice required'),
('UNILEVER_IN', 'EXPIRY_CHECK', 'Y', 'Expiry date validation for FMCG')
ON CONFLICT (storerkey, configkey) DO NOTHING;

-- Test Storer Config (for plugin testing)
INSERT INTO client_config (storerkey, configkey, configvalue, description) VALUES
('TEST_STORER_001', 'PLUGIN_PRE_RECEIPT', 'ispPRREC_TEST', 'Test pre-receipt plugin'),
('TEST_STORER_001', 'PLUGIN_POST_FINALIZE', 'ispASNFZ_TEST', 'Test post-finalize plugin'),
('TEST_STORER_001', 'PLUGIN_FAIL_MODE', 'N', 'Set to Y to simulate plugin failure')
ON CONFLICT (storerkey, configkey) DO NOTHING;

-- ═══════════════════════════════════════════════════════════════════════════
-- Nike Client POs (Full lottable tracking)
-- ═══════════════════════════════════════════════════════════════════════════

-- NIKE-PO-001: Nike Korea standard PO with style/color tracking
INSERT INTO orders (orderkey, storerkey, facility, externorderkey, status, orderdate, expecteddate, suppliername, susr1, susr2) VALUES
('NIKE-PO-001', 'NIKE_KR', 'KR01', 'NIKE-EXT-001', '0', CURRENT_DATE, CURRENT_DATE + INTERVAL '21 days', 'Nike Factory Vietnam', 'PURCHASE', 'SEASON-S24')
ON CONFLICT (orderkey) DO NOTHING;

INSERT INTO orderdetail (orderkey, orderlinenumber, storerkey, sku, qtyordered, status, lottable01, lottable02, lottable03, lottable04) VALUES
('NIKE-PO-001', 1, 'NIKE_KR', 'NK-AIRMAX90-BLK', 1000, '0', 'STYLE-AM90-2024', 'COLOR-001-BLK', 'SEASON-S24', 'SIZE-MIX'),
('NIKE-PO-001', 2, 'NIKE_KR', 'NK-AIRMAX90-WHT', 800, '0', 'STYLE-AM90-2024', 'COLOR-002-WHT', 'SEASON-S24', 'SIZE-MIX'),
('NIKE-PO-001', 3, 'NIKE_KR', 'NK-AF1-BLK', 600, '0', 'STYLE-AF1-2024', 'COLOR-001-BLK', 'SEASON-S24', 'SIZE-MIX'),
('NIKE-PO-001', 4, 'NIKE_KR', 'NK-AF1-WHT', 600, '0', 'STYLE-AF1-2024', 'COLOR-002-WHT', 'SEASON-S24', 'SIZE-MIX'),
('NIKE-PO-001', 5, 'NIKE_KR', 'NK-DRIFIT-M', 2000, '0', 'STYLE-DFT-2024', 'SIZE-M', 'SEASON-S24', 'PERF-LINE')
ON CONFLICT (orderkey, orderlinenumber) DO NOTHING;

-- NIKE-PO-002: Nike Korea populated PO (ready for finalization)
INSERT INTO orders (orderkey, storerkey, facility, externorderkey, status, orderdate, expecteddate, suppliername, susr1, susr2) VALUES
('NIKE-PO-002', 'NIKE_KR', 'KR01', 'NIKE-EXT-002', '5', CURRENT_DATE - INTERVAL '7 days', CURRENT_DATE + INTERVAL '7 days', 'Nike Factory Indonesia', 'PURCHASE', 'SEASON-S24')
ON CONFLICT (orderkey) DO NOTHING;

INSERT INTO orderdetail (orderkey, orderlinenumber, storerkey, sku, qtyordered, qtyreceived, status, lottable01, lottable02) VALUES
('NIKE-PO-002', 1, 'NIKE_KR', 'NK-AIRMAX90-BLK', 500, 500, '5', 'STYLE-AM90-2024', 'COLOR-001-BLK'),
('NIKE-PO-002', 2, 'NIKE_KR', 'NK-AF1-BLK', 300, 300, '5', 'STYLE-AF1-2024', 'COLOR-001-BLK')
ON CONFLICT (orderkey, orderlinenumber) DO NOTHING;

-- ═══════════════════════════════════════════════════════════════════════════
-- H&M Client POs (Fast fashion - no lot control)
-- ═══════════════════════════════════════════════════════════════════════════

-- HM-PO-001: H&M Korea fast fashion PO
INSERT INTO orders (orderkey, storerkey, facility, externorderkey, status, orderdate, expecteddate, suppliername, susr1) VALUES
('HM-PO-001', 'HM_KR', 'KR02', 'HM-EXT-001', '0', CURRENT_DATE, CURRENT_DATE + INTERVAL '5 days', 'H&M Supplier Bangladesh', 'FASTTRACK')
ON CONFLICT (orderkey) DO NOTHING;

INSERT INTO orderdetail (orderkey, orderlinenumber, storerkey, sku, qtyordered, status) VALUES
('HM-PO-001', 1, 'HM_KR', 'HM-BASIC-TEE-S', 5000, '0'),
('HM-PO-001', 2, 'HM_KR', 'HM-BASIC-TEE-M', 8000, '0'),
('HM-PO-001', 3, 'HM_KR', 'HM-BASIC-TEE-L', 5000, '0'),
('HM-PO-001', 4, 'HM_KR', 'HM-SLIM-JEANS-32', 3000, '0'),
('HM-PO-001', 5, 'HM_KR', 'HM-SLIM-JEANS-34', 3000, '0')
ON CONFLICT (orderkey, orderlinenumber) DO NOTHING;

-- ═══════════════════════════════════════════════════════════════════════════
-- Adidas Client POs (Serial tracking)
-- ═══════════════════════════════════════════════════════════════════════════

-- AD-PO-001: Adidas Korea with serial tracking
INSERT INTO orders (orderkey, storerkey, facility, externorderkey, status, orderdate, expecteddate, suppliername, susr1) VALUES
('AD-PO-001', 'ADIDAS_KR', 'KR01', 'AD-EXT-001', '0', CURRENT_DATE, CURRENT_DATE + INTERVAL '14 days', 'Adidas Factory China', 'SERIAL-TRACK')
ON CONFLICT (orderkey) DO NOTHING;

INSERT INTO orderdetail (orderkey, orderlinenumber, storerkey, sku, qtyordered, status, lottable01, lottable02) VALUES
('AD-PO-001', 1, 'ADIDAS_KR', 'AD-ULTRABOOST-BLK', 400, '0', 'STYLE-UB24', 'COLOR-CBLK'),
('AD-PO-001', 2, 'ADIDAS_KR', 'AD-ULTRABOOST-WHT', 400, '0', 'STYLE-UB24', 'COLOR-FWHT'),
('AD-PO-001', 3, 'ADIDAS_KR', 'AD-STAN-SMITH', 300, '0', 'STYLE-SS24', 'COLOR-OWHT')
ON CONFLICT (orderkey, orderlinenumber) DO NOTHING;

-- ═══════════════════════════════════════════════════════════════════════════
-- Unilever India POs (FMCG with expiry tracking)
-- ═══════════════════════════════════════════════════════════════════════════

-- UL-PO-001: Unilever India with GST and expiry
INSERT INTO orders (orderkey, storerkey, facility, externorderkey, status, orderdate, expecteddate, suppliername, susr1, susr2) VALUES
('UL-PO-001', 'UNILEVER_IN', 'IN02', 'UL-EXT-001', '0', CURRENT_DATE, CURRENT_DATE + INTERVAL '3 days', 'Unilever Factory Mumbai', 'GST-IN-29ABCDE1234F1Z5', 'FMCG-STANDARD')
ON CONFLICT (orderkey) DO NOTHING;

INSERT INTO orderdetail (orderkey, orderlinenumber, storerkey, sku, qtyordered, status, lottable01, lottable02) VALUES
('UL-PO-001', 1, 'UNILEVER_IN', 'UL-SOAP-LUX', 10000, '0', 'BATCH-2024-001', '2026-05-01'),  -- Expiry date
('UL-PO-001', 2, 'UNILEVER_IN', 'UL-SHAMPOO-DOVE', 5000, '0', 'BATCH-2024-002', '2027-05-01'),
('UL-PO-001', 3, 'UNILEVER_IN', 'UL-CREAM-FAIR', 8000, '0', 'BATCH-2024-003', '2025-11-01')
ON CONFLICT (orderkey, orderlinenumber) DO NOTHING;

-- ═══════════════════════════════════════════════════════════════════════════
-- Client-Specific Receipts
-- ═══════════════════════════════════════════════════════════════════════════

-- Nike Receipt for plugin testing
INSERT INTO receipt (receiptkey, orderkey, storerkey, facility, externreceiptkey, status, receiptdate) VALUES
('NIKE-RCV-001', 'NIKE-PO-002', 'NIKE_KR', 'KR01', 'NIKE-EXT-RCV-001', '5', CURRENT_TIMESTAMP)
ON CONFLICT (receiptkey) DO NOTHING;

INSERT INTO receiptdetail (receiptkey, receiptlinenumber, orderkey, orderlinenumber, storerkey, sku, qtyexpected, qtyreceived, status, toloc, lottable01, lottable02) VALUES
('NIKE-RCV-001', 1, 'NIKE-PO-002', 1, 'NIKE_KR', 'NK-AIRMAX90-BLK', 500, 500, '5', 'KR01-RECV-01', 'STYLE-AM90-2024', 'COLOR-001-BLK'),
('NIKE-RCV-001', 2, 'NIKE-PO-002', 2, 'NIKE_KR', 'NK-AF1-BLK', 300, 300, '5', 'KR01-RECV-02', 'STYLE-AF1-2024', 'COLOR-001-BLK')
ON CONFLICT (receiptkey, receiptlinenumber) DO NOTHING;

-- ═══════════════════════════════════════════════════════════════════════════
-- Verification
-- ═══════════════════════════════════════════════════════════════════════════

DO $$
BEGIN
    RAISE NOTICE 'TD-CLIENT Data Loaded:';
    RAISE NOTICE '  Client Configs: %', (SELECT COUNT(*) FROM client_config);
    RAISE NOTICE '  Nike POs: %', (SELECT COUNT(*) FROM orders WHERE orderkey LIKE 'NIKE-PO-%');
    RAISE NOTICE '  H&M POs: %', (SELECT COUNT(*) FROM orders WHERE orderkey LIKE 'HM-PO-%');
    RAISE NOTICE '  Adidas POs: %', (SELECT COUNT(*) FROM orders WHERE orderkey LIKE 'AD-PO-%');
    RAISE NOTICE '  Unilever POs: %', (SELECT COUNT(*) FROM orders WHERE orderkey LIKE 'UL-PO-%');
    RAISE NOTICE '  Client Receipts: %', (SELECT COUNT(*) FROM receipt WHERE receiptkey LIKE 'NIKE-RCV-%');
END $$;
