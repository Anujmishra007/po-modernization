-- =============================================================================
-- TD-ORDER.sql
-- Outbound Order Test Data (Sales Orders) for PO Modernization E2E Testing
-- =============================================================================
-- Used for: Cross-dock allocation (F4), XDock testing
-- Covers: F4-TC01 to F4-TC20 (XDock allocation scenarios)
-- =============================================================================

SET search_path TO dbo, public;

-- =============================================================================
-- SECTION 1: SALES ORDERS (For XDock allocation)
-- =============================================================================

DELETE FROM orders WHERE orderkey LIKE 'SO-%' OR orderkey LIKE 'XDOCK-SO-%';
DELETE FROM orderdetail WHERE orderkey LIKE 'SO-%' OR orderkey LIKE 'XDOCK-SO-%';

-- Standard sales orders awaiting allocation
INSERT INTO orders (orderkey, storerkey, externorderkey, ordertype, status, facility, orderdate, expectedshipdate, priority, adddate, addwho) VALUES
-- Nike orders (for XDock from PO receipts)
('SO-NIKE-001', 'NIKE_KR', 'SO-EXT-NIKE-001', 'STANDARD', '0', 'KR01', CURRENT_TIMESTAMP - INTERVAL '1 day', CURRENT_TIMESTAMP + INTERVAL '2 days', 5, CURRENT_TIMESTAMP - INTERVAL '1 day', 'SYSTEM'),
('SO-NIKE-002', 'NIKE_KR', 'SO-EXT-NIKE-002', 'STANDARD', '0', 'KR01', CURRENT_TIMESTAMP - INTERVAL '1 day', CURRENT_TIMESTAMP + INTERVAL '3 days', 3, CURRENT_TIMESTAMP - INTERVAL '1 day', 'SYSTEM'),
-- H&M orders (fast-fashion, high priority)
('SO-HM-001', 'HM_KR', 'SO-EXT-HM-001', 'STANDARD', '0', 'KR02', CURRENT_TIMESTAMP - INTERVAL '2 hours', CURRENT_TIMESTAMP + INTERVAL '1 day', 1, CURRENT_TIMESTAMP - INTERVAL '2 hours', 'SYSTEM'),
('SO-HM-002', 'HM_KR', 'SO-EXT-HM-002', 'STANDARD', '0', 'KR02', CURRENT_TIMESTAMP - INTERVAL '1 hour', CURRENT_TIMESTAMP + INTERVAL '1 day', 1, CURRENT_TIMESTAMP - INTERVAL '1 hour', 'SYSTEM'),
-- Adidas orders
('SO-ADI-001', 'ADIDAS_IN', 'SO-EXT-ADI-001', 'STANDARD', '0', 'IN01', CURRENT_TIMESTAMP - INTERVAL '3 days', CURRENT_TIMESTAMP + INTERVAL '5 days', 3, CURRENT_TIMESTAMP - INTERVAL '3 days', 'SYSTEM'),

-- XDock-specific orders (linked to inbound POs)
('XDOCK-SO-001', 'NIKE_KR', 'XDOCK-EXT-001', 'XDOCK', '0', 'KR01', CURRENT_TIMESTAMP - INTERVAL '1 hour', CURRENT_TIMESTAMP + INTERVAL '1 day', 1, CURRENT_TIMESTAMP - INTERVAL '1 hour', 'SYSTEM'),
('XDOCK-SO-002', 'HM_KR', 'XDOCK-EXT-002', 'XDOCK', '0', 'KR02', CURRENT_TIMESTAMP - INTERVAL '30 minutes', CURRENT_TIMESTAMP + INTERVAL '1 day', 1, CURRENT_TIMESTAMP - INTERVAL '30 minutes', 'SYSTEM');

-- Order details
INSERT INTO orderdetail (orderkey, orderlinenumber, storerkey, sku, qtyordered, qtyallocated, qtypicked, qtyshipped, unitprice, status, adddate, addwho) VALUES
-- SO-NIKE-001 (Air Max 90)
('SO-NIKE-001', '00001', 'NIKE_KR', 'NK-AIRMAX90-BLK', 100, 0, 0, 0, 89.99, '0', CURRENT_TIMESTAMP - INTERVAL '1 day', 'SYSTEM'),
('SO-NIKE-001', '00002', 'NIKE_KR', 'NK-AIRMAX90-WHT', 50, 0, 0, 0, 89.99, '0', CURRENT_TIMESTAMP - INTERVAL '1 day', 'SYSTEM'),
-- SO-NIKE-002 (Air Force 1)
('SO-NIKE-002', '00001', 'NIKE_KR', 'NK-AF1-BLK', 75, 0, 0, 0, 109.99, '0', CURRENT_TIMESTAMP - INTERVAL '1 day', 'SYSTEM'),
-- SO-HM-001 (Basic Tees - fast fashion)
('SO-HM-001', '00001', 'HM_KR', 'HM-BASIC-TEE-S', 500, 0, 0, 0, 4.99, '0', CURRENT_TIMESTAMP - INTERVAL '2 hours', 'SYSTEM'),
('SO-HM-001', '00002', 'HM_KR', 'HM-BASIC-TEE-M', 800, 0, 0, 0, 4.99, '0', CURRENT_TIMESTAMP - INTERVAL '2 hours', 'SYSTEM'),
('SO-HM-001', '00003', 'HM_KR', 'HM-BASIC-TEE-L', 500, 0, 0, 0, 4.99, '0', CURRENT_TIMESTAMP - INTERVAL '2 hours', 'SYSTEM'),
-- SO-HM-002 (Jeans)
('SO-HM-002', '00001', 'HM_KR', 'HM-SLIM-JEANS-32', 100, 0, 0, 0, 29.99, '0', CURRENT_TIMESTAMP - INTERVAL '1 hour', 'SYSTEM'),
('SO-HM-002', '00002', 'HM_KR', 'HM-SLIM-JEANS-34', 100, 0, 0, 0, 29.99, '0', CURRENT_TIMESTAMP - INTERVAL '1 hour', 'SYSTEM'),
-- SO-ADI-001
('SO-ADI-001', '00001', 'ADIDAS_IN', 'ADI-ULTRABOOST-BLK', 50, 0, 0, 0, 149.99, '0', CURRENT_TIMESTAMP - INTERVAL '3 days', 'SYSTEM'),
-- XDOCK-SO-001 (Linked to Nike inbound)
('XDOCK-SO-001', '00001', 'NIKE_KR', 'NK-AIRMAX90-BLK', 100, 0, 0, 0, 89.99, '0', CURRENT_TIMESTAMP - INTERVAL '1 hour', 'SYSTEM'),
-- XDOCK-SO-002 (Linked to H&M inbound)
('XDOCK-SO-002', '00001', 'HM_KR', 'HM-BASIC-TEE-M', 500, 0, 0, 0, 4.99, '0', CURRENT_TIMESTAMP - INTERVAL '30 minutes', 'SYSTEM');


-- =============================================================================
-- SECTION 2: ALLOCATED ORDERS (For testing already allocated)
-- =============================================================================

INSERT INTO orders (orderkey, storerkey, externorderkey, ordertype, status, facility, orderdate, expectedshipdate, priority, adddate, addwho) VALUES
('SO-ALLOC-001', 'NIKE_KR', 'SO-EXT-ALLOC-001', 'STANDARD', '5', 'KR01', CURRENT_TIMESTAMP - INTERVAL '2 days', CURRENT_TIMESTAMP + INTERVAL '1 day', 3, CURRENT_TIMESTAMP - INTERVAL '2 days', 'SYSTEM');

INSERT INTO orderdetail (orderkey, orderlinenumber, storerkey, sku, qtyordered, qtyallocated, qtypicked, qtyshipped, unitprice, status, adddate, addwho) VALUES
('SO-ALLOC-001', '00001', 'NIKE_KR', 'NK-AIRMAX90-BLK', 50, 50, 0, 0, 89.99, '5', CURRENT_TIMESTAMP - INTERVAL '2 days', 'SYSTEM');


-- =============================================================================
-- SECTION 3: XDOCK LINKAGE (PO to SO mapping)
-- =============================================================================

DELETE FROM xdocklinkage WHERE linkkey LIKE 'XDLINK-%';

INSERT INTO xdocklinkage (linkkey, pokey, polinenumber, orderkey, orderlinenumber, storerkey, sku, linkqty, status, adddate, addwho) VALUES
-- Nike XDock links
('XDLINK-001', 'PO-HAPPY-001', '00001', 'XDOCK-SO-001', '00001', 'NIKE_KR', 'NK-AIRMAX90-BLK', 100, '0', CURRENT_TIMESTAMP - INTERVAL '1 hour', 'SYSTEM'),
-- H&M XDock links
('XDLINK-002', 'PO-HAPPY-002', '00001', 'XDOCK-SO-002', '00001', 'HM_KR', 'HM-BASIC-TEE-M', 500, '0', CURRENT_TIMESTAMP - INTERVAL '30 minutes', 'SYSTEM');


-- =============================================================================
-- SECTION 4: ALLOCATION RECORDS
-- =============================================================================

DELETE FROM allocation WHERE allocationkey LIKE 'ALLOC-%';

INSERT INTO allocation (allocationkey, orderkey, orderlinenumber, storerkey, sku, loc, id, qty, status, allocateddate, allocatedby, adddate, addwho) VALUES
-- Allocations for SO-ALLOC-001
('ALLOC-001', 'SO-ALLOC-001', '00001', 'NIKE_KR', 'NK-AIRMAX90-BLK', 'KR01-STOR-A01', 'INV-NIKE-001', 50, '1', CURRENT_TIMESTAMP - INTERVAL '1 day', 'SYSTEM', CURRENT_TIMESTAMP - INTERVAL '1 day', 'SYSTEM');


-- =============================================================================
-- SECTION 5: ORDER ERROR SCENARIOS
-- =============================================================================

INSERT INTO orders (orderkey, storerkey, externorderkey, ordertype, status, facility, orderdate, expectedshipdate, priority, adddate, addwho) VALUES
-- Order not found test
-- (No insert - test for non-existent order)
-- Insufficient inventory order
('SO-ERR-INSUFF', 'ADIDAS_IN', 'SO-ERR-001', 'STANDARD', '0', 'IN01', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP + INTERVAL '1 day', 5, CURRENT_TIMESTAMP, 'SYSTEM'),
-- Already allocated order
('SO-ERR-ALLOC', 'NIKE_KR', 'SO-ERR-002', 'STANDARD', '9', 'KR01', CURRENT_TIMESTAMP - INTERVAL '5 days', CURRENT_TIMESTAMP - INTERVAL '2 days', 3, CURRENT_TIMESTAMP - INTERVAL '5 days', 'SYSTEM');

INSERT INTO orderdetail (orderkey, orderlinenumber, storerkey, sku, qtyordered, qtyallocated, qtypicked, qtyshipped, unitprice, status, adddate, addwho) VALUES
-- Insufficient inventory (requesting more than available)
('SO-ERR-INSUFF', '00001', 'ADIDAS_IN', 'ADI-ULTRABOOST-BLK', 5000, 0, 0, 0, 149.99, '0', CURRENT_TIMESTAMP, 'SYSTEM'),
-- Already shipped
('SO-ERR-ALLOC', '00001', 'NIKE_KR', 'NK-AIRMAX90-BLK', 25, 25, 25, 25, 89.99, '9', CURRENT_TIMESTAMP - INTERVAL '5 days', 'SYSTEM');


-- =============================================================================
-- SECTION 6: SHIP-TO ADDRESSES (Customer destinations)
-- =============================================================================

DELETE FROM shiptoaddress WHERE addresskey LIKE 'SHIPTO-%';

INSERT INTO shiptoaddress (addresskey, orderkey, company, address1, address2, city, state, zip, country, contactname, phone, email) VALUES
-- Nike Korea retail
('SHIPTO-NIKE-001', 'SO-NIKE-001', 'Nike Seoul Store', '123 Gangnam Blvd', 'Floor 2', 'Seoul', 'Seoul', '06123', 'KR', 'Kim Manager', '+82-2-1234-5678', 'seoul.store@nike.com'),
('SHIPTO-NIKE-002', 'SO-NIKE-002', 'Nike Busan Store', '456 Haeundae Road', NULL, 'Busan', 'Busan', '48099', 'KR', 'Park Manager', '+82-51-987-6543', 'busan.store@nike.com'),
-- H&M Korea retail (fast fashion distribution)
('SHIPTO-HM-001', 'SO-HM-001', 'H&M Myeongdong', '789 Myeongdong St', 'Main Building', 'Seoul', 'Seoul', '04538', 'KR', 'Lee Store Manager', '+82-2-2222-3333', 'myeongdong@hm.com'),
('SHIPTO-HM-002', 'SO-HM-002', 'H&M Hongdae', '321 Hongdae Ave', NULL, 'Seoul', 'Seoul', '04065', 'KR', 'Choi Manager', '+82-2-4444-5555', 'hongdae@hm.com'),
-- XDock destinations
('SHIPTO-XD-001', 'XDOCK-SO-001', 'Nike Distribution Hub', 'Nike Logistics Center', 'Building A', 'Incheon', 'Incheon', '22301', 'KR', 'Logistics Team', '+82-32-8888-9999', 'logistics@nike.com'),
('SHIPTO-XD-002', 'XDOCK-SO-002', 'H&M Regional DC', 'H&M Logistics Park', NULL, 'Pyeongtaek', 'Gyeonggi', '17802', 'KR', 'DC Operations', '+82-31-7777-8888', 'dc.ops@hm.com');


-- =============================================================================
-- VERIFICATION
-- =============================================================================

SELECT 'ORDERS' as entity, COUNT(*) as count FROM orders WHERE orderkey LIKE 'SO-%' OR orderkey LIKE 'XDOCK-SO-%'
UNION ALL
SELECT 'ORDERDETAIL', COUNT(*) FROM orderdetail WHERE orderkey LIKE 'SO-%' OR orderkey LIKE 'XDOCK-SO-%'
UNION ALL
SELECT 'XDOCKLINKAGE', COUNT(*) FROM xdocklinkage WHERE linkkey LIKE 'XDLINK-%'
UNION ALL
SELECT 'ALLOCATION', COUNT(*) FROM allocation WHERE allocationkey LIKE 'ALLOC-%'
UNION ALL
SELECT 'SHIPTOADDRESS', COUNT(*) FROM shiptoaddress WHERE addresskey LIKE 'SHIPTO-%';

-- =============================================================================
-- END OF ORDER TEST DATA
-- =============================================================================
