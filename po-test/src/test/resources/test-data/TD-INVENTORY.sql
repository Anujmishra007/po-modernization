-- =============================================================================
-- TD-INVENTORY.sql
-- Inventory Test Data (LOTxLOCxID) for PO Modernization E2E Testing
-- =============================================================================
-- Used for: Receipt finalization (inventory posting), XDock allocation,
--           Trigger testing (tr_lotxlocxid_insert)
-- Covers: F3-TC06, F3-TC26, F3-TC29, F4-TC01, F4-TC07
-- =============================================================================

SET search_path TO dbo, public;

-- =============================================================================
-- SECTION 1: EXISTING INVENTORY (For allocation/XDock testing)
-- =============================================================================

DELETE FROM lotxlocxid WHERE id LIKE 'INV-%' OR id LIKE 'XDOCK-%' OR id LIKE 'EXIST-%';

INSERT INTO lotxlocxid (id, storerkey, sku, loc, lot, qty, qtyallocated, qtyonhold, qtypicked, qtyintransit, status, lottable01, lottable02, lottable03, lottable04, lottable05, adddate, addwho, editdate, editwho) VALUES
-- Nike existing inventory (for XDock allocation)
('INV-NIKE-001', 'NIKE_KR', 'NK-AIRMAX90-BLK', 'KR01-STOR-A01', 'LOT-2024-001', 500, 0, 0, 0, 0, '1', 'STYLE-AM90-2024', 'COLOR-001-BLK', 'SEASON-S24', NULL, NULL, CURRENT_TIMESTAMP - INTERVAL '7 days', 'SYSTEM', CURRENT_TIMESTAMP, 'SYSTEM'),
('INV-NIKE-002', 'NIKE_KR', 'NK-AIRMAX90-WHT', 'KR01-STOR-A02', 'LOT-2024-002', 300, 50, 0, 0, 0, '1', 'STYLE-AM90-2024', 'COLOR-002-WHT', 'SEASON-S24', NULL, NULL, CURRENT_TIMESTAMP - INTERVAL '7 days', 'SYSTEM', CURRENT_TIMESTAMP, 'SYSTEM'),
('INV-NIKE-003', 'NIKE_KR', 'NK-AF1-BLK', 'KR01-STOR-B01', 'LOT-2024-003', 200, 0, 0, 0, 0, '1', 'STYLE-AF1-2024', 'COLOR-001-BLK', 'SEASON-S24', NULL, NULL, CURRENT_TIMESTAMP - INTERVAL '5 days', 'SYSTEM', CURRENT_TIMESTAMP, 'SYSTEM'),

-- H&M existing inventory
('INV-HM-001', 'HM_KR', 'HM-BASIC-TEE-S', 'KR02-STOR-A01', 'LOT-HM-001', 2000, 500, 0, 0, 0, '1', NULL, NULL, NULL, NULL, NULL, CURRENT_TIMESTAMP - INTERVAL '3 days', 'SYSTEM', CURRENT_TIMESTAMP, 'SYSTEM'),
('INV-HM-002', 'HM_KR', 'HM-BASIC-TEE-M', 'KR02-STOR-A02', 'LOT-HM-002', 3000, 800, 0, 0, 0, '1', NULL, NULL, NULL, NULL, NULL, CURRENT_TIMESTAMP - INTERVAL '3 days', 'SYSTEM', CURRENT_TIMESTAMP, 'SYSTEM'),
('INV-HM-003', 'HM_KR', 'HM-SLIM-JEANS-32', 'KR02-STOR-B01', 'LOT-HM-003', 500, 100, 0, 0, 0, '1', NULL, NULL, NULL, NULL, NULL, CURRENT_TIMESTAMP - INTERVAL '2 days', 'SYSTEM', CURRENT_TIMESTAMP, 'SYSTEM'),

-- Adidas existing inventory (India)
('INV-ADI-001', 'ADIDAS_IN', 'ADI-ULTRABOOST-BLK', 'IN01-STOR-A01', 'LOT-ADI-001', 150, 0, 0, 0, 0, '1', 'UB-2024', NULL, NULL, NULL, NULL, CURRENT_TIMESTAMP - INTERVAL '10 days', 'SYSTEM', CURRENT_TIMESTAMP, 'SYSTEM'),
('INV-ADI-002', 'ADIDAS_IN', 'ADI-SUPERSTAR-WHT', 'IN01-STOR-A02', 'LOT-ADI-002', 200, 25, 0, 0, 0, '1', 'SS-2024', NULL, NULL, NULL, NULL, CURRENT_TIMESTAMP - INTERVAL '10 days', 'SYSTEM', CURRENT_TIMESTAMP, 'SYSTEM'),

-- Unilever existing inventory (Singapore)
('INV-UNI-001', 'UNI_SG', 'UNI-DOVE-SOAP-100', 'SG01-STOR-A01', 'LOT-UNI-001', 5000, 1000, 0, 0, 0, '1', '2024-06-15', 'BD001', NULL, NULL, NULL, CURRENT_TIMESTAMP - INTERVAL '14 days', 'SYSTEM', CURRENT_TIMESTAMP, 'SYSTEM');


-- =============================================================================
-- SECTION 2: INVENTORY ON HOLD (For hold release testing)
-- =============================================================================

INSERT INTO lotxlocxid (id, storerkey, sku, loc, lot, qty, qtyallocated, qtyonhold, qtypicked, qtyintransit, status, adddate, addwho) VALUES
-- Inventory with quality hold
('INV-HOLD-001', 'NIKE_KR', 'NK-AIRMAX90-BLK', 'KR01-HOLD-01', 'LOT-HOLD-001', 100, 0, 100, 0, 0, '2', CURRENT_TIMESTAMP - INTERVAL '5 days', 'SYSTEM'),
-- Inventory with damage hold
('INV-HOLD-002', 'HM_KR', 'HM-BASIC-TEE-M', 'KR02-HOLD-01', 'LOT-HOLD-002', 50, 0, 50, 0, 0, '3', CURRENT_TIMESTAMP - INTERVAL '3 days', 'SYSTEM'),
-- Inventory with customs hold
('INV-HOLD-003', 'ADIDAS_IN', 'ADI-ULTRABOOST-BLK', 'IN01-HOLD-01', 'LOT-HOLD-003', 75, 0, 75, 0, 0, '4', CURRENT_TIMESTAMP - INTERVAL '7 days', 'SYSTEM');


-- =============================================================================
-- SECTION 3: XDOCK INVENTORY (In cross-dock staging)
-- =============================================================================

INSERT INTO lotxlocxid (id, storerkey, sku, loc, lot, qty, qtyallocated, qtyonhold, qtypicked, qtyintransit, status, adddate, addwho) VALUES
-- XDock inventory awaiting allocation
('XDOCK-001', 'NIKE_KR', 'NK-AIRMAX90-BLK', 'KR01-XDOCK-01', 'LOT-XD-001', 100, 0, 0, 0, 0, '1', CURRENT_TIMESTAMP - INTERVAL '1 hour', 'SYSTEM'),
('XDOCK-002', 'NIKE_KR', 'NK-AF1-BLK', 'KR01-XDOCK-01', 'LOT-XD-002', 50, 0, 0, 0, 0, '1', CURRENT_TIMESTAMP - INTERVAL '1 hour', 'SYSTEM'),
-- XDock inventory already allocated
('XDOCK-003', 'HM_KR', 'HM-BASIC-TEE-M', 'KR02-XDOCK-01', 'LOT-XD-003', 500, 500, 0, 0, 0, '1', CURRENT_TIMESTAMP - INTERVAL '2 hours', 'SYSTEM');


-- =============================================================================
-- SECTION 4: RECEIVING DOCK INVENTORY (Just received, not yet put away)
-- =============================================================================

INSERT INTO lotxlocxid (id, storerkey, sku, loc, lot, qty, qtyallocated, qtyonhold, qtypicked, qtyintransit, status, adddate, addwho) VALUES
-- Inventory at receiving dock
('INV-RECV-001', 'NIKE_KR', 'NK-DRIFIT-M', 'KR01-RECV-01', 'LOT-RECV-001', 200, 0, 0, 0, 0, '1', CURRENT_TIMESTAMP - INTERVAL '30 minutes', 'SYSTEM'),
('INV-RECV-002', 'HM_KR', 'HM-SLIM-JEANS-34', 'KR02-RECV-01', 'LOT-RECV-002', 150, 0, 0, 0, 0, '1', CURRENT_TIMESTAMP - INTERVAL '45 minutes', 'SYSTEM');


-- =============================================================================
-- SECTION 5: INVENTORY FOR ERROR SCENARIOS
-- =============================================================================

INSERT INTO lotxlocxid (id, storerkey, sku, loc, lot, qty, qtyallocated, qtyonhold, qtypicked, qtyintransit, status, adddate, addwho) VALUES
-- Location at full capacity (for F3-TC29: Location full)
('INV-FULL-001', 'NIKE_KR', 'NK-AIRMAX90-BLK', 'KR01-STOR-FULL', 'LOT-FULL-001', 999, 0, 0, 0, 0, '1', CURRENT_TIMESTAMP - INTERVAL '1 day', 'SYSTEM'),
-- Insufficient inventory for allocation (for F4-TC07)
('INV-LOW-001', 'ADIDAS_IN', 'ADI-ULTRABOOST-BLK', 'IN01-STOR-LOW', 'LOT-LOW-001', 10, 5, 0, 0, 0, '1', CURRENT_TIMESTAMP - INTERVAL '2 days', 'SYSTEM');


-- =============================================================================
-- SECTION 6: INVENTORY HOLDS
-- =============================================================================

DELETE FROM inventoryhold WHERE holdkey LIKE 'HOLD-%';

INSERT INTO inventoryhold (holdkey, id, storerkey, sku, holdcode, holdqty, holddate, holdby, reason, status) VALUES
-- Quality holds
('HOLD-QA-001', 'INV-HOLD-001', 'NIKE_KR', 'NK-AIRMAX90-BLK', 'QA', 100, CURRENT_TIMESTAMP - INTERVAL '5 days', 'QA_INSPECTOR', 'Pending quality inspection', '1'),
-- Damage holds
('HOLD-DMG-001', 'INV-HOLD-002', 'HM_KR', 'HM-BASIC-TEE-M', 'DMG', 50, CURRENT_TIMESTAMP - INTERVAL '3 days', 'RECV_OPERATOR', 'Box damage during unloading', '1'),
-- Customs holds
('HOLD-CUS-001', 'INV-HOLD-003', 'ADIDAS_IN', 'ADI-ULTRABOOST-BLK', 'CUSTOMS', 75, CURRENT_TIMESTAMP - INTERVAL '7 days', 'CUSTOMS_TEAM', 'Awaiting customs clearance', '1');


-- =============================================================================
-- SECTION 7: LOT MASTER (Lot definitions)
-- =============================================================================

DELETE FROM lot WHERE lot LIKE 'LOT-%';

INSERT INTO lot (lot, storerkey, sku, status, manufacturedate, expirationdate, lottable01, lottable02, lottable03, adddate, addwho) VALUES
-- Nike lots
('LOT-2024-001', 'NIKE_KR', 'NK-AIRMAX90-BLK', '1', '2024-01-15', NULL, 'STYLE-AM90-2024', 'COLOR-001-BLK', 'SEASON-S24', CURRENT_TIMESTAMP - INTERVAL '30 days', 'SYSTEM'),
('LOT-2024-002', 'NIKE_KR', 'NK-AIRMAX90-WHT', '1', '2024-01-15', NULL, 'STYLE-AM90-2024', 'COLOR-002-WHT', 'SEASON-S24', CURRENT_TIMESTAMP - INTERVAL '30 days', 'SYSTEM'),
('LOT-2024-003', 'NIKE_KR', 'NK-AF1-BLK', '1', '2024-02-01', NULL, 'STYLE-AF1-2024', 'COLOR-001-BLK', 'SEASON-S24', CURRENT_TIMESTAMP - INTERVAL '25 days', 'SYSTEM'),
-- H&M lots (no expiration for apparel)
('LOT-HM-001', 'HM_KR', 'HM-BASIC-TEE-S', '1', '2024-03-01', NULL, NULL, NULL, NULL, CURRENT_TIMESTAMP - INTERVAL '20 days', 'SYSTEM'),
('LOT-HM-002', 'HM_KR', 'HM-BASIC-TEE-M', '1', '2024-03-01', NULL, NULL, NULL, NULL, CURRENT_TIMESTAMP - INTERVAL '20 days', 'SYSTEM'),
-- Unilever lots (with expiration)
('LOT-UNI-001', 'UNI_SG', 'UNI-DOVE-SOAP-100', '1', '2024-01-10', '2026-01-10', '2024-06-15', 'BD001', NULL, CURRENT_TIMESTAMP - INTERVAL '14 days', 'SYSTEM');


-- =============================================================================
-- SECTION 8: INVENTORY TRANSACTIONS (Audit trail)
-- =============================================================================

DELETE FROM inventorytransaction WHERE transactionkey LIKE 'INVTX-%';

INSERT INTO inventorytransaction (transactionkey, id, storerkey, sku, loc, lot, transactiontype, qty, fromqty, toqty, transactiondate, transactionby, reference, adddate) VALUES
-- Receipt posting transactions
('INVTX-001', 'INV-NIKE-001', 'NIKE_KR', 'NK-AIRMAX90-BLK', 'KR01-STOR-A01', 'LOT-2024-001', 'RECEIPT', 500, 0, 500, CURRENT_TIMESTAMP - INTERVAL '7 days', 'SYSTEM', 'RCV-001', CURRENT_TIMESTAMP - INTERVAL '7 days'),
-- Allocation transactions
('INVTX-002', 'INV-NIKE-002', 'NIKE_KR', 'NK-AIRMAX90-WHT', 'KR01-STOR-A02', 'LOT-2024-002', 'ALLOCATE', 50, 0, 50, CURRENT_TIMESTAMP - INTERVAL '2 days', 'SYSTEM', 'ORD-001', CURRENT_TIMESTAMP - INTERVAL '2 days'),
-- Pick transactions
('INVTX-003', 'INV-HM-001', 'HM_KR', 'HM-BASIC-TEE-S', 'KR02-STOR-A01', 'LOT-HM-001', 'PICK', 100, 2000, 1900, CURRENT_TIMESTAMP - INTERVAL '1 day', 'PICKER001', 'PICK-001', CURRENT_TIMESTAMP - INTERVAL '1 day');


-- =============================================================================
-- VERIFICATION
-- =============================================================================

SELECT 'LOTXLOCXID' as entity, COUNT(*) as count FROM lotxlocxid WHERE id LIKE 'INV-%' OR id LIKE 'XDOCK-%' OR id LIKE 'EXIST-%'
UNION ALL
SELECT 'INVENTORYHOLD', COUNT(*) FROM inventoryhold WHERE holdkey LIKE 'HOLD-%'
UNION ALL
SELECT 'LOT', COUNT(*) FROM lot WHERE lot LIKE 'LOT-%'
UNION ALL
SELECT 'INVENTORYTRANSACTION', COUNT(*) FROM inventorytransaction WHERE transactionkey LIKE 'INVTX-%';

-- =============================================================================
-- END OF INVENTORY TEST DATA
-- =============================================================================
