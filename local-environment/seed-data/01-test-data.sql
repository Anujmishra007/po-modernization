-- PO Modernization Test Data
-- This file runs after schema (01- prefix)

SET search_path TO dbo, public;

-- ═══════════════════════════════════════════════════════════
-- Storer Master (11 Storers - 3 Countries)
-- ═══════════════════════════════════════════════════════════

INSERT INTO storer (storerkey, company, type, status, country) VALUES
-- Korea Storers
('NIKE_KR', 'Nike Korea', '1', '1', 'KR'),
('HM_KR', 'H&M Korea', '1', '1', 'KR'),
('ADIDAS_KR', 'Adidas Korea', '1', '1', 'KR'),
-- India Storers
('NIKE_IN', 'Nike India', '1', '1', 'IN'),
('HM_IN', 'H&M India', '1', '1', 'IN'),
('UNILEVER_IN', 'Unilever India', '1', '1', 'IN'),
-- Singapore Storers
('NIKE_SG', 'Nike Singapore', '1', '1', 'SG'),
('ADIDAS_SG', 'Adidas Singapore', '1', '1', 'SG'),
-- Test Storers
('TEST_STORER_001', 'Test Storer 001', '1', '1', 'KR'),
('TEST_STORER_002', 'Test Storer 002', '1', '1', 'IN'),
('TEST_STORER_ERR', 'Error Test Storer', '1', '9', 'KR')  -- Inactive for error tests
ON CONFLICT (storerkey) DO NOTHING;

-- ═══════════════════════════════════════════════════════════
-- Facility Master
-- ═══════════════════════════════════════════════════════════

INSERT INTO facility (facility, storerkey, facilityname, country, status) VALUES
('KR01', 'NIKE_KR', 'Korea DC 1', 'KR', '1'),
('KR02', 'HM_KR', 'Korea DC 2', 'KR', '1'),
('IN01', 'NIKE_IN', 'India DC 1', 'IN', '1'),
('IN02', 'UNILEVER_IN', 'India DC 2', 'IN', '1'),
('SG01', 'NIKE_SG', 'Singapore DC 1', 'SG', '1'),
('TEST01', 'TEST_STORER_001', 'Test Facility 1', 'KR', '1')
ON CONFLICT (facility) DO NOTHING;

-- ═══════════════════════════════════════════════════════════
-- SKU Master (15 SKUs)
-- ═══════════════════════════════════════════════════════════

INSERT INTO sku (storerkey, sku, descr, lotcontrol, status) VALUES
-- Nike SKUs
('NIKE_KR', 'SHOE-001', 'Nike Air Max 90', 'Y', '1'),
('NIKE_KR', 'SHOE-002', 'Nike Air Force 1', 'Y', '1'),
('NIKE_KR', 'APPAREL-001', 'Nike Dri-FIT Shirt', 'Y', '1'),
('NIKE_IN', 'SHOE-001', 'Nike Air Max 90', 'Y', '1'),
('NIKE_SG', 'SHOE-001', 'Nike Air Max 90', 'Y', '1'),
-- H&M SKUs
('HM_KR', 'HM-SHIRT-001', 'Basic T-Shirt White', 'N', '1'),
('HM_KR', 'HM-PANTS-001', 'Slim Fit Jeans', 'N', '1'),
('HM_IN', 'HM-SHIRT-001', 'Basic T-Shirt White', 'N', '1'),
-- Adidas SKUs
('ADIDAS_KR', 'AD-SHOE-001', 'Adidas Ultraboost', 'Y', '1'),
('ADIDAS_SG', 'AD-SHOE-001', 'Adidas Ultraboost', 'Y', '1'),
-- Test SKUs
('TEST_STORER_001', 'TEST-SKU-001', 'Test SKU Valid', 'Y', '1'),
('TEST_STORER_001', 'TEST-SKU-002', 'Test SKU No Lot', 'N', '1'),
('TEST_STORER_001', 'TEST-SKU-003', 'Test SKU Perishable', 'Y', '1'),
('TEST_STORER_002', 'TEST-SKU-001', 'Test SKU India', 'Y', '1'),
('TEST_STORER_001', 'TEST-SKU-ERR', 'Test SKU Inactive', 'Y', '9')  -- Inactive for error tests
ON CONFLICT (storerkey, sku) DO NOTHING;

-- ═══════════════════════════════════════════════════════════
-- Location Master (15 Locations)
-- ═══════════════════════════════════════════════════════════

INSERT INTO loc (loc, facility, loctype, putawayzone, locationflag, status) VALUES
-- Receiving Locations
('RECV-01', 'KR01', 'RECV', 'RECEIVING', 'AVAILABLE', '1'),
('RECV-02', 'KR01', 'RECV', 'RECEIVING', 'AVAILABLE', '1'),
('RECV-03', 'IN01', 'RECV', 'RECEIVING', 'AVAILABLE', '1'),
-- Staging Locations
('STAGE-01', 'KR01', 'STAGE', 'STAGING', 'AVAILABLE', '1'),
('STAGE-02', 'KR01', 'STAGE', 'STAGING', 'AVAILABLE', '1'),
-- Storage Locations
('A-01-01', 'KR01', 'STORAGE', 'ZONE-A', 'AVAILABLE', '1'),
('A-01-02', 'KR01', 'STORAGE', 'ZONE-A', 'AVAILABLE', '1'),
('B-01-01', 'KR01', 'STORAGE', 'ZONE-B', 'AVAILABLE', '1'),
('B-01-02', 'KR01', 'STORAGE', 'ZONE-B', 'AVAILABLE', '1'),
-- Cross-Dock Locations
('XDOCK-01', 'KR01', 'XDOCK', 'CROSSDOCK', 'AVAILABLE', '1'),
('XDOCK-02', 'KR01', 'XDOCK', 'CROSSDOCK', 'AVAILABLE', '1'),
-- Test Locations
('TEST-LOC-01', 'TEST01', 'STORAGE', 'ZONE-A', 'AVAILABLE', '1'),
('TEST-LOC-02', 'TEST01', 'STORAGE', 'ZONE-B', 'AVAILABLE', '1'),
('TEST-LOC-FULL', 'TEST01', 'STORAGE', 'ZONE-A', 'FULL', '1'),  -- Full for edge case tests
('TEST-LOC-ERR', 'TEST01', 'STORAGE', 'ZONE-A', 'DISABLED', '9')  -- Disabled for error tests
ON CONFLICT (loc) DO NOTHING;

-- ═══════════════════════════════════════════════════════════
-- Code Lookup (Configuration)
-- ═══════════════════════════════════════════════════════════

INSERT INTO codelkup (listname, code, description) VALUES
-- PO Status
('POSTATUS', '0', 'Open'),
('POSTATUS', '5', 'In Progress'),
('POSTATUS', '9', 'Closed'),
('POSTATUS', 'X', 'Cancelled'),
-- Receipt Status
('RECEIPTSTATUS', '0', 'New'),
('RECEIPTSTATUS', '5', 'In Progress'),
('RECEIPTSTATUS', '9', 'Finalized'),
('RECEIPTSTATUS', 'X', 'Cancelled'),
-- Task Status
('TASKSTATUS', '0', 'Created'),
('TASKSTATUS', '1', 'Released'),
('TASKSTATUS', '5', 'In Progress'),
('TASKSTATUS', '9', 'Completed'),
-- Hold Codes
('HOLDCODE', 'QC', 'Quality Control'),
('HOLDCODE', 'DAMAGE', 'Damaged Goods'),
('HOLDCODE', 'RECALL', 'Product Recall')
ON CONFLICT (listname, code) DO NOTHING;

-- ═══════════════════════════════════════════════════════════
-- Sample POs for Testing (10 POs)
-- ═══════════════════════════════════════════════════════════

-- PO-TEST-001: Standard PO for happy path tests (Status: Open)
INSERT INTO orders (orderkey, storerkey, facility, externorderkey, status, expecteddate, suppliername) VALUES
('PO-TEST-001', 'TEST_STORER_001', 'TEST01', 'EXT-PO-001', '0', CURRENT_DATE + INTERVAL '7 days', 'Test Supplier 1')
ON CONFLICT (orderkey) DO NOTHING;

INSERT INTO orderdetail (orderkey, orderlinenumber, storerkey, sku, qtyordered, status) VALUES
('PO-TEST-001', 1, 'TEST_STORER_001', 'TEST-SKU-001', 100, '0'),
('PO-TEST-001', 2, 'TEST_STORER_001', 'TEST-SKU-002', 50, '0')
ON CONFLICT (orderkey, orderlinenumber) DO NOTHING;

-- PO-TEST-002: Already populated PO (Status: In Progress)
INSERT INTO orders (orderkey, storerkey, facility, externorderkey, status, expecteddate, suppliername) VALUES
('PO-TEST-002', 'TEST_STORER_001', 'TEST01', 'EXT-PO-002', '5', CURRENT_DATE + INTERVAL '5 days', 'Test Supplier 2')
ON CONFLICT (orderkey) DO NOTHING;

INSERT INTO orderdetail (orderkey, orderlinenumber, storerkey, sku, qtyordered, qtyreceived, status) VALUES
('PO-TEST-002', 1, 'TEST_STORER_001', 'TEST-SKU-001', 100, 100, '5')
ON CONFLICT (orderkey, orderlinenumber) DO NOTHING;

-- PO-TEST-003: Closed PO (Status: Closed)
INSERT INTO orders (orderkey, storerkey, facility, externorderkey, status, expecteddate, suppliername) VALUES
('PO-TEST-003', 'TEST_STORER_001', 'TEST01', 'EXT-PO-003', '9', CURRENT_DATE - INTERVAL '10 days', 'Test Supplier 3')
ON CONFLICT (orderkey) DO NOTHING;

INSERT INTO orderdetail (orderkey, orderlinenumber, storerkey, sku, qtyordered, qtyreceived, status) VALUES
('PO-TEST-003', 1, 'TEST_STORER_001', 'TEST-SKU-001', 100, 100, '9')
ON CONFLICT (orderkey, orderlinenumber) DO NOTHING;

-- PO-TEST-004: Cancelled PO (Status: Cancelled)
INSERT INTO orders (orderkey, storerkey, facility, externorderkey, status, expecteddate, suppliername) VALUES
('PO-TEST-004', 'TEST_STORER_001', 'TEST01', 'EXT-PO-004', 'X', CURRENT_DATE, 'Test Supplier 4')
ON CONFLICT (orderkey) DO NOTHING;

-- PO-NIKE-001: Nike Korea PO (for plugin tests)
INSERT INTO orders (orderkey, storerkey, facility, externorderkey, status, expecteddate, suppliername) VALUES
('PO-NIKE-001', 'NIKE_KR', 'KR01', 'NIKE-EXT-001', '0', CURRENT_DATE + INTERVAL '14 days', 'Nike Factory')
ON CONFLICT (orderkey) DO NOTHING;

INSERT INTO orderdetail (orderkey, orderlinenumber, storerkey, sku, qtyordered, status, lottable01, lottable02) VALUES
('PO-NIKE-001', 1, 'NIKE_KR', 'SHOE-001', 500, '0', 'STYLE-001', 'COLOR-BLK'),
('PO-NIKE-001', 2, 'NIKE_KR', 'SHOE-002', 300, '0', 'STYLE-002', 'COLOR-WHT'),
('PO-NIKE-001', 3, 'NIKE_KR', 'APPAREL-001', 1000, '0', 'STYLE-003', 'SIZE-M')
ON CONFLICT (orderkey, orderlinenumber) DO NOTHING;

-- PO-HM-001: H&M Korea PO (for plugin tests)
INSERT INTO orders (orderkey, storerkey, facility, externorderkey, status, expecteddate, suppliername) VALUES
('PO-HM-001', 'HM_KR', 'KR02', 'HM-EXT-001', '0', CURRENT_DATE + INTERVAL '10 days', 'H&M Supplier')
ON CONFLICT (orderkey) DO NOTHING;

INSERT INTO orderdetail (orderkey, orderlinenumber, storerkey, sku, qtyordered, status) VALUES
('PO-HM-001', 1, 'HM_KR', 'HM-SHIRT-001', 2000, '0'),
('PO-HM-001', 2, 'HM_KR', 'HM-PANTS-001', 1500, '0')
ON CONFLICT (orderkey, orderlinenumber) DO NOTHING;

-- ═══════════════════════════════════════════════════════════
-- Sample Receipts for Finalization Tests (5 Receipts)
-- ═══════════════════════════════════════════════════════════

-- RCV-TEST-001: Ready for finalization
INSERT INTO receipt (receiptkey, orderkey, storerkey, facility, externreceiptkey, status) VALUES
('RCV-TEST-001', 'PO-TEST-002', 'TEST_STORER_001', 'TEST01', 'EXT-RCV-001', '5')
ON CONFLICT (receiptkey) DO NOTHING;

INSERT INTO receiptdetail (receiptkey, receiptlinenumber, orderkey, orderlinenumber, storerkey, sku, qtyexpected, qtyreceived, status, toloc) VALUES
('RCV-TEST-001', 1, 'PO-TEST-002', 1, 'TEST_STORER_001', 'TEST-SKU-001', 100, 100, '5', 'TEST-LOC-01')
ON CONFLICT (receiptkey, receiptlinenumber) DO NOTHING;

-- RCV-TEST-002: Already finalized
INSERT INTO receipt (receiptkey, orderkey, storerkey, facility, externreceiptkey, status, finalizationdate) VALUES
('RCV-TEST-002', 'PO-TEST-003', 'TEST_STORER_001', 'TEST01', 'EXT-RCV-002', '9', CURRENT_TIMESTAMP - INTERVAL '5 days')
ON CONFLICT (receiptkey) DO NOTHING;

INSERT INTO receiptdetail (receiptkey, receiptlinenumber, orderkey, orderlinenumber, storerkey, sku, qtyexpected, qtyreceived, status, toloc) VALUES
('RCV-TEST-002', 1, 'PO-TEST-003', 1, 'TEST_STORER_001', 'TEST-SKU-001', 100, 100, '9', 'TEST-LOC-01')
ON CONFLICT (receiptkey, receiptlinenumber) DO NOTHING;

-- RCV-NIKE-001: Nike receipt for plugin testing
INSERT INTO receipt (receiptkey, orderkey, storerkey, facility, externreceiptkey, status) VALUES
('RCV-NIKE-001', 'PO-NIKE-001', 'NIKE_KR', 'KR01', 'NIKE-RCV-001', '5')
ON CONFLICT (receiptkey) DO NOTHING;

INSERT INTO receiptdetail (receiptkey, receiptlinenumber, orderkey, orderlinenumber, storerkey, sku, qtyexpected, qtyreceived, status, toloc, lottable01, lottable02) VALUES
('RCV-NIKE-001', 1, 'PO-NIKE-001', 1, 'NIKE_KR', 'SHOE-001', 500, 500, '5', 'RECV-01', 'STYLE-001', 'COLOR-BLK'),
('RCV-NIKE-001', 2, 'PO-NIKE-001', 2, 'NIKE_KR', 'SHOE-002', 300, 300, '5', 'RECV-01', 'STYLE-002', 'COLOR-WHT')
ON CONFLICT (receiptkey, receiptlinenumber) DO NOTHING;

-- ═══════════════════════════════════════════════════════════
-- Verification
-- ═══════════════════════════════════════════════════════════

-- Verify counts
DO $$
BEGIN
    RAISE NOTICE 'Test Data Loaded:';
    RAISE NOTICE '  Storers: %', (SELECT COUNT(*) FROM storer);
    RAISE NOTICE '  Facilities: %', (SELECT COUNT(*) FROM facility);
    RAISE NOTICE '  SKUs: %', (SELECT COUNT(*) FROM sku);
    RAISE NOTICE '  Locations: %', (SELECT COUNT(*) FROM loc);
    RAISE NOTICE '  POs: %', (SELECT COUNT(*) FROM orders);
    RAISE NOTICE '  PO Details: %', (SELECT COUNT(*) FROM orderdetail);
    RAISE NOTICE '  Receipts: %', (SELECT COUNT(*) FROM receipt);
    RAISE NOTICE '  Receipt Details: %', (SELECT COUNT(*) FROM receiptdetail);
END $$;
