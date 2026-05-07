-- ═══════════════════════════════════════════════════════════════════════════
-- TD-PO-ERROR.sql - PO Test Data for Error/Edge Case Scenarios
-- ═══════════════════════════════════════════════════════════════════════════
-- Test Data Set: TD-PO-ERROR
-- Required: 5+ POs for error scenario testing
-- Entities: ORDERS, ORDERDETAIL
-- Used By: F1 Error Cases, Compensation Testing
-- ═══════════════════════════════════════════════════════════════════════════

SET search_path TO dbo, public;

-- ═══════════════════════════════════════════════════════════════════════════
-- Error Scenario POs (Status-based errors)
-- ═══════════════════════════════════════════════════════════════════════════

-- PO-ERR-001: Already Populated PO (Status: 5 - In Progress)
-- Used for: "PO already populated" error test
INSERT INTO orders (orderkey, storerkey, facility, externorderkey, status, orderdate, expecteddate, suppliername) VALUES
('PO-ERR-001', 'TEST_STORER_001', 'TEST01', 'EXT-ERR-001', '5', CURRENT_DATE - INTERVAL '5 days', CURRENT_DATE + INTERVAL '2 days', 'Error Test Supplier 1')
ON CONFLICT (orderkey) DO NOTHING;

INSERT INTO orderdetail (orderkey, orderlinenumber, storerkey, sku, qtyordered, qtyreceived, status) VALUES
('PO-ERR-001', 1, 'TEST_STORER_001', 'TEST-SKU-001', 100, 100, '5'),
('PO-ERR-001', 2, 'TEST_STORER_001', 'TEST-SKU-002', 50, 50, '5')
ON CONFLICT (orderkey, orderlinenumber) DO NOTHING;

-- PO-ERR-002: Closed PO (Status: 9 - Closed)
-- Used for: "PO already closed" error test
INSERT INTO orders (orderkey, storerkey, facility, externorderkey, status, orderdate, expecteddate, closeddate, suppliername) VALUES
('PO-ERR-002', 'TEST_STORER_001', 'TEST01', 'EXT-ERR-002', '9', CURRENT_DATE - INTERVAL '30 days', CURRENT_DATE - INTERVAL '20 days', CURRENT_DATE - INTERVAL '15 days', 'Error Test Supplier 2')
ON CONFLICT (orderkey) DO NOTHING;

INSERT INTO orderdetail (orderkey, orderlinenumber, storerkey, sku, qtyordered, qtyreceived, status) VALUES
('PO-ERR-002', 1, 'TEST_STORER_001', 'TEST-SKU-001', 100, 100, '9')
ON CONFLICT (orderkey, orderlinenumber) DO NOTHING;

-- PO-ERR-003: Cancelled PO (Status: X - Cancelled)
-- Used for: "PO cancelled" error test
INSERT INTO orders (orderkey, storerkey, facility, externorderkey, status, orderdate, expecteddate, suppliername) VALUES
('PO-ERR-003', 'TEST_STORER_001', 'TEST01', 'EXT-ERR-003', 'X', CURRENT_DATE - INTERVAL '10 days', CURRENT_DATE, 'Error Test Supplier 3')
ON CONFLICT (orderkey) DO NOTHING;

INSERT INTO orderdetail (orderkey, orderlinenumber, storerkey, sku, qtyordered, status) VALUES
('PO-ERR-003', 1, 'TEST_STORER_001', 'TEST-SKU-001', 100, 'X')
ON CONFLICT (orderkey, orderlinenumber) DO NOTHING;

-- PO-ERR-004: PO with Inactive Storer
-- Used for: "Invalid storer" error test
INSERT INTO orders (orderkey, storerkey, facility, externorderkey, status, orderdate, expecteddate, suppliername) VALUES
('PO-ERR-004', 'TEST_STORER_ERR', 'TEST01', 'EXT-ERR-004', '0', CURRENT_DATE, CURRENT_DATE + INTERVAL '7 days', 'Error Test Supplier 4')
ON CONFLICT (orderkey) DO NOTHING;

INSERT INTO orderdetail (orderkey, orderlinenumber, storerkey, sku, qtyordered, status) VALUES
('PO-ERR-004', 1, 'TEST_STORER_ERR', 'TEST-SKU-001', 100, '0')
ON CONFLICT (orderkey, orderlinenumber) DO NOTHING;

-- PO-ERR-005: PO with Inactive SKU
-- Used for: "Invalid SKU" error test
INSERT INTO orders (orderkey, storerkey, facility, externorderkey, status, orderdate, expecteddate, suppliername) VALUES
('PO-ERR-005', 'TEST_STORER_001', 'TEST01', 'EXT-ERR-005', '0', CURRENT_DATE, CURRENT_DATE + INTERVAL '7 days', 'Error Test Supplier 5')
ON CONFLICT (orderkey) DO NOTHING;

INSERT INTO orderdetail (orderkey, orderlinenumber, storerkey, sku, qtyordered, status) VALUES
('PO-ERR-005', 1, 'TEST_STORER_001', 'TEST-SKU-ERR', 100, '0')  -- Inactive SKU
ON CONFLICT (orderkey, orderlinenumber) DO NOTHING;

-- PO-ERR-006: PO with Past Expected Date
-- Used for: "Past expected date" edge case
INSERT INTO orders (orderkey, storerkey, facility, externorderkey, status, orderdate, expecteddate, suppliername) VALUES
('PO-ERR-006', 'TEST_STORER_001', 'TEST01', 'EXT-ERR-006', '0', CURRENT_DATE - INTERVAL '30 days', CURRENT_DATE - INTERVAL '10 days', 'Error Test Supplier 6')
ON CONFLICT (orderkey) DO NOTHING;

INSERT INTO orderdetail (orderkey, orderlinenumber, storerkey, sku, qtyordered, status) VALUES
('PO-ERR-006', 1, 'TEST_STORER_001', 'TEST-SKU-001', 100, '0')
ON CONFLICT (orderkey, orderlinenumber) DO NOTHING;

-- PO-ERR-007: PO with Zero Quantity Line
-- Used for: "Zero quantity" validation test
INSERT INTO orders (orderkey, storerkey, facility, externorderkey, status, orderdate, expecteddate, suppliername) VALUES
('PO-ERR-007', 'TEST_STORER_001', 'TEST01', 'EXT-ERR-007', '0', CURRENT_DATE, CURRENT_DATE + INTERVAL '7 days', 'Error Test Supplier 7')
ON CONFLICT (orderkey) DO NOTHING;

INSERT INTO orderdetail (orderkey, orderlinenumber, storerkey, sku, qtyordered, status) VALUES
('PO-ERR-007', 1, 'TEST_STORER_001', 'TEST-SKU-001', 0, '0'),  -- Zero qty
('PO-ERR-007', 2, 'TEST_STORER_001', 'TEST-SKU-002', 100, '0')  -- Valid line
ON CONFLICT (orderkey, orderlinenumber) DO NOTHING;

-- PO-ERR-008: PO for Concurrency Test (will be updated concurrently)
INSERT INTO orders (orderkey, storerkey, facility, externorderkey, status, orderdate, expecteddate, suppliername) VALUES
('PO-ERR-008', 'TEST_STORER_001', 'TEST01', 'EXT-CONCURRENT-001', '0', CURRENT_DATE, CURRENT_DATE + INTERVAL '7 days', 'Concurrency Test Supplier')
ON CONFLICT (orderkey) DO NOTHING;

INSERT INTO orderdetail (orderkey, orderlinenumber, storerkey, sku, qtyordered, status) VALUES
('PO-ERR-008', 1, 'TEST_STORER_001', 'TEST-SKU-001', 100, '0')
ON CONFLICT (orderkey, orderlinenumber) DO NOTHING;

-- ═══════════════════════════════════════════════════════════════════════════
-- Edge Case POs
-- ═══════════════════════════════════════════════════════════════════════════

-- PO-EDGE-001: Max Lines PO (for boundary testing - 100 lines)
INSERT INTO orders (orderkey, storerkey, facility, externorderkey, status, orderdate, expecteddate, suppliername) VALUES
('PO-EDGE-001', 'TEST_STORER_001', 'TEST01', 'EXT-EDGE-001', '0', CURRENT_DATE, CURRENT_DATE + INTERVAL '14 days', 'Edge Case Supplier')
ON CONFLICT (orderkey) DO NOTHING;

-- Insert 100 lines for max boundary test
DO $$
DECLARE
    i INT;
BEGIN
    FOR i IN 1..100 LOOP
        INSERT INTO orderdetail (orderkey, orderlinenumber, storerkey, sku, qtyordered, status)
        VALUES ('PO-EDGE-001', i, 'TEST_STORER_001',
                CASE WHEN i % 3 = 0 THEN 'TEST-SKU-001'
                     WHEN i % 3 = 1 THEN 'TEST-SKU-002'
                     ELSE 'TEST-SKU-003' END,
                (i * 10), '0')
        ON CONFLICT (orderkey, orderlinenumber) DO NOTHING;
    END LOOP;
END $$;

-- PO-EDGE-002: Unicode Characters in Fields
INSERT INTO orders (orderkey, storerkey, facility, externorderkey, status, orderdate, expecteddate, suppliername, susr1) VALUES
('PO-EDGE-002', 'TEST_STORER_001', 'TEST01', 'EXT-UNICODE-001', '0', CURRENT_DATE, CURRENT_DATE + INTERVAL '7 days', '한국 공급업체 (Korean Supplier)', '特殊字符测试')
ON CONFLICT (orderkey) DO NOTHING;

INSERT INTO orderdetail (orderkey, orderlinenumber, storerkey, sku, qtyordered, status) VALUES
('PO-EDGE-002', 1, 'TEST_STORER_001', 'TEST-SKU-001', 100, '0')
ON CONFLICT (orderkey, orderlinenumber) DO NOTHING;

-- ═══════════════════════════════════════════════════════════════════════════
-- Verification
-- ═══════════════════════════════════════════════════════════════════════════

DO $$
BEGIN
    RAISE NOTICE 'TD-PO-ERROR Data Loaded:';
    RAISE NOTICE '  Error POs: %', (SELECT COUNT(*) FROM orders WHERE orderkey LIKE 'PO-ERR-%');
    RAISE NOTICE '  Edge Case POs: %', (SELECT COUNT(*) FROM orders WHERE orderkey LIKE 'PO-EDGE-%');
    RAISE NOTICE '  Total Error/Edge Lines: %', (SELECT COUNT(*) FROM orderdetail WHERE orderkey LIKE 'PO-ERR-%' OR orderkey LIKE 'PO-EDGE-%');
END $$;
