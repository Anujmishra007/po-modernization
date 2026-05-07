-- ═══════════════════════════════════════════════════════════════════════════
-- TD-PO-HAPPY.sql - PO Test Data for Happy Path Scenarios
-- ═══════════════════════════════════════════════════════════════════════════
-- Test Data Set: TD-PO-HAPPY
-- Required: 10 POs for happy path testing
-- Entities: ORDERS, ORDERDETAIL
-- Used By: F1 (PO Creation), F2 (ASN Population), F3 (Receipt Finalization)
-- ═══════════════════════════════════════════════════════════════════════════

SET search_path TO dbo, public;

-- ═══════════════════════════════════════════════════════════════════════════
-- Happy Path POs (10 POs - Various scenarios)
-- ═══════════════════════════════════════════════════════════════════════════

-- PO-HAPPY-001: Single line PO - simplest case
INSERT INTO orders (orderkey, storerkey, facility, externorderkey, status, orderdate, expecteddate, suppliername, buyerref) VALUES
('PO-HAPPY-001', 'TEST_STORER_001', 'TEST01', 'EXT-HAPPY-001', '0', CURRENT_DATE, CURRENT_DATE + INTERVAL '7 days', 'Happy Supplier 1', 'BUY-001')
ON CONFLICT (orderkey) DO NOTHING;

INSERT INTO orderdetail (orderkey, orderlinenumber, storerkey, sku, qtyordered, status) VALUES
('PO-HAPPY-001', 1, 'TEST_STORER_001', 'TEST-SKU-001', 100, '0')
ON CONFLICT (orderkey, orderlinenumber) DO NOTHING;

-- PO-HAPPY-002: Multi-line PO (5 lines) - standard case
INSERT INTO orders (orderkey, storerkey, facility, externorderkey, status, orderdate, expecteddate, suppliername, buyerref) VALUES
('PO-HAPPY-002', 'TEST_STORER_001', 'TEST01', 'EXT-HAPPY-002', '0', CURRENT_DATE, CURRENT_DATE + INTERVAL '10 days', 'Happy Supplier 2', 'BUY-002')
ON CONFLICT (orderkey) DO NOTHING;

INSERT INTO orderdetail (orderkey, orderlinenumber, storerkey, sku, qtyordered, status) VALUES
('PO-HAPPY-002', 1, 'TEST_STORER_001', 'TEST-SKU-001', 200, '0'),
('PO-HAPPY-002', 2, 'TEST_STORER_001', 'TEST-SKU-002', 150, '0'),
('PO-HAPPY-002', 3, 'TEST_STORER_001', 'TEST-SKU-003', 100, '0'),
('PO-HAPPY-002', 4, 'TEST_STORER_001', 'TEST-SKU-001', 50, '0'),
('PO-HAPPY-002', 5, 'TEST_STORER_001', 'TEST-SKU-002', 75, '0')
ON CONFLICT (orderkey, orderlinenumber) DO NOTHING;

-- PO-HAPPY-003: Large quantity PO - volume test
INSERT INTO orders (orderkey, storerkey, facility, externorderkey, status, orderdate, expecteddate, suppliername, buyerref) VALUES
('PO-HAPPY-003', 'TEST_STORER_001', 'TEST01', 'EXT-HAPPY-003', '0', CURRENT_DATE, CURRENT_DATE + INTERVAL '14 days', 'Happy Supplier 3', 'BUY-003')
ON CONFLICT (orderkey) DO NOTHING;

INSERT INTO orderdetail (orderkey, orderlinenumber, storerkey, sku, qtyordered, status) VALUES
('PO-HAPPY-003', 1, 'TEST_STORER_001', 'TEST-SKU-001', 10000, '0'),
('PO-HAPPY-003', 2, 'TEST_STORER_001', 'TEST-SKU-002', 5000, '0')
ON CONFLICT (orderkey, orderlinenumber) DO NOTHING;

-- PO-HAPPY-004: PO with lottable data (Nike style tracking)
INSERT INTO orders (orderkey, storerkey, facility, externorderkey, status, orderdate, expecteddate, suppliername, buyerref) VALUES
('PO-HAPPY-004', 'NIKE_KR', 'KR01', 'NIKE-HAPPY-001', '0', CURRENT_DATE, CURRENT_DATE + INTERVAL '21 days', 'Nike Factory Korea', 'NIKE-BUY-001')
ON CONFLICT (orderkey) DO NOTHING;

INSERT INTO orderdetail (orderkey, orderlinenumber, storerkey, sku, qtyordered, status, lottable01, lottable02, lottable03) VALUES
('PO-HAPPY-004', 1, 'NIKE_KR', 'NK-AIRMAX90-BLK', 500, '0', 'STYLE-AM90', 'COLOR-BLK', 'SEASON-S24'),
('PO-HAPPY-004', 2, 'NIKE_KR', 'NK-AIRMAX90-WHT', 500, '0', 'STYLE-AM90', 'COLOR-WHT', 'SEASON-S24'),
('PO-HAPPY-004', 3, 'NIKE_KR', 'NK-AF1-BLK', 300, '0', 'STYLE-AF1', 'COLOR-BLK', 'SEASON-S24'),
('PO-HAPPY-004', 4, 'NIKE_KR', 'NK-DRIFIT-M', 1000, '0', 'STYLE-DFT', 'SIZE-M', 'SEASON-S24'),
('PO-HAPPY-004', 5, 'NIKE_KR', 'NK-DRIFIT-L', 800, '0', 'STYLE-DFT', 'SIZE-L', 'SEASON-S24')
ON CONFLICT (orderkey, orderlinenumber) DO NOTHING;

-- PO-HAPPY-005: H&M fast fashion PO (no lot control)
INSERT INTO orders (orderkey, storerkey, facility, externorderkey, status, orderdate, expecteddate, suppliername, buyerref) VALUES
('PO-HAPPY-005', 'HM_KR', 'KR02', 'HM-HAPPY-001', '0', CURRENT_DATE, CURRENT_DATE + INTERVAL '7 days', 'H&M Supplier Bangladesh', 'HM-BUY-001')
ON CONFLICT (orderkey) DO NOTHING;

INSERT INTO orderdetail (orderkey, orderlinenumber, storerkey, sku, qtyordered, status) VALUES
('PO-HAPPY-005', 1, 'HM_KR', 'HM-BASIC-TEE-S', 2000, '0'),
('PO-HAPPY-005', 2, 'HM_KR', 'HM-BASIC-TEE-M', 3000, '0'),
('PO-HAPPY-005', 3, 'HM_KR', 'HM-BASIC-TEE-L', 2000, '0'),
('PO-HAPPY-005', 4, 'HM_KR', 'HM-SLIM-JEANS-32', 1500, '0'),
('PO-HAPPY-005', 5, 'HM_KR', 'HM-SLIM-JEANS-34', 1500, '0')
ON CONFLICT (orderkey, orderlinenumber) DO NOTHING;

-- PO-HAPPY-006: India region PO (GST tracking)
INSERT INTO orders (orderkey, storerkey, facility, externorderkey, status, orderdate, expecteddate, suppliername, buyerref, susr1) VALUES
('PO-HAPPY-006', 'NIKE_IN', 'IN01', 'NIKE-IN-001', '0', CURRENT_DATE, CURRENT_DATE + INTERVAL '14 days', 'Nike Factory Vietnam', 'NIKE-IN-BUY', 'GST-IN-123456')
ON CONFLICT (orderkey) DO NOTHING;

INSERT INTO orderdetail (orderkey, orderlinenumber, storerkey, sku, qtyordered, status, lottable01, lottable02) VALUES
('PO-HAPPY-006', 1, 'NIKE_IN', 'NK-AIRMAX90-BLK', 300, '0', 'STYLE-AM90', 'COLOR-BLK'),
('PO-HAPPY-006', 2, 'NIKE_IN', 'NK-AF1-BLK', 200, '0', 'STYLE-AF1', 'COLOR-BLK')
ON CONFLICT (orderkey, orderlinenumber) DO NOTHING;

-- PO-HAPPY-007: Singapore region PO
INSERT INTO orders (orderkey, storerkey, facility, externorderkey, status, orderdate, expecteddate, suppliername, buyerref) VALUES
('PO-HAPPY-007', 'NIKE_SG', 'SG01', 'NIKE-SG-001', '0', CURRENT_DATE, CURRENT_DATE + INTERVAL '10 days', 'Nike Regional Hub', 'NIKE-SG-BUY')
ON CONFLICT (orderkey) DO NOTHING;

INSERT INTO orderdetail (orderkey, orderlinenumber, storerkey, sku, qtyordered, status) VALUES
('PO-HAPPY-007', 1, 'TEST_STORER_003', 'TEST-SKU-001', 150, '0')
ON CONFLICT (orderkey, orderlinenumber) DO NOTHING;

-- PO-HAPPY-008: Ready for population (Status: Open, complete data)
INSERT INTO orders (orderkey, storerkey, facility, externorderkey, status, orderdate, expecteddate, suppliername, buyerref) VALUES
('PO-HAPPY-008', 'TEST_STORER_001', 'TEST01', 'EXT-POPULATE-001', '0', CURRENT_DATE - INTERVAL '2 days', CURRENT_DATE + INTERVAL '5 days', 'Populate Test Supplier', 'POP-BUY-001')
ON CONFLICT (orderkey) DO NOTHING;

INSERT INTO orderdetail (orderkey, orderlinenumber, storerkey, sku, qtyordered, status) VALUES
('PO-HAPPY-008', 1, 'TEST_STORER_001', 'TEST-SKU-001', 100, '0'),
('PO-HAPPY-008', 2, 'TEST_STORER_001', 'TEST-SKU-002', 100, '0'),
('PO-HAPPY-008', 3, 'TEST_STORER_001', 'TEST-SKU-003', 100, '0')
ON CONFLICT (orderkey, orderlinenumber) DO NOTHING;

-- PO-HAPPY-009: Cross-dock eligible PO
INSERT INTO orders (orderkey, storerkey, facility, externorderkey, status, orderdate, expecteddate, suppliername, buyerref, susr2) VALUES
('PO-HAPPY-009', 'TEST_STORER_001', 'TEST01', 'EXT-XDOCK-001', '0', CURRENT_DATE, CURRENT_DATE + INTERVAL '3 days', 'CrossDock Supplier', 'XDOCK-BUY', 'CROSSDOCK')
ON CONFLICT (orderkey) DO NOTHING;

INSERT INTO orderdetail (orderkey, orderlinenumber, storerkey, sku, qtyordered, status) VALUES
('PO-HAPPY-009', 1, 'TEST_STORER_001', 'TEST-SKU-001', 50, '0'),
('PO-HAPPY-009', 2, 'TEST_STORER_001', 'TEST-SKU-002', 50, '0')
ON CONFLICT (orderkey, orderlinenumber) DO NOTHING;

-- PO-HAPPY-010: Batch processing PO (part of batch)
INSERT INTO orders (orderkey, storerkey, facility, externorderkey, status, orderdate, expecteddate, suppliername, buyerref, susr3) VALUES
('PO-HAPPY-010', 'TEST_STORER_001', 'TEST01', 'EXT-BATCH-001', '0', CURRENT_DATE, CURRENT_DATE + INTERVAL '7 days', 'Batch Supplier', 'BATCH-BUY', 'BATCH-2024-001')
ON CONFLICT (orderkey) DO NOTHING;

INSERT INTO orderdetail (orderkey, orderlinenumber, storerkey, sku, qtyordered, status) VALUES
('PO-HAPPY-010', 1, 'TEST_STORER_001', 'TEST-SKU-001', 100, '0'),
('PO-HAPPY-010', 2, 'TEST_STORER_001', 'TEST-SKU-002', 100, '0')
ON CONFLICT (orderkey, orderlinenumber) DO NOTHING;

-- ═══════════════════════════════════════════════════════════════════════════
-- Verification
-- ═══════════════════════════════════════════════════════════════════════════

DO $$
BEGIN
    RAISE NOTICE 'TD-PO-HAPPY Data Loaded:';
    RAISE NOTICE '  Happy Path POs: %', (SELECT COUNT(*) FROM orders WHERE orderkey LIKE 'PO-HAPPY-%');
    RAISE NOTICE '  Happy Path Lines: %', (SELECT COUNT(*) FROM orderdetail WHERE orderkey LIKE 'PO-HAPPY-%');
END $$;
