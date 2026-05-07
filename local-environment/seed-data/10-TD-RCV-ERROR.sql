-- ═══════════════════════════════════════════════════════════════════════════
-- TD-RCV-ERROR.sql - Receipt Test Data for Error/Edge Case Scenarios
-- ═══════════════════════════════════════════════════════════════════════════
-- Test Data Set: TD-RCV-ERROR
-- Required: 3+ Receipts for error scenario testing
-- Entities: RECEIPT, RECEIPTDETAIL
-- Used By: F3 Error Cases, Compensation Testing
-- ═══════════════════════════════════════════════════════════════════════════

SET search_path TO dbo, public;

-- ═══════════════════════════════════════════════════════════════════════════
-- Error Scenario Receipts
-- ═══════════════════════════════════════════════════════════════════════════

-- RCV-ERR-001: Already Finalized Receipt (Status: 9)
-- Used for: "Receipt already finalized" error test
INSERT INTO receipt (receiptkey, orderkey, storerkey, facility, externreceiptkey, status, receiptdate, finalizationdate) VALUES
('RCV-ERR-001', 'PO-ERR-002', 'TEST_STORER_001', 'TEST01', 'EXT-ERR-RCV-001', '9', CURRENT_TIMESTAMP - INTERVAL '10 days', CURRENT_TIMESTAMP - INTERVAL '5 days')
ON CONFLICT (receiptkey) DO NOTHING;

INSERT INTO receiptdetail (receiptdetailkey, receiptkey, receiptlinenumber, orderkey, orderlinenumber, storerkey, sku, qtyexpected, qtyreceived, status, toloc) VALUES
('RCV-ERR-001-1', 'RCV-ERR-001', 1, 'PO-ERR-002', 1, 'TEST_STORER_001', 'TEST-SKU-001', 100, 100, '9', 'TEST-LOC-01')
ON CONFLICT (receiptdetailkey) DO NOTHING;

-- RCV-ERR-002: Cancelled Receipt (Status: X)
-- Used for: "Receipt cancelled" error test
INSERT INTO receipt (receiptkey, orderkey, storerkey, facility, externreceiptkey, status, receiptdate) VALUES
('RCV-ERR-002', 'PO-ERR-003', 'TEST_STORER_001', 'TEST01', 'EXT-ERR-RCV-002', 'X', CURRENT_TIMESTAMP - INTERVAL '5 days')
ON CONFLICT (receiptkey) DO NOTHING;

INSERT INTO receiptdetail (receiptdetailkey, receiptkey, receiptlinenumber, orderkey, orderlinenumber, storerkey, sku, qtyexpected, qtyreceived, status, toloc) VALUES
('RCV-ERR-002-1', 'RCV-ERR-002', 1, 'PO-ERR-003', 1, 'TEST_STORER_001', 'TEST-SKU-001', 100, 0, 'X', 'TEST-LOC-01')
ON CONFLICT (receiptdetailkey) DO NOTHING;

-- RCV-ERR-003: Over-Receipt (120% - exceeds tolerance)
-- Used for: "Over-receipt" error/warning test
INSERT INTO receipt (receiptkey, orderkey, storerkey, facility, externreceiptkey, status, receiptdate) VALUES
('RCV-ERR-003', 'PO-HAPPY-001', 'TEST_STORER_001', 'TEST01', 'EXT-OVER-RCV-001', '5', CURRENT_TIMESTAMP)
ON CONFLICT (receiptkey) DO NOTHING;

INSERT INTO receiptdetail (receiptdetailkey, receiptkey, receiptlinenumber, orderkey, orderlinenumber, storerkey, sku, qtyexpected, qtyreceived, status, toloc) VALUES
('RCV-ERR-003-1', 'RCV-ERR-003', 1, 'PO-HAPPY-001', 1, 'TEST_STORER_001', 'TEST-SKU-001', 100, 120, '5', 'TEST-RECV-01')  -- 120% received
ON CONFLICT (receiptdetailkey) DO NOTHING;

-- RCV-ERR-004: Receipt to Full Location
-- Used for: "Location full" error test
INSERT INTO receipt (receiptkey, orderkey, storerkey, facility, externreceiptkey, status, receiptdate) VALUES
('RCV-ERR-004', 'PO-HAPPY-003', 'TEST_STORER_001', 'TEST01', 'EXT-FULL-LOC-001', '5', CURRENT_TIMESTAMP)
ON CONFLICT (receiptkey) DO NOTHING;

INSERT INTO receiptdetail (receiptdetailkey, receiptkey, receiptlinenumber, orderkey, orderlinenumber, storerkey, sku, qtyexpected, qtyreceived, status, toloc) VALUES
('RCV-ERR-004-1', 'RCV-ERR-004', 1, 'PO-HAPPY-003', 1, 'TEST_STORER_001', 'TEST-SKU-001', 10000, 10000, '5', 'TEST-LOC-FULL')  -- Full location
ON CONFLICT (receiptdetailkey) DO NOTHING;

-- RCV-ERR-005: Receipt with Missing Lottable (Korea customs requirement)
-- Used for: "Missing required lottable" error test
INSERT INTO receipt (receiptkey, orderkey, storerkey, facility, externreceiptkey, status, receiptdate) VALUES
('RCV-ERR-005', 'PO-HAPPY-004', 'NIKE_KR', 'KR01', 'NIKE-MISSING-LOT-001', '5', CURRENT_TIMESTAMP)
ON CONFLICT (receiptkey) DO NOTHING;

INSERT INTO receiptdetail (receiptdetailkey, receiptkey, receiptlinenumber, orderkey, orderlinenumber, storerkey, sku, qtyexpected, qtyreceived, status, toloc) VALUES
('RCV-ERR-005-1', 'RCV-ERR-005', 1, 'PO-HAPPY-004', 1, 'NIKE_KR', 'NK-AIRMAX90-BLK', 500, 500, '5', 'KR01-RECV-01')  -- Missing lottables
ON CONFLICT (receiptdetailkey) DO NOTHING;

-- ═══════════════════════════════════════════════════════════════════════════
-- Edge Case Receipts
-- ═══════════════════════════════════════════════════════════════════════════

-- RCV-EDGE-001: Zero Quantity Receipt Line
INSERT INTO receipt (receiptkey, orderkey, storerkey, facility, externreceiptkey, status, receiptdate) VALUES
('RCV-EDGE-001', 'PO-HAPPY-002', 'TEST_STORER_001', 'TEST01', 'EXT-ZERO-RCV-001', '5', CURRENT_TIMESTAMP)
ON CONFLICT (receiptkey) DO NOTHING;

INSERT INTO receiptdetail (receiptdetailkey, receiptkey, receiptlinenumber, orderkey, orderlinenumber, storerkey, sku, qtyexpected, qtyreceived, status, toloc) VALUES
('RCV-EDGE-001-1', 'RCV-EDGE-001', 1, 'PO-HAPPY-002', 1, 'TEST_STORER_001', 'TEST-SKU-001', 200, 0, '5', 'TEST-RECV-01'),  -- Zero received
('RCV-EDGE-001-2', 'RCV-EDGE-001', 2, 'PO-HAPPY-002', 2, 'TEST_STORER_001', 'TEST-SKU-002', 150, 150, '5', 'TEST-RECV-01')  -- Normal
ON CONFLICT (receiptdetailkey) DO NOTHING;

-- RCV-EDGE-002: Receipt for Concurrent Finalization Test
INSERT INTO receipt (receiptkey, orderkey, storerkey, facility, externreceiptkey, status, receiptdate) VALUES
('RCV-EDGE-002', 'PO-ERR-008', 'TEST_STORER_001', 'TEST01', 'EXT-CONCURRENT-RCV', '5', CURRENT_TIMESTAMP)
ON CONFLICT (receiptkey) DO NOTHING;

INSERT INTO receiptdetail (receiptdetailkey, receiptkey, receiptlinenumber, orderkey, orderlinenumber, storerkey, sku, qtyexpected, qtyreceived, status, toloc) VALUES
('RCV-EDGE-002-1', 'RCV-EDGE-002', 1, 'PO-ERR-008', 1, 'TEST_STORER_001', 'TEST-SKU-001', 100, 100, '5', 'TEST-RECV-01')
ON CONFLICT (receiptdetailkey) DO NOTHING;

-- ═══════════════════════════════════════════════════════════════════════════
-- Verification
-- ═══════════════════════════════════════════════════════════════════════════

DO $$
BEGIN
    RAISE NOTICE 'TD-RCV-ERROR Data Loaded:';
    RAISE NOTICE '  Error Receipts: %', (SELECT COUNT(*) FROM receipt WHERE receiptkey LIKE 'RCV-ERR-%');
    RAISE NOTICE '  Edge Case Receipts: %', (SELECT COUNT(*) FROM receipt WHERE receiptkey LIKE 'RCV-EDGE-%');
    RAISE NOTICE '  Total Error/Edge Lines: %', (SELECT COUNT(*) FROM receiptdetail WHERE receiptkey LIKE 'RCV-ERR-%' OR receiptkey LIKE 'RCV-EDGE-%');
END $$;
