-- =============================================================================
-- TD-TASK.sql
-- Task Test Data (Putaway, Pick) for PO Modernization E2E Testing
-- =============================================================================
-- Used for: Putaway release (F6), RDT task completion
-- Covers: F6-TC01 to F6-TC20, F3-TC08, RDT task workflows
-- =============================================================================

SET search_path TO dbo, public;

-- =============================================================================
-- SECTION 1: PUTAWAY TASKS
-- =============================================================================

DELETE FROM task WHERE taskkey LIKE 'PA-%' OR taskkey LIKE 'TSK-%';

INSERT INTO task (taskkey, tasktype, storerkey, sku, fromloc, fromid, toloc, toid, qty, status, priority, assignedto, receiptkey, pokey, adddate, addwho, editdate, editwho) VALUES
-- Released putaway tasks (ready for execution via RDT)
('PA-001', 'PUTAWAY', 'NIKE_KR', 'NK-AIRMAX90-BLK', 'KR01-RECV-01', 'LP-PA-001', 'KR01-STOR-A01', NULL, 100, '3', 5, 'RDT-OPR-004', 'RCV-HAPPY-001', 'PO-HAPPY-001', CURRENT_TIMESTAMP - INTERVAL '30 minutes', 'SYSTEM', CURRENT_TIMESTAMP, 'SYSTEM'),
('PA-002', 'PUTAWAY', 'NIKE_KR', 'NK-AIRMAX90-WHT', 'KR01-RECV-01', 'LP-PA-002', 'KR01-STOR-A02', NULL, 100, '3', 5, 'RDT-OPR-004', 'RCV-HAPPY-001', 'PO-HAPPY-001', CURRENT_TIMESTAMP - INTERVAL '30 minutes', 'SYSTEM', CURRENT_TIMESTAMP, 'SYSTEM'),
('PA-003', 'PUTAWAY', 'HM_KR', 'HM-BASIC-TEE-M', 'KR02-RECV-01', 'LP-PA-003', 'KR02-STOR-A01', NULL, 500, '3', 3, 'RDT-OPR-005', 'RCV-HAPPY-002', 'PO-HAPPY-002', CURRENT_TIMESTAMP - INTERVAL '20 minutes', 'SYSTEM', CURRENT_TIMESTAMP, 'SYSTEM'),

-- Pending putaway tasks (not yet released)
('PA-004', 'PUTAWAY', 'ADIDAS_IN', 'ADI-ULTRABOOST-BLK', 'IN01-RECV-01', 'LP-PA-004', 'IN01-STOR-A01', NULL, 75, '0', 5, NULL, 'RCV-HAPPY-003', 'PO-HAPPY-003', CURRENT_TIMESTAMP - INTERVAL '1 hour', 'SYSTEM', CURRENT_TIMESTAMP, 'SYSTEM'),
('PA-005', 'PUTAWAY', 'UNI_SG', 'UNI-DOVE-SOAP-100', 'SG01-RECV-01', 'LP-PA-005', 'SG01-STOR-A01', NULL, 1000, '0', 3, NULL, 'RCV-HAPPY-004', 'PO-HAPPY-004', CURRENT_TIMESTAMP - INTERVAL '45 minutes', 'SYSTEM', CURRENT_TIMESTAMP, 'SYSTEM'),

-- In-progress putaway task
('PA-006', 'PUTAWAY', 'NIKE_KR', 'NK-AF1-BLK', 'KR01-RECV-01', 'LP-PA-006', 'KR01-STOR-B01', NULL, 50, '5', 5, 'RDT-OPR-004', 'RCV-HAPPY-005', 'PO-HAPPY-005', CURRENT_TIMESTAMP - INTERVAL '15 minutes', 'SYSTEM', CURRENT_TIMESTAMP, 'SYSTEM'),

-- Completed putaway task
('PA-007', 'PUTAWAY', 'HM_KR', 'HM-SLIM-JEANS-32', 'KR02-RECV-01', 'LP-PA-007', 'KR02-STOR-B01', 'LP-PA-007', 200, '9', 3, 'RDT-OPR-005', 'RCV-HAPPY-006', 'PO-HAPPY-006', CURRENT_TIMESTAMP - INTERVAL '2 hours', 'SYSTEM', CURRENT_TIMESTAMP - INTERVAL '1 hour', 'RDT-OPR-005'),

-- Error scenario tasks
('PA-ERR-001', 'PUTAWAY', 'NIKE_KR', 'NK-AIRMAX90-BLK', 'KR01-RECV-01', 'LP-ERR-001', 'KR01-STOR-FULL', NULL, 100, '3', 5, NULL, 'RCV-ERROR-001', 'PO-ERROR-001', CURRENT_TIMESTAMP - INTERVAL '30 minutes', 'SYSTEM', CURRENT_TIMESTAMP, 'SYSTEM');


-- =============================================================================
-- SECTION 2: PICK TASKS (For outbound/XDock)
-- =============================================================================

INSERT INTO task (taskkey, tasktype, storerkey, sku, fromloc, fromid, toloc, toid, qty, status, priority, assignedto, orderkey, orderline, adddate, addwho) VALUES
-- Released pick tasks
('PICK-001', 'PICKING', 'NIKE_KR', 'NK-AIRMAX90-BLK', 'KR01-STOR-A01', 'INV-NIKE-001', 'KR01-STAGE-01', NULL, 50, '3', 5, 'RDT-OPR-006', 'SO-001', '00001', CURRENT_TIMESTAMP - INTERVAL '20 minutes', 'SYSTEM'),
('PICK-002', 'PICKING', 'HM_KR', 'HM-BASIC-TEE-M', 'KR02-STOR-A02', 'INV-HM-002', 'KR02-STAGE-01', NULL, 100, '3', 3, 'RDT-OPR-007', 'SO-002', '00001', CURRENT_TIMESTAMP - INTERVAL '15 minutes', 'SYSTEM'),

-- XDock pick tasks
('XDOCK-PICK-001', 'XDOCK', 'NIKE_KR', 'NK-AIRMAX90-BLK', 'KR01-XDOCK-01', 'XDOCK-001', 'KR01-SHIP-01', NULL, 100, '3', 1, NULL, 'SO-XD-001', '00001', CURRENT_TIMESTAMP - INTERVAL '10 minutes', 'SYSTEM');


-- =============================================================================
-- SECTION 3: TASK DETAILS (For multi-LP tasks)
-- =============================================================================

DELETE FROM taskdetail WHERE taskkey LIKE 'PA-%' OR taskkey LIKE 'PICK-%';

INSERT INTO taskdetail (taskkey, taskdetailkey, storerkey, sku, fromloc, fromid, toloc, toid, qty, status, lottable01, lottable02, adddate, addwho) VALUES
-- Details for PA-001 (Nike with lottables)
('PA-001', 'PA-001-D01', 'NIKE_KR', 'NK-AIRMAX90-BLK', 'KR01-RECV-01', 'LP-PA-001', 'KR01-STOR-A01', NULL, 100, '3', 'STYLE-AM90-2024', 'COLOR-001-BLK', CURRENT_TIMESTAMP - INTERVAL '30 minutes', 'SYSTEM'),
-- Details for PA-002
('PA-002', 'PA-002-D01', 'NIKE_KR', 'NK-AIRMAX90-WHT', 'KR01-RECV-01', 'LP-PA-002', 'KR01-STOR-A02', NULL, 100, '3', 'STYLE-AM90-2024', 'COLOR-002-WHT', CURRENT_TIMESTAMP - INTERVAL '30 minutes', 'SYSTEM'),
-- Details for PICK-001
('PICK-001', 'PICK-001-D01', 'NIKE_KR', 'NK-AIRMAX90-BLK', 'KR01-STOR-A01', 'INV-NIKE-001', 'KR01-STAGE-01', NULL, 50, '3', 'STYLE-AM90-2024', 'COLOR-001-BLK', CURRENT_TIMESTAMP - INTERVAL '20 minutes', 'SYSTEM');


-- =============================================================================
-- SECTION 4: PUTAWAY STRATEGIES
-- =============================================================================

DELETE FROM putawaystrategy WHERE strategykey LIKE 'PASTRAT-%';

INSERT INTO putawaystrategy (strategykey, storerkey, sku, skugroup, zone, priority, ruletype, maxqty, status, adddate, addwho) VALUES
-- Nike putaway strategy (by style)
('PASTRAT-NIKE-001', 'NIKE_KR', NULL, 'FOOTWEAR', 'ZONE-A', 1, 'DIRECTED', 1000, '1', CURRENT_TIMESTAMP, 'SYSTEM'),
('PASTRAT-NIKE-002', 'NIKE_KR', NULL, 'APPAREL', 'ZONE-B', 2, 'DIRECTED', 2000, '1', CURRENT_TIMESTAMP, 'SYSTEM'),
-- H&M putaway strategy (fast-fashion)
('PASTRAT-HM-001', 'HM_KR', NULL, 'APPAREL', 'ZONE-FF', 1, 'FIFO', 5000, '1', CURRENT_TIMESTAMP, 'SYSTEM'),
-- Default strategy
('PASTRAT-DEFAULT', NULL, NULL, NULL, 'ZONE-DEFAULT', 99, 'RANDOM', 10000, '1', CURRENT_TIMESTAMP, 'SYSTEM');


-- =============================================================================
-- SECTION 5: TASK ASSIGNMENTS (User assignments)
-- =============================================================================

DELETE FROM taskassignment WHERE assignmentkey LIKE 'ASSIGN-%';

INSERT INTO taskassignment (assignmentkey, taskkey, userid, facility, assignedtime, starttime, completetime, status, device) VALUES
-- Active assignments
('ASSIGN-001', 'PA-001', 'RDT-OPR-004', 'KR01', CURRENT_TIMESTAMP - INTERVAL '30 minutes', CURRENT_TIMESTAMP - INTERVAL '25 minutes', NULL, 'IN_PROGRESS', 'RDT-KR01-003'),
('ASSIGN-002', 'PA-006', 'RDT-OPR-004', 'KR01', CURRENT_TIMESTAMP - INTERVAL '15 minutes', CURRENT_TIMESTAMP - INTERVAL '10 minutes', NULL, 'IN_PROGRESS', 'RDT-KR01-003'),
-- Completed assignments
('ASSIGN-003', 'PA-007', 'RDT-OPR-005', 'KR02', CURRENT_TIMESTAMP - INTERVAL '2 hours', CURRENT_TIMESTAMP - INTERVAL '1 hour 50 minutes', CURRENT_TIMESTAMP - INTERVAL '1 hour', 'COMPLETED', 'RDT-KR02-001'),
-- Pending assignments
('ASSIGN-004', 'PICK-001', 'RDT-OPR-006', 'KR01', CURRENT_TIMESTAMP - INTERVAL '20 minutes', NULL, NULL, 'ASSIGNED', NULL);


-- =============================================================================
-- SECTION 6: TASK HISTORY
-- =============================================================================

DELETE FROM taskhistory WHERE historykey LIKE 'HIST-%';

INSERT INTO taskhistory (historykey, taskkey, action, fromstatus, tostatus, actiontime, actionby, notes) VALUES
-- PA-001 history
('HIST-001', 'PA-001', 'CREATE', NULL, '0', CURRENT_TIMESTAMP - INTERVAL '35 minutes', 'SYSTEM', 'Task created from receipt finalization'),
('HIST-002', 'PA-001', 'RELEASE', '0', '3', CURRENT_TIMESTAMP - INTERVAL '30 minutes', 'SYSTEM', 'Auto-released by job'),
('HIST-003', 'PA-001', 'ASSIGN', '3', '3', CURRENT_TIMESTAMP - INTERVAL '25 minutes', 'SYSTEM', 'Assigned to RDT-OPR-004'),
-- PA-007 history (completed)
('HIST-004', 'PA-007', 'CREATE', NULL, '0', CURRENT_TIMESTAMP - INTERVAL '3 hours', 'SYSTEM', 'Task created'),
('HIST-005', 'PA-007', 'RELEASE', '0', '3', CURRENT_TIMESTAMP - INTERVAL '2 hours 30 minutes', 'SYSTEM', 'Released'),
('HIST-006', 'PA-007', 'START', '3', '5', CURRENT_TIMESTAMP - INTERVAL '1 hour 50 minutes', 'RDT-OPR-005', 'Started via RDT'),
('HIST-007', 'PA-007', 'COMPLETE', '5', '9', CURRENT_TIMESTAMP - INTERVAL '1 hour', 'RDT-OPR-005', 'Completed via RDT');


-- =============================================================================
-- SECTION 7: TASK QUEUES (For job processing)
-- =============================================================================

DELETE FROM taskqueue WHERE queuekey LIKE 'QUEUE-%';

INSERT INTO taskqueue (queuekey, facility, zone, tasktype, priority, maxconcurrent, currentactive, status, adddate, addwho) VALUES
('QUEUE-KR01-PA', 'KR01', NULL, 'PUTAWAY', 5, 20, 5, '1', CURRENT_TIMESTAMP, 'SYSTEM'),
('QUEUE-KR01-PICK', 'KR01', NULL, 'PICKING', 3, 30, 10, '1', CURRENT_TIMESTAMP, 'SYSTEM'),
('QUEUE-KR02-PA', 'KR02', NULL, 'PUTAWAY', 5, 15, 3, '1', CURRENT_TIMESTAMP, 'SYSTEM'),
('QUEUE-KR02-PICK', 'KR02', NULL, 'PICKING', 3, 25, 8, '1', CURRENT_TIMESTAMP, 'SYSTEM'),
('QUEUE-IN01-PA', 'IN01', NULL, 'PUTAWAY', 5, 10, 2, '1', CURRENT_TIMESTAMP, 'SYSTEM');


-- =============================================================================
-- SECTION 8: LICENSE PLATES (For task tracking)
-- =============================================================================

DELETE FROM licenseplate WHERE id LIKE 'LP-PA-%' OR id LIKE 'LP-ERR-%';

INSERT INTO licenseplate (id, storerkey, status, parentid, childflag, loc, adddate, addwho) VALUES
-- License plates for putaway tasks
('LP-PA-001', 'NIKE_KR', '1', NULL, 'N', 'KR01-RECV-01', CURRENT_TIMESTAMP - INTERVAL '1 hour', 'SYSTEM'),
('LP-PA-002', 'NIKE_KR', '1', NULL, 'N', 'KR01-RECV-01', CURRENT_TIMESTAMP - INTERVAL '1 hour', 'SYSTEM'),
('LP-PA-003', 'HM_KR', '1', NULL, 'N', 'KR02-RECV-01', CURRENT_TIMESTAMP - INTERVAL '45 minutes', 'SYSTEM'),
('LP-PA-004', 'ADIDAS_IN', '1', NULL, 'N', 'IN01-RECV-01', CURRENT_TIMESTAMP - INTERVAL '1 hour', 'SYSTEM'),
('LP-PA-005', 'UNI_SG', '1', NULL, 'N', 'SG01-RECV-01', CURRENT_TIMESTAMP - INTERVAL '45 minutes', 'SYSTEM'),
('LP-PA-006', 'NIKE_KR', '1', NULL, 'N', 'KR01-RECV-01', CURRENT_TIMESTAMP - INTERVAL '30 minutes', 'SYSTEM'),
('LP-PA-007', 'HM_KR', '9', NULL, 'N', 'KR02-STOR-B01', CURRENT_TIMESTAMP - INTERVAL '2 hours', 'SYSTEM'),  -- Completed
-- Error scenario LP
('LP-ERR-001', 'NIKE_KR', '1', NULL, 'N', 'KR01-RECV-01', CURRENT_TIMESTAMP - INTERVAL '30 minutes', 'SYSTEM');


-- =============================================================================
-- VERIFICATION
-- =============================================================================

SELECT 'TASK' as entity, COUNT(*) as count FROM task WHERE taskkey LIKE 'PA-%' OR taskkey LIKE 'PICK-%' OR taskkey LIKE 'XDOCK-PICK-%'
UNION ALL
SELECT 'TASKDETAIL', COUNT(*) FROM taskdetail WHERE taskkey LIKE 'PA-%' OR taskkey LIKE 'PICK-%'
UNION ALL
SELECT 'PUTAWAYSTRATEGY', COUNT(*) FROM putawaystrategy WHERE strategykey LIKE 'PASTRAT-%'
UNION ALL
SELECT 'TASKASSIGNMENT', COUNT(*) FROM taskassignment WHERE assignmentkey LIKE 'ASSIGN-%'
UNION ALL
SELECT 'TASKHISTORY', COUNT(*) FROM taskhistory WHERE historykey LIKE 'HIST-%'
UNION ALL
SELECT 'LICENSEPLATE', COUNT(*) FROM licenseplate WHERE id LIKE 'LP-PA-%' OR id LIKE 'LP-ERR-%';

-- =============================================================================
-- END OF TASK TEST DATA
-- =============================================================================
