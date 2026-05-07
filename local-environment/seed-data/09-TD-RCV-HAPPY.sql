-- ═══════════════════════════════════════════════════════════════════════════
-- TD-RCV-HAPPY.sql - Receipt Test Data for Happy Path Scenarios
-- ═══════════════════════════════════════════════════════════════════════════
-- Test Data Set: TD-RCV-HAPPY
-- Required: 5 Receipts for happy path testing
-- Entities: RECEIPT, RECEIPTDETAIL
-- Used By: F3 (Receipt Finalization)
-- ═══════════════════════════════════════════════════════════════════════════

SET search_path TO dbo, public;

-- ═══════════════════════════════════════════════════════════════════════════
-- Prerequisites: Create populated POs for receipts
-- ═══════════════════════════════════════════════════════════════════════════

-- PO for RCV-HAPPY-001 (Already in progress)
INSERT INTO orders (orderkey, storerkey, facility, externorderkey, status, orderdate, expecteddate, suppliername) VALUES
('PO-FOR-RCV-001', 'TEST_STORER_001', 'TEST01', 'EXT-RCV-PO-001', '5', CURRENT_DATE - INTERVAL '3 days', CURRENT_DATE + INTERVAL '4 days', 'Receipt Test Supplier 1')
ON CONFLICT (orderkey) DO NOTHING;

INSERT INTO orderdetail (orderkey, orderlinenumber, storerkey, sku, qtyordered, qtyreceived, status) VALUES
('PO-FOR-RCV-001', 1, 'TEST_STORER_001', 'TEST-SKU-001', 100, 100, '5'),
('PO-FOR-RCV-001', 2, 'TEST_STORER_001', 'TEST-SKU-002', 50, 50, '5')
ON CONFLICT (orderkey, orderlinenumber) DO NOTHING;

-- PO for RCV-HAPPY-002 (partial receipt scenario)
INSERT INTO orders (orderkey, storerkey, facility, externorderkey, status, orderdate, expecteddate, suppliername) VALUES
('PO-FOR-RCV-002', 'TEST_STORER_001', 'TEST01', 'EXT-RCV-PO-002', '5', CURRENT_DATE - INTERVAL '5 days', CURRENT_DATE + INTERVAL '2 days', 'Receipt Test Supplier 2')
ON CONFLICT (orderkey) DO NOTHING;

INSERT INTO orderdetail (orderkey, orderlinenumber, storerkey, sku, qtyordered, qtyreceived, status) VALUES
('PO-FOR-RCV-002', 1, 'TEST_STORER_001', 'TEST-SKU-001', 200, 160, '5'),  -- 80% received
('PO-FOR-RCV-002', 2, 'TEST_STORER_001', 'TEST-SKU-002', 100, 80, '5')    -- 80% received
ON CONFLICT (orderkey, orderlinenumber) DO NOTHING;

-- PO for RCV-HAPPY-003 (Nike with lottables)
INSERT INTO orders (orderkey, storerkey, facility, externorderkey, status, orderdate, expecteddate, suppliername) VALUES
('PO-FOR-RCV-003', 'NIKE_KR', 'KR01', 'NIKE-RCV-PO-001', '5', CURRENT_DATE - INTERVAL '7 days', CURRENT_DATE, 'Nike Factory')
ON CONFLICT (orderkey) DO NOTHING;

INSERT INTO orderdetail (orderkey, orderlinenumber, storerkey, sku, qtyordered, qtyreceived, status, lottable01, lottable02) VALUES
('PO-FOR-RCV-003', 1, 'NIKE_KR', 'NK-AIRMAX90-BLK', 500, 500, '5', 'STYLE-AM90', 'COLOR-BLK'),
('PO-FOR-RCV-003', 2, 'NIKE_KR', 'NK-AF1-BLK', 300, 300, '5', 'STYLE-AF1', 'COLOR-BLK')
ON CONFLICT (orderkey, orderlinenumber) DO NOTHING;

-- ═══════════════════════════════════════════════════════════════════════════
-- Happy Path Receipts (5 Receipts)
-- ═══════════════════════════════════════════════════════════════════════════

-- RCV-HAPPY-001: Single receipt ready for finalization (100% received)
INSERT INTO receipt (receiptkey, orderkey, storerkey, facility, externreceiptkey, status, receiptdate) VALUES
('RCV-HAPPY-001', 'PO-FOR-RCV-001', 'TEST_STORER_001', 'TEST01', 'EXT-RCV-001', '5', CURRENT_TIMESTAMP - INTERVAL '1 day')
ON CONFLICT (receiptkey) DO NOTHING;

INSERT INTO receiptdetail (receiptdetailkey, receiptkey, receiptlinenumber, orderkey, orderlinenumber, storerkey, sku, qtyexpected, qtyreceived, status, toloc) VALUES
('RCV-HAPPY-001-1', 'RCV-HAPPY-001', 1, 'PO-FOR-RCV-001', 1, 'TEST_STORER_001', 'TEST-SKU-001', 100, 100, '5', 'TEST-RECV-01'),
('RCV-HAPPY-001-2', 'RCV-HAPPY-001', 2, 'PO-FOR-RCV-001', 2, 'TEST_STORER_001', 'TEST-SKU-002', 50, 50, '5', 'TEST-RECV-01')
ON CONFLICT (receiptdetailkey) DO NOTHING;

-- RCV-HAPPY-002: Partial receipt (80% - within tolerance)
INSERT INTO receipt (receiptkey, orderkey, storerkey, facility, externreceiptkey, status, receiptdate) VALUES
('RCV-HAPPY-002', 'PO-FOR-RCV-002', 'TEST_STORER_001', 'TEST01', 'EXT-RCV-002', '5', CURRENT_TIMESTAMP - INTERVAL '2 days')
ON CONFLICT (receiptkey) DO NOTHING;

INSERT INTO receiptdetail (receiptdetailkey, receiptkey, receiptlinenumber, orderkey, orderlinenumber, storerkey, sku, qtyexpected, qtyreceived, status, toloc) VALUES
('RCV-HAPPY-002-1', 'RCV-HAPPY-002', 1, 'PO-FOR-RCV-002', 1, 'TEST_STORER_001', 'TEST-SKU-001', 200, 160, '5', 'TEST-RECV-01'),
('RCV-HAPPY-002-2', 'RCV-HAPPY-002', 2, 'PO-FOR-RCV-002', 2, 'TEST_STORER_001', 'TEST-SKU-002', 100, 80, '5', 'TEST-RECV-02')
ON CONFLICT (receiptdetailkey) DO NOTHING;

-- RCV-HAPPY-003: Nike receipt with lottable tracking
INSERT INTO receipt (receiptkey, orderkey, storerkey, facility, externreceiptkey, status, receiptdate) VALUES
('RCV-HAPPY-003', 'PO-FOR-RCV-003', 'NIKE_KR', 'KR01', 'NIKE-EXT-RCV-001', '5', CURRENT_TIMESTAMP - INTERVAL '1 day')
ON CONFLICT (receiptkey) DO NOTHING;

INSERT INTO receiptdetail (receiptdetailkey, receiptkey, receiptlinenumber, orderkey, orderlinenumber, storerkey, sku, qtyexpected, qtyreceived, status, toloc, lottable01, lottable02) VALUES
('RCV-HAPPY-003-1', 'RCV-HAPPY-003', 1, 'PO-FOR-RCV-003', 1, 'NIKE_KR', 'NK-AIRMAX90-BLK', 500, 500, '5', 'KR01-RECV-01', 'STYLE-AM90', 'COLOR-BLK'),
('RCV-HAPPY-003-2', 'RCV-HAPPY-003', 2, 'PO-FOR-RCV-003', 2, 'NIKE_KR', 'NK-AF1-BLK', 300, 300, '5', 'KR01-RECV-02', 'STYLE-AF1', 'COLOR-BLK')
ON CONFLICT (receiptdetailkey) DO NOTHING;

-- RCV-HAPPY-004: Receipt with cross-dock allocation
INSERT INTO receipt (receiptkey, orderkey, storerkey, facility, externreceiptkey, status, receiptdate, susr1) VALUES
('RCV-HAPPY-004', 'PO-HAPPY-009', 'TEST_STORER_001', 'TEST01', 'EXT-XDOCK-RCV-001', '5', CURRENT_TIMESTAMP, 'CROSSDOCK')
ON CONFLICT (receiptkey) DO NOTHING;

INSERT INTO receiptdetail (receiptdetailkey, receiptkey, receiptlinenumber, orderkey, orderlinenumber, storerkey, sku, qtyexpected, qtyreceived, status, toloc) VALUES
('RCV-HAPPY-004-1', 'RCV-HAPPY-004', 1, 'PO-HAPPY-009', 1, 'TEST_STORER_001', 'TEST-SKU-001', 50, 50, '5', 'TEST-XDOCK-01'),
('RCV-HAPPY-004-2', 'RCV-HAPPY-004', 2, 'PO-HAPPY-009', 2, 'TEST_STORER_001', 'TEST-SKU-002', 50, 50, '5', 'TEST-XDOCK-02')
ON CONFLICT (receiptdetailkey) DO NOTHING;

-- RCV-HAPPY-005: Multi-line receipt (batch finalization test)
INSERT INTO receipt (receiptkey, orderkey, storerkey, facility, externreceiptkey, status, receiptdate) VALUES
('RCV-HAPPY-005', 'PO-HAPPY-002', 'TEST_STORER_001', 'TEST01', 'EXT-BATCH-RCV-001', '5', CURRENT_TIMESTAMP)
ON CONFLICT (receiptkey) DO NOTHING;

INSERT INTO receiptdetail (receiptdetailkey, receiptkey, receiptlinenumber, orderkey, orderlinenumber, storerkey, sku, qtyexpected, qtyreceived, status, toloc) VALUES
('RCV-HAPPY-005-1', 'RCV-HAPPY-005', 1, 'PO-HAPPY-002', 1, 'TEST_STORER_001', 'TEST-SKU-001', 200, 200, '5', 'TEST-RECV-01'),
('RCV-HAPPY-005-2', 'RCV-HAPPY-005', 2, 'PO-HAPPY-002', 2, 'TEST_STORER_001', 'TEST-SKU-002', 150, 150, '5', 'TEST-RECV-01'),
('RCV-HAPPY-005-3', 'RCV-HAPPY-005', 3, 'PO-HAPPY-002', 3, 'TEST_STORER_001', 'TEST-SKU-003', 100, 100, '5', 'TEST-RECV-02'),
('RCV-HAPPY-005-4', 'RCV-HAPPY-005', 4, 'PO-HAPPY-002', 4, 'TEST_STORER_001', 'TEST-SKU-001', 50, 50, '5', 'TEST-RECV-02'),
('RCV-HAPPY-005-5', 'RCV-HAPPY-005', 5, 'PO-HAPPY-002', 5, 'TEST_STORER_001', 'TEST-SKU-002', 75, 75, '5', 'TEST-RECV-01')
ON CONFLICT (receiptdetailkey) DO NOTHING;

-- ═══════════════════════════════════════════════════════════════════════════
-- Verification
-- ═══════════════════════════════════════════════════════════════════════════

DO $$
BEGIN
    RAISE NOTICE 'TD-RCV-HAPPY Data Loaded:';
    RAISE NOTICE '  Happy Path Receipts: %', (SELECT COUNT(*) FROM receipt WHERE receiptkey LIKE 'RCV-HAPPY-%');
    RAISE NOTICE '  Happy Path Receipt Lines: %', (SELECT COUNT(*) FROM receiptdetail WHERE receiptkey LIKE 'RCV-HAPPY-%');
END $$;
