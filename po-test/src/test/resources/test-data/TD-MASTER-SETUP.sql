-- ═══════════════════════════════════════════════════════════════
-- PO MODERNIZATION - MASTER TEST DATA SETUP
-- ═══════════════════════════════════════════════════════════════
-- Version: 1.0
-- Purpose: Complete test data setup for E2E testing
-- Usage: Run this script to set up all test data in local DB
-- ═══════════════════════════════════════════════════════════════

SET search_path TO dbo, public;

-- ═══════════════════════════════════════════════════════════════
-- SECTION 1: STORER TEST DATA (TD-STORER-001)
-- ═══════════════════════════════════════════════════════════════

DELETE FROM dbo.storer WHERE storerkey LIKE 'TEST_%' OR storerkey IN ('STORER001', 'STORER002', 'NIKE_STORER', 'HM_STORER', 'ADIDAS_STORER', 'COLUMBIA_STORER', 'UNILEVER_STORER');

INSERT INTO dbo.storer (storerkey, company, type, status) VALUES
-- Standard Test Storers
('STORER001', 'Test Company Alpha', 1, 'Active'),
('STORER002', 'Test Company Beta', 1, 'Active'),
-- Client-Specific Storers
('NIKE_STORER', 'Nike Inc', 1, 'Active'),
('HM_STORER', 'H&M Retail Group', 1, 'Active'),
('ADIDAS_STORER', 'Adidas AG', 1, 'Active'),
('COLUMBIA_STORER', 'Columbia Sportswear', 1, 'Active'),
('UNILEVER_STORER', 'Unilever PLC', 1, 'Active'),
-- Regional Storers
('TEST_TH_STORER', 'Thailand Test Storer', 1, 'Active'),
('TEST_TW_STORER', 'Taiwan Test Storer', 1, 'Active'),
('TEST_IN_STORER', 'India Test Storer', 1, 'Active'),
('TEST_KR_STORER', 'Korea Test Storer', 1, 'Active');


-- ═══════════════════════════════════════════════════════════════
-- SECTION 2: STORER CONFIGURATION (TD-STORER-002)
-- ═══════════════════════════════════════════════════════════════

DELETE FROM dbo.codelkup WHERE code LIKE 'STORER%' OR code LIKE 'NIKE_%' OR code LIKE 'HM_%' OR code LIKE 'TEST_%';

-- Auto Close Configuration
INSERT INTO dbo.codelkup (listname, code, value1, value2) VALUES
('CloseASNUponFinalize', 'STORER001', 'Y', 'Auto close ASN after finalize'),
('CloseASNUponFinalize', 'STORER002', 'N', 'Manual close required'),
('CloseASNUponFinalize', 'NIKE_STORER', 'Y', 'Nike auto close'),
('CloseASNUponFinalize', 'HM_STORER', 'Y', 'H&M auto close');

-- Variance Tolerance Configuration
INSERT INTO dbo.codelkup (listname, code, value1, value2) VALUES
('ChkASNVarianceTolerance', 'STORER001', '5', 'Allow 5% variance'),
('ChkASNVarianceTolerance', 'STORER002', '10', 'Allow 10% variance'),
('ChkASNVarianceTolerance', 'NIKE_STORER', '0', 'No variance allowed');

-- Auto Putaway Release Configuration
INSERT INTO dbo.codelkup (listname, code, value1, value2) VALUES
('AutoPARelease', 'STORER001', 'Y', 'Auto release PA tasks'),
('AutoPARelease', 'STORER002', 'N', 'Manual PA release'),
('AutoPARelease', 'NIKE_STORER', 'Y', 'Nike auto PA');

-- XDock Configuration
INSERT INTO dbo.codelkup (listname, code, value1, value2) VALUES
('XDockEnabled', 'STORER001', 'Y', 'XDock enabled'),
('XDockEnabled', 'STORER002', 'N', 'XDock disabled'),
('XDockEnabled', 'NIKE_STORER', 'Y', 'Nike XDock');


-- ═══════════════════════════════════════════════════════════════
-- SECTION 3: PACK DEFINITIONS (TD-PACK-001)
-- ═══════════════════════════════════════════════════════════════

DELETE FROM dbo.pack WHERE packkey LIKE 'PACK%' OR packkey LIKE 'TEST_%';

INSERT INTO dbo.pack (packkey, descr, packuom1, casecnt) VALUES
('PACK001', 'Standard Pack - 12 units', 'EA', 12),
('PACK002', 'Bulk Pack - 48 units', 'EA', 48),
('PACK003', 'Small Pack - 6 units', 'EA', 6),
('PACK004', 'Pallet Pack - 144 units', 'EA', 144),
('PACK_NIKE', 'Nike Shoe Box', 'EA', 1),
('PACK_HM', 'H&M Garment Pack', 'EA', 10),
('PACK_ADIDAS', 'Adidas Sports Pack', 'EA', 12);


-- ═══════════════════════════════════════════════════════════════
-- SECTION 4: SKU TEST DATA (TD-SKU-001)
-- ═══════════════════════════════════════════════════════════════

DELETE FROM dbo.sku WHERE storerkey IN ('STORER001', 'STORER002', 'NIKE_STORER', 'HM_STORER', 'ADIDAS_STORER');

-- Standard Test SKUs
INSERT INTO dbo.sku (storerkey, sku, descr, packkey, unitprice, stdcube, stdgrosswgt) VALUES
('STORER001', 'SKU001', 'Test Product Alpha', 'PACK001', 100.00, 1.50, 2.00),
('STORER001', 'SKU002', 'Test Product Beta', 'PACK001', 150.00, 2.00, 3.00),
('STORER001', 'SKU003', 'Test Product Gamma', 'PACK002', 200.00, 1.00, 1.50),
('STORER001', 'SKU004', 'Test Product Delta', 'PACK001', 75.00, 0.50, 0.75),
('STORER001', 'SKU005', 'Test Product Epsilon', 'PACK003', 250.00, 3.00, 4.00),
('STORER002', 'SKU001-B', 'Storer2 Product A', 'PACK001', 120.00, 1.20, 1.80),
('STORER002', 'SKU002-B', 'Storer2 Product B', 'PACK002', 180.00, 2.50, 3.50);

-- Nike SKUs
INSERT INTO dbo.sku (storerkey, sku, descr, packkey, unitprice, stdcube, stdgrosswgt) VALUES
('NIKE_STORER', 'NIKE-AM-001', 'Nike Air Max 90', 'PACK_NIKE', 180.00, 0.50, 0.80),
('NIKE_STORER', 'NIKE-AM-002', 'Nike Air Max 95', 'PACK_NIKE', 200.00, 0.55, 0.85),
('NIKE_STORER', 'NIKE-AF-001', 'Nike Air Force 1', 'PACK_NIKE', 150.00, 0.60, 0.90),
('NIKE_STORER', 'NIKE-JD-001', 'Nike Jordan 1', 'PACK_NIKE', 250.00, 0.65, 1.00);

-- H&M SKUs
INSERT INTO dbo.sku (storerkey, sku, descr, packkey, unitprice, stdcube, stdgrosswgt) VALUES
('HM_STORER', 'HM-SHIRT-001', 'H&M Basic T-Shirt', 'PACK_HM', 19.99, 0.10, 0.20),
('HM_STORER', 'HM-JEANS-001', 'H&M Slim Fit Jeans', 'PACK_HM', 49.99, 0.30, 0.50),
('HM_STORER', 'HM-DRESS-001', 'H&M Summer Dress', 'PACK_HM', 39.99, 0.25, 0.35);

-- Adidas SKUs
INSERT INTO dbo.sku (storerkey, sku, descr, packkey, unitprice, stdcube, stdgrosswgt) VALUES
('ADIDAS_STORER', 'ADI-UB-001', 'Adidas Ultraboost', 'PACK_ADIDAS', 180.00, 0.50, 0.75),
('ADIDAS_STORER', 'ADI-SS-001', 'Adidas Superstar', 'PACK_ADIDAS', 120.00, 0.45, 0.70);


-- ═══════════════════════════════════════════════════════════════
-- SECTION 5: LOCATION TEST DATA (TD-LOC-001)
-- ═══════════════════════════════════════════════════════════════

DELETE FROM dbo.loc WHERE loc LIKE 'RECV-%' OR loc LIKE 'STAGE-%' OR loc LIKE 'STORE-%' OR loc LIKE 'XDOCK-%';

-- Receiving Locations
INSERT INTO dbo.loc (loc, loctype, locationflag, storerkey) VALUES
('RECV-001', 'RECV', 'A', 'STORER001'),
('RECV-002', 'RECV', 'A', 'STORER001'),
('RECV-003', 'RECV', 'A', 'STORER002'),
('RECV-NIKE', 'RECV', 'A', 'NIKE_STORER'),
('RECV-HM', 'RECV', 'A', 'HM_STORER');

-- Staging Locations
INSERT INTO dbo.loc (loc, loctype, locationflag, storerkey) VALUES
('STAGE-001', 'STAGE', 'A', 'STORER001'),
('STAGE-002', 'STAGE', 'A', 'STORER001'),
('STAGE-003', 'STAGE', 'A', 'STORER002');

-- Storage Locations
INSERT INTO dbo.loc (loc, loctype, locationflag, storerkey) VALUES
('STORE-A01', 'STORE', 'A', 'STORER001'),
('STORE-A02', 'STORE', 'A', 'STORER001'),
('STORE-A03', 'STORE', 'A', 'STORER001'),
('STORE-B01', 'STORE', 'A', 'STORER001'),
('STORE-B02', 'STORE', 'A', 'STORER001'),
('STORE-C01', 'STORE', 'A', 'STORER002'),
('STORE-NIKE-01', 'STORE', 'A', 'NIKE_STORER'),
('STORE-HM-01', 'STORE', 'A', 'HM_STORER');

-- XDock Locations
INSERT INTO dbo.loc (loc, loctype, locationflag, storerkey) VALUES
('XDOCK-001', 'XDOCK', 'A', 'STORER001'),
('XDOCK-002', 'XDOCK', 'A', 'STORER001'),
('XDOCK-NIKE', 'XDOCK', 'A', 'NIKE_STORER');


-- ═══════════════════════════════════════════════════════════════
-- SECTION 6: PO TEST DATA (TD-PO-001 to TD-PO-010)
-- ═══════════════════════════════════════════════════════════════

DELETE FROM dbo.orderdetail WHERE pokey LIKE 'PO-TEST-%' OR pokey LIKE 'PO-NIKE-%' OR pokey LIKE 'PO-HM-%';
DELETE FROM dbo.orders WHERE pokey LIKE 'PO-TEST-%' OR pokey LIKE 'PO-NIKE-%' OR pokey LIKE 'PO-HM-%';

-- TD-PO-001: Standard PO for Basic Tests
INSERT INTO dbo.orders (pokey, storerkey, externpokey, orderdate, expecteddate, status, addwho) VALUES
('PO-TEST-001', 'STORER001', 'EXT-PO-001', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP + INTERVAL '7 days', '0', 'TEST_USER');

INSERT INTO dbo.orderdetail (pokey, polinenumber, storerkey, sku, qtyordered, unitprice, status) VALUES
('PO-TEST-001', '00001', 'STORER001', 'SKU001', 100, 100.00, '0'),
('PO-TEST-001', '00002', 'STORER001', 'SKU002', 200, 150.00, '0'),
('PO-TEST-001', '00003', 'STORER001', 'SKU003', 50, 200.00, '0');

-- TD-PO-002: PO Already Populated (for duplicate test)
INSERT INTO dbo.orders (pokey, storerkey, externpokey, status) VALUES
('PO-TEST-002', 'STORER001', 'EXT-PO-002', '5');

INSERT INTO dbo.orderdetail (pokey, polinenumber, storerkey, sku, qtyordered, qtyreceived, status) VALUES
('PO-TEST-002', '00001', 'STORER001', 'SKU001', 100, 100, '5');

-- TD-PO-003: Closed PO (for error test)
INSERT INTO dbo.orders (pokey, storerkey, externpokey, status) VALUES
('PO-TEST-003', 'STORER001', 'EXT-PO-003', '9');

INSERT INTO dbo.orderdetail (pokey, polinenumber, storerkey, sku, qtyordered, qtyreceived, status) VALUES
('PO-TEST-003', '00001', 'STORER001', 'SKU001', 100, 100, '9');

-- TD-PO-004: PO with Line Splits (Multi-Lot)
INSERT INTO dbo.orders (pokey, storerkey, externpokey, status) VALUES
('PO-SPLIT-001', 'STORER001', 'EXT-SPLIT-001', '0');

INSERT INTO dbo.orderdetail (pokey, polinenumber, storerkey, sku, qtyordered, lottable01, lottable02, status) VALUES
('PO-SPLIT-001', '00001', 'STORER001', 'SKU001', 500, 'LOT-A', 'SUBLOT-1', '0'),
('PO-SPLIT-001', '00002', 'STORER001', 'SKU001', 300, 'LOT-A', 'SUBLOT-2', '0'),
('PO-SPLIT-001', '00003', 'STORER001', 'SKU001', 200, 'LOT-B', 'SUBLOT-1', '0');

-- TD-PO-005: Large PO (Performance Test)
INSERT INTO dbo.orders (pokey, storerkey, externpokey, status) VALUES
('PO-LARGE-001', 'STORER001', 'EXT-LARGE-001', '0');

-- Generate 100 lines for large PO
INSERT INTO dbo.orderdetail (pokey, polinenumber, storerkey, sku, qtyordered, status)
SELECT
    'PO-LARGE-001',
    LPAD(CAST(ROW_NUMBER() OVER () AS VARCHAR), 5, '0'),
    'STORER001',
    CASE (ROW_NUMBER() OVER () % 5) + 1
        WHEN 1 THEN 'SKU001'
        WHEN 2 THEN 'SKU002'
        WHEN 3 THEN 'SKU003'
        WHEN 4 THEN 'SKU004'
        ELSE 'SKU005'
    END,
    (ROW_NUMBER() OVER () % 100) + 10,
    '0'
FROM generate_series(1, 100);

-- TD-PO-006: Nike Client PO
INSERT INTO dbo.orders (pokey, storerkey, externpokey, status) VALUES
('PO-NIKE-001', 'NIKE_STORER', 'NIKE-EXT-001', '0');

INSERT INTO dbo.orderdetail (pokey, polinenumber, storerkey, sku, qtyordered, status) VALUES
('PO-NIKE-001', '00001', 'NIKE_STORER', 'NIKE-AM-001', 500, '0'),
('PO-NIKE-001', '00002', 'NIKE_STORER', 'NIKE-AF-001', 300, '0'),
('PO-NIKE-001', '00003', 'NIKE_STORER', 'NIKE-JD-001', 200, '0');

-- TD-PO-007: H&M Client PO
INSERT INTO dbo.orders (pokey, storerkey, externpokey, status) VALUES
('PO-HM-001', 'HM_STORER', 'HM-EXT-001', '0');

INSERT INTO dbo.orderdetail (pokey, polinenumber, storerkey, sku, qtyordered, status) VALUES
('PO-HM-001', '00001', 'HM_STORER', 'HM-SHIRT-001', 1000, '0'),
('PO-HM-001', '00002', 'HM_STORER', 'HM-JEANS-001', 500, '0'),
('PO-HM-001', '00003', 'HM_STORER', 'HM-DRESS-001', 300, '0');

-- TD-PO-008: PO with Invalid SKU (for error test)
INSERT INTO dbo.orders (pokey, storerkey, externpokey, status) VALUES
('PO-ERROR-001', 'STORER001', 'EXT-ERROR-001', '0');

INSERT INTO dbo.orderdetail (pokey, polinenumber, storerkey, sku, qtyordered, status) VALUES
('PO-ERROR-001', '00001', 'STORER001', 'INVALID-SKU-XXX', 100, '0');

-- TD-PO-009: Multiple POs for Batch Populate
INSERT INTO dbo.orders (pokey, storerkey, externpokey, status) VALUES
('PO-BATCH-001', 'STORER001', 'EXT-BATCH-001', '0'),
('PO-BATCH-002', 'STORER001', 'EXT-BATCH-002', '0'),
('PO-BATCH-003', 'STORER001', 'EXT-BATCH-003', '0');

INSERT INTO dbo.orderdetail (pokey, polinenumber, storerkey, sku, qtyordered, status) VALUES
('PO-BATCH-001', '00001', 'STORER001', 'SKU001', 50, '0'),
('PO-BATCH-002', '00001', 'STORER001', 'SKU002', 75, '0'),
('PO-BATCH-003', '00001', 'STORER001', 'SKU003', 100, '0');


-- ═══════════════════════════════════════════════════════════════
-- SECTION 7: RECEIPT TEST DATA (TD-RECEIPT-001 to TD-RECEIPT-005)
-- ═══════════════════════════════════════════════════════════════

DELETE FROM dbo.receiptdetail WHERE receiptkey LIKE 'RCV-TEST-%' OR receiptkey LIKE 'RCV-NIKE-%' OR receiptkey LIKE 'RCV-HM-%';
DELETE FROM dbo.receipt WHERE receiptkey LIKE 'RCV-TEST-%' OR receiptkey LIKE 'RCV-NIKE-%' OR receiptkey LIKE 'RCV-HM-%';

-- TD-RECEIPT-001: Receipt Ready for Finalization
INSERT INTO dbo.receipt (receiptkey, pokey, storerkey, status, type, receiptdate, addwho) VALUES
('RCV-TEST-001', 'PO-TEST-001', 'STORER001', '5', 'Normal', CURRENT_TIMESTAMP, 'TEST_USER');

INSERT INTO dbo.receiptdetail (receiptkey, receiptlinenumber, pokey, polinenumber, storerkey, sku, qtyexpected, qtyreceived, status, toloc, toid) VALUES
('RCV-TEST-001', '00001', 'PO-TEST-001', '00001', 'STORER001', 'SKU001', 100, 100, '5', 'RECV-001', 'LP001'),
('RCV-TEST-001', '00002', 'PO-TEST-001', '00002', 'STORER001', 'SKU002', 200, 200, '5', 'RECV-001', 'LP002'),
('RCV-TEST-001', '00003', 'PO-TEST-001', '00003', 'STORER001', 'SKU003', 50, 50, '5', 'RECV-001', 'LP003');

-- TD-RECEIPT-002: Already Finalized Receipt (for duplicate test)
INSERT INTO dbo.receipt (receiptkey, pokey, storerkey, status, type) VALUES
('RCV-FINALIZED', 'PO-TEST-002', 'STORER001', '9', 'Normal');

INSERT INTO dbo.receiptdetail (receiptkey, receiptlinenumber, storerkey, sku, qtyexpected, qtyreceived, status) VALUES
('RCV-FINALIZED', '00001', 'STORER001', 'SKU001', 100, 100, '9');

-- TD-RECEIPT-003: Receipt with Invalid Status
INSERT INTO dbo.receipt (receiptkey, pokey, storerkey, status, type) VALUES
('RCV-INVALID', 'PO-TEST-001', 'STORER001', '0', 'Normal');

INSERT INTO dbo.receiptdetail (receiptkey, receiptlinenumber, storerkey, sku, qtyexpected, qtyreceived, status) VALUES
('RCV-INVALID', '00001', 'STORER001', 'SKU001', 100, 0, '0');

-- TD-RECEIPT-004: Nike Receipt Ready for Finalization
INSERT INTO dbo.receipt (receiptkey, pokey, storerkey, status, type) VALUES
('RCV-NIKE-001', 'PO-NIKE-001', 'NIKE_STORER', '5', 'Normal');

INSERT INTO dbo.receiptdetail (receiptkey, receiptlinenumber, pokey, polinenumber, storerkey, sku, qtyexpected, qtyreceived, status) VALUES
('RCV-NIKE-001', '00001', 'PO-NIKE-001', '00001', 'NIKE_STORER', 'NIKE-AM-001', 500, 500, '5'),
('RCV-NIKE-001', '00002', 'PO-NIKE-001', '00002', 'NIKE_STORER', 'NIKE-AF-001', 300, 300, '5'),
('RCV-NIKE-001', '00003', 'PO-NIKE-001', '00003', 'NIKE_STORER', 'NIKE-JD-001', 200, 200, '5');

-- TD-RECEIPT-005: H&M Receipt Ready for Finalization
INSERT INTO dbo.receipt (receiptkey, pokey, storerkey, status, type) VALUES
('RCV-HM-001', 'PO-HM-001', 'HM_STORER', '5', 'Normal');

INSERT INTO dbo.receiptdetail (receiptkey, receiptlinenumber, pokey, polinenumber, storerkey, sku, qtyexpected, qtyreceived, status) VALUES
('RCV-HM-001', '00001', 'PO-HM-001', '00001', 'HM_STORER', 'HM-SHIRT-001', 1000, 1000, '5'),
('RCV-HM-001', '00002', 'PO-HM-001', '00002', 'HM_STORER', 'HM-JEANS-001', 500, 500, '5'),
('RCV-HM-001', '00003', 'PO-HM-001', '00003', 'HM_STORER', 'HM-DRESS-001', 300, 300, '5');


-- ═══════════════════════════════════════════════════════════════
-- SECTION 8: COUNTER INITIALIZATION
-- ═══════════════════════════════════════════════════════════════

DELETE FROM dbo.ncounter WHERE listname IN ('POKEY', 'RECEIPTKEY', 'TASKKEY', 'LPKEY');

INSERT INTO dbo.ncounter (listname, startkey, nextkey) VALUES
('POKEY', 1, 2000001),
('RECEIPTKEY', 1, 2000001),
('TASKKEY', 1, 2000001),
('LPKEY', 1, 2000001);


-- ═══════════════════════════════════════════════════════════════
-- VERIFICATION QUERIES
-- ═══════════════════════════════════════════════════════════════

-- Verify data load
SELECT 'STORER' as entity, COUNT(*) as count FROM dbo.storer
UNION ALL
SELECT 'SKU', COUNT(*) FROM dbo.sku
UNION ALL
SELECT 'PACK', COUNT(*) FROM dbo.pack
UNION ALL
SELECT 'LOC', COUNT(*) FROM dbo.loc
UNION ALL
SELECT 'ORDERS', COUNT(*) FROM dbo.orders
UNION ALL
SELECT 'ORDERDETAIL', COUNT(*) FROM dbo.orderdetail
UNION ALL
SELECT 'RECEIPT', COUNT(*) FROM dbo.receipt
UNION ALL
SELECT 'RECEIPTDETAIL', COUNT(*) FROM dbo.receiptdetail
UNION ALL
SELECT 'CODELKUP', COUNT(*) FROM dbo.codelkup;

-- ═══════════════════════════════════════════════════════════════
-- END OF MASTER TEST DATA SETUP
-- ═══════════════════════════════════════════════════════════════
