-- =============================================================================
-- TD-TRIGGER.sql
-- Database Trigger Test Data for PO Modernization E2E Testing
-- =============================================================================
-- Entry Point: DB Triggers (25 Test Cases)
-- Covers: F1-TC18, F1-TC19, F2-TC30, F3-TC06
-- Triggers: tr_orders_insert, tr_orders_update, tr_receipt_insert,
--           tr_receipt_update, tr_lotxlocxid_insert
-- =============================================================================

SET search_path TO dbo, public;

-- =============================================================================
-- SECTION 1: TRIGGER AUDIT CONFIGURATION
-- =============================================================================

DELETE FROM triggerconfig WHERE triggerkey LIKE 'TRG-%';

INSERT INTO triggerconfig (triggerkey, triggername, tablename, operation, enabled, auditlog, cascadeaction, priority, adddate, addwho) VALUES
-- PO Triggers
('TRG-PO-INS', 'tr_orders_insert', 'orders', 'INSERT', 'Y', 'Y', 'VALIDATE_AUDIT', 1, CURRENT_TIMESTAMP, 'SYSTEM'),
('TRG-PO-UPD', 'tr_orders_update', 'orders', 'UPDATE', 'Y', 'Y', 'STATUS_TRACK', 2, CURRENT_TIMESTAMP, 'SYSTEM'),
('TRG-POD-INS', 'tr_orderdetail_insert', 'orderdetail', 'INSERT', 'Y', 'Y', 'UPDATE_HEADER', 3, CURRENT_TIMESTAMP, 'SYSTEM'),
-- Receipt Triggers
('TRG-RCV-INS', 'tr_receipt_insert', 'receipt', 'INSERT', 'Y', 'Y', 'VALIDATE_AUTO_POP', 1, CURRENT_TIMESTAMP, 'SYSTEM'),
('TRG-RCV-UPD', 'tr_receipt_update', 'receipt', 'UPDATE', 'Y', 'Y', 'STATUS_CASCADE', 2, CURRENT_TIMESTAMP, 'SYSTEM'),
('TRG-RCVD-INS', 'tr_receiptdetail_insert', 'receiptdetail', 'INSERT', 'Y', 'Y', 'UPDATE_HEADER', 3, CURRENT_TIMESTAMP, 'SYSTEM'),
-- Inventory Triggers
('TRG-INV-INS', 'tr_lotxlocxid_insert', 'lotxlocxid', 'INSERT', 'Y', 'Y', 'UPDATE_LOCATION', 1, CURRENT_TIMESTAMP, 'SYSTEM'),
('TRG-INV-UPD', 'tr_lotxlocxid_update', 'lotxlocxid', 'UPDATE', 'Y', 'Y', 'ADJUST_QTY', 2, CURRENT_TIMESTAMP, 'SYSTEM'),
-- Disabled trigger for testing
('TRG-DISABLED', 'tr_test_disabled', 'test_table', 'INSERT', 'N', 'Y', 'NONE', 99, CURRENT_TIMESTAMP, 'SYSTEM');


-- =============================================================================
-- SECTION 2: TRIGGER-SPECIFIC POs (For tr_orders_* testing)
-- =============================================================================

DELETE FROM po WHERE pokey LIKE 'TRG-PO-%';
DELETE FROM podetail WHERE pokey LIKE 'TRG-PO-%';

-- PO for insert trigger testing
INSERT INTO po (pokey, storerkey, externpokey, potype, status, facility, expecteddate, adddate, addwho) VALUES
('TRG-PO-001', 'NIKE_KR', 'TRG-EXT-001', 'STANDARD', '0', 'KR01', CURRENT_TIMESTAMP + INTERVAL '3 days', CURRENT_TIMESTAMP, 'SYSTEM'),
-- PO for update trigger testing (status changes)
('TRG-PO-002', 'HM_KR', 'TRG-EXT-002', 'STANDARD', '0', 'KR02', CURRENT_TIMESTAMP + INTERVAL '2 days', CURRENT_TIMESTAMP, 'SYSTEM'),
-- PO for duplicate detection trigger
('TRG-PO-DUP', 'NIKE_KR', 'TRG-EXT-DUP', 'STANDARD', '5', 'KR01', CURRENT_TIMESTAMP + INTERVAL '3 days', CURRENT_TIMESTAMP, 'SYSTEM'),
-- PO for cascade update trigger
('TRG-PO-CASCADE', 'ADIDAS_IN', 'TRG-EXT-CASC', 'STANDARD', '0', 'IN01', CURRENT_TIMESTAMP + INTERVAL '5 days', CURRENT_TIMESTAMP, 'SYSTEM');

INSERT INTO podetail (pokey, polinenumber, storerkey, sku, qtyordered, qtyreceived, unitprice, status, adddate, addwho) VALUES
('TRG-PO-001', '00001', 'NIKE_KR', 'NK-AIRMAX90-BLK', 100, 0, 89.99, '0', CURRENT_TIMESTAMP, 'SYSTEM'),
('TRG-PO-002', '00001', 'HM_KR', 'HM-BASIC-TEE-M', 200, 0, 4.99, '0', CURRENT_TIMESTAMP, 'SYSTEM'),
('TRG-PO-DUP', '00001', 'NIKE_KR', 'NK-AIRMAX90-BLK', 100, 100, 89.99, '5', CURRENT_TIMESTAMP, 'SYSTEM'),
('TRG-PO-CASCADE', '00001', 'ADIDAS_IN', 'ADI-ULTRABOOST-BLK', 50, 0, 149.99, '0', CURRENT_TIMESTAMP, 'SYSTEM'),
('TRG-PO-CASCADE', '00002', 'ADIDAS_IN', 'ADI-SUPERSTAR-WHT', 50, 0, 119.99, '0', CURRENT_TIMESTAMP, 'SYSTEM');


-- =============================================================================
-- SECTION 3: TRIGGER-SPECIFIC RECEIPTS (For tr_receipt_* testing)
-- =============================================================================

DELETE FROM receipt WHERE receiptkey LIKE 'TRG-RCV-%';
DELETE FROM receiptdetail WHERE receiptkey LIKE 'TRG-RCV-%';

-- Receipt for insert trigger testing
INSERT INTO receipt (receiptkey, pokey, storerkey, status, type, facility, receiptdate, adddate, addwho) VALUES
('TRG-RCV-001', 'TRG-PO-001', 'NIKE_KR', '0', 'Normal', 'KR01', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 'SYSTEM'),
-- Receipt for status update trigger (will be finalized)
('TRG-RCV-002', 'TRG-PO-002', 'HM_KR', '5', 'Normal', 'KR02', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 'SYSTEM'),
-- Receipt for cascade update
('TRG-RCV-CASCADE', 'TRG-PO-CASCADE', 'ADIDAS_IN', '5', 'Normal', 'IN01', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 'SYSTEM');

INSERT INTO receiptdetail (receiptdetailkey, receiptkey, receiptlinenumber, pokey, polinenumber, storerkey, sku, qtyexpected, qtyreceived, status, toloc, toid, adddate, addwho) VALUES
('TRG-RCV-001-1', 'TRG-RCV-001', 1, 'TRG-PO-001', 1, 'NIKE_KR', 'NK-AIRMAX90-BLK', 100, 0, '0', 'KR01-RECV-01', NULL, CURRENT_TIMESTAMP, 'SYSTEM'),
('TRG-RCV-002-1', 'TRG-RCV-002', 1, 'TRG-PO-002', 1, 'HM_KR', 'HM-BASIC-TEE-M', 200, 200, '5', 'KR02-RECV-01', 'LP-TRG-001', CURRENT_TIMESTAMP, 'SYSTEM'),
('TRG-RCV-CASC-1', 'TRG-RCV-CASCADE', 1, 'TRG-PO-CASCADE', 1, 'ADIDAS_IN', 'ADI-ULTRABOOST-BLK', 50, 50, '5', 'IN01-RECV-01', 'LP-TRG-002', CURRENT_TIMESTAMP, 'SYSTEM'),
('TRG-RCV-CASC-2', 'TRG-RCV-CASCADE', 2, 'TRG-PO-CASCADE', 2, 'ADIDAS_IN', 'ADI-SUPERSTAR-WHT', 50, 50, '5', 'IN01-RECV-01', 'LP-TRG-003', CURRENT_TIMESTAMP, 'SYSTEM');


-- =============================================================================
-- SECTION 4: INVENTORY DATA (For tr_lotxlocxid_* testing)
-- =============================================================================

DELETE FROM lotxlocxid WHERE id LIKE 'LP-TRG-%' OR id LIKE 'TRG-INV-%';

-- Existing inventory for trigger testing
INSERT INTO lotxlocxid (id, storerkey, sku, loc, lot, qty, qtyallocated, qtyonhold, status, adddate, addwho, editdate, editwho) VALUES
-- Inventory that will be updated by receipt finalization
('LP-TRG-001', 'HM_KR', 'HM-BASIC-TEE-M', 'KR02-RECV-01', 'LOT-TRG-001', 200, 0, 0, '1', CURRENT_TIMESTAMP, 'SYSTEM', CURRENT_TIMESTAMP, 'SYSTEM'),
('LP-TRG-002', 'ADIDAS_IN', 'ADI-ULTRABOOST-BLK', 'IN01-RECV-01', 'LOT-TRG-002', 50, 0, 0, '1', CURRENT_TIMESTAMP, 'SYSTEM', CURRENT_TIMESTAMP, 'SYSTEM'),
('LP-TRG-003', 'ADIDAS_IN', 'ADI-SUPERSTAR-WHT', 'IN01-RECV-01', 'LOT-TRG-003', 50, 0, 0, '1', CURRENT_TIMESTAMP, 'SYSTEM', CURRENT_TIMESTAMP, 'SYSTEM'),
-- Existing inventory for location capacity testing
('TRG-INV-EXIST', 'NIKE_KR', 'NK-AIRMAX90-BLK', 'KR01-STOR-A01', 'LOT-EXIST', 500, 100, 0, '1', CURRENT_TIMESTAMP, 'SYSTEM', CURRENT_TIMESTAMP, 'SYSTEM');


-- =============================================================================
-- SECTION 5: TRIGGER AUDIT LOG (Historical data for testing)
-- =============================================================================

DELETE FROM triggerauditlog WHERE auditkey LIKE 'AUD-TRG-%';

INSERT INTO triggerauditlog (auditkey, triggerkey, tablename, operation, recordkey, oldvalue, newvalue, executiontime, adddate, addwho) VALUES
-- Sample audit logs
('AUD-TRG-001', 'TRG-PO-INS', 'orders', 'INSERT', 'TRG-PO-001', NULL, '{"pokey":"TRG-PO-001","status":"0"}', 15, CURRENT_TIMESTAMP - INTERVAL '1 hour', 'SYSTEM'),
('AUD-TRG-002', 'TRG-RCV-INS', 'receipt', 'INSERT', 'TRG-RCV-001', NULL, '{"receiptkey":"TRG-RCV-001","status":"0"}', 12, CURRENT_TIMESTAMP - INTERVAL '30 minutes', 'SYSTEM'),
('AUD-TRG-003', 'TRG-RCV-UPD', 'receipt', 'UPDATE', 'TRG-RCV-002', '{"status":"0"}', '{"status":"5"}', 8, CURRENT_TIMESTAMP - INTERVAL '15 minutes', 'SYSTEM');


-- =============================================================================
-- SECTION 6: STATUS TRANSITION DATA (For trigger validation)
-- =============================================================================

DELETE FROM statushistory WHERE entitykey LIKE 'TRG-%';

INSERT INTO statushistory (historykey, entitykey, entitytype, fromstatus, tostatus, transitiontime, transitionby, reason) VALUES
-- PO status transitions
('HIST-TRG-001', 'TRG-PO-DUP', 'PO', '0', '5', CURRENT_TIMESTAMP - INTERVAL '2 hours', 'SYSTEM', 'Auto-populated'),
-- Receipt status transitions
('HIST-TRG-002', 'TRG-RCV-002', 'RECEIPT', '0', '5', CURRENT_TIMESTAMP - INTERVAL '30 minutes', 'SYSTEM', 'Receiving completed');


-- =============================================================================
-- SECTION 7: CASCADE RULES (For trigger cascade testing)
-- =============================================================================

DELETE FROM cascaderule WHERE rulekey LIKE 'CASC-%';

INSERT INTO cascaderule (rulekey, sourcetable, sourcecolumn, targettable, targetcolumn, cascadetype, enabled) VALUES
-- PO to Receipt cascade
('CASC-PO-RCV', 'orders', 'status', 'receipt', 'postatus', 'UPDATE', 'Y'),
-- Receipt to Inventory cascade
('CASC-RCV-INV', 'receipt', 'status', 'lotxlocxid', 'status', 'UPDATE', 'Y'),
-- PO Detail to PO Header cascade (totals)
('CASC-POD-PO', 'orderdetail', 'qtyreceived', 'orders', 'totalqtyreceived', 'SUM', 'Y'),
-- Receipt Detail to Receipt Header cascade
('CASC-RCVD-RCV', 'receiptdetail', 'qtyreceived', 'receipt', 'totalqtyreceived', 'SUM', 'Y');


-- =============================================================================
-- SECTION 8: TRIGGER ERROR SCENARIOS
-- =============================================================================

-- Data for error scenario testing
-- Note: Using inactive storer (TEST_STORER_ERR status=9) for error tests
INSERT INTO po (pokey, storerkey, externpokey, potype, status, facility, expecteddate, adddate, addwho) VALUES
-- PO with inactive storer (trigger should reject during processing)
('TRG-PO-ERR-STORER', 'TEST_STORER_ERR', 'TRG-EXT-ERR-1', 'STANDARD', '0', 'TEST01', CURRENT_TIMESTAMP + INTERVAL '3 days', CURRENT_TIMESTAMP, 'SYSTEM'),
-- PO that would cause duplicate key trigger error (same externpokey as TRG-PO-DUP)
('TRG-PO-ERR-DUP', 'NIKE_KR', 'TRG-EXT-DUP', 'STANDARD', '0', 'KR01', CURRENT_TIMESTAMP + INTERVAL '3 days', CURRENT_TIMESTAMP, 'SYSTEM');


-- =============================================================================
-- SECTION 9: LOCATION CAPACITY DATA (For inventory trigger)
-- =============================================================================

DELETE FROM locationcapacity WHERE loc LIKE 'KR01-STOR-%' OR loc LIKE 'IN01-STOR-%';

INSERT INTO locationcapacity (loc, facility, maxcube, currentcube, maxweight, currentweight, maxqty, currentqty, status) VALUES
('KR01-STOR-A01', 'KR01', 100.00, 25.00, 1000.00, 250.00, 1000, 500, '1'),
('KR01-STOR-A02', 'KR01', 100.00, 0.00, 1000.00, 0.00, 1000, 0, '1'),
('KR01-STOR-FULL', 'KR01', 100.00, 99.00, 1000.00, 990.00, 1000, 999, '1'),  -- Near capacity
('IN01-STOR-A01', 'IN01', 150.00, 50.00, 1500.00, 400.00, 1500, 100, '1');


-- =============================================================================
-- VERIFICATION
-- =============================================================================

SELECT 'TRIGGERCONFIG' as entity, COUNT(*) as count FROM triggerconfig WHERE triggerkey LIKE 'TRG-%'
UNION ALL
SELECT 'TRG_PO', COUNT(*) FROM po WHERE pokey LIKE 'TRG-PO-%'
UNION ALL
SELECT 'TRG_RECEIPT', COUNT(*) FROM receipt WHERE receiptkey LIKE 'TRG-RCV-%'
UNION ALL
SELECT 'TRG_INVENTORY', COUNT(*) FROM lotxlocxid WHERE id LIKE 'LP-TRG-%' OR id LIKE 'TRG-INV-%'
UNION ALL
SELECT 'TRIGGERAUDITLOG', COUNT(*) FROM triggerauditlog WHERE auditkey LIKE 'AUD-TRG-%'
UNION ALL
SELECT 'CASCADERULE', COUNT(*) FROM cascaderule WHERE rulekey LIKE 'CASC-%';

-- =============================================================================
-- END OF TRIGGER TEST DATA
-- =============================================================================
