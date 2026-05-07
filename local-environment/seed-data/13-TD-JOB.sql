-- =============================================================================
-- TD-JOB.sql
-- SQL Job Test Data for PO Modernization E2E Testing
-- =============================================================================
-- Entry Point: SQL Jobs (40 Test Cases)
-- Covers: F1-TC04, F2-TC06, F3-TC05, F4-TC03, F6-TC03, F6-TC15-17
-- =============================================================================

SET search_path TO dbo, public;

-- =============================================================================
-- SECTION 1: JOB CONFIGURATION
-- =============================================================================

DELETE FROM jobconfiguration WHERE jobkey LIKE 'JOB-%' OR jobkey LIKE 'WMS_%';

INSERT INTO jobconfiguration (jobkey, jobname, description, jobtype, schedule, enabled, facility, storerkey, lastrun, nextrun, status, adddate, addwho) VALUES
-- Auto Population Jobs
('JOB-AUTO-POP', 'WMS Auto Populate PO', 'Automatically populate POs from pending ASNs', 'POPULATE', '*/15 * * * *', 'Y', NULL, NULL, NULL, CURRENT_TIMESTAMP + INTERVAL '15 minutes', '1', CURRENT_TIMESTAMP, 'SYSTEM'),
('JOB-AUTO-POP-NIKE', 'WMS Auto Populate PO - Nike', 'Nike-specific auto populate', 'POPULATE', '*/10 * * * *', 'Y', 'KR01', 'NIKE_KR', NULL, CURRENT_TIMESTAMP + INTERVAL '10 minutes', '1', CURRENT_TIMESTAMP, 'SYSTEM'),

-- Auto Finalize Jobs
('JOB-AUTO-FIN', 'WMS Auto Finalize ASN', 'Automatically finalize receipts in status 5', 'FINALIZE', '*/15 * * * *', 'Y', NULL, NULL, NULL, CURRENT_TIMESTAMP + INTERVAL '15 minutes', '1', CURRENT_TIMESTAMP, 'SYSTEM'),
('JOB-AUTO-FIN-HM', 'WMS Auto Finalize ASN - H&M', 'H&M fast-fashion auto finalize', 'FINALIZE', '*/5 * * * *', 'Y', 'KR02', 'HM_KR', NULL, CURRENT_TIMESTAMP + INTERVAL '5 minutes', '1', CURRENT_TIMESTAMP, 'SYSTEM'),

-- Putaway Release Jobs
('JOB-PA-REL', 'WMS Auto PA Release', 'Release putaway tasks automatically', 'PUTAWAY', '*/30 * * * *', 'Y', NULL, NULL, NULL, CURRENT_TIMESTAMP + INTERVAL '30 minutes', '1', CURRENT_TIMESTAMP, 'SYSTEM'),
('JOB-PA-BATCH', 'WMS Batch PA Standard', 'Batch putaway release - standard', 'PUTAWAY', '0 */2 * * *', 'Y', NULL, NULL, NULL, CURRENT_TIMESTAMP + INTERVAL '2 hours', '1', CURRENT_TIMESTAMP, 'SYSTEM'),
('JOB-PA-NIKE-CRW', 'WMS Batch PA Nike CRW', 'Nike CRW putaway release', 'PUTAWAY', '0 6,14,22 * * *', 'Y', 'KR01', 'NIKE_KR', NULL, CURRENT_TIMESTAMP + INTERVAL '8 hours', '1', CURRENT_TIMESTAMP, 'SYSTEM'),
('JOB-PA-ULM', 'WMS PA Release ULM', 'Unilever putaway release', 'PUTAWAY', '0 8,16 * * *', 'Y', 'SG01', 'UNI_SG', NULL, CURRENT_TIMESTAMP + INTERVAL '8 hours', '1', CURRENT_TIMESTAMP, 'SYSTEM'),

-- Generic Inbound Jobs
('JOB-GEN-INB', 'WMS Generic Inbound PO', 'Process inbound PO files', 'INBOUND', '*/5 * * * *', 'Y', NULL, NULL, NULL, CURRENT_TIMESTAMP + INTERVAL '5 minutes', '1', CURRENT_TIMESTAMP, 'SYSTEM'),

-- XDock Jobs
('JOB-XDOCK-ALLOC', 'WMS XDock Auto Allocate', 'Cross-dock automatic allocation', 'XDOCK', '*/15 * * * *', 'Y', NULL, NULL, NULL, CURRENT_TIMESTAMP + INTERVAL '15 minutes', '1', CURRENT_TIMESTAMP, 'SYSTEM'),

-- Archival Jobs
('JOB-ARCHIVE', 'WMS Archive PO', 'Archive old completed POs', 'ARCHIVE', '0 2 * * *', 'Y', NULL, NULL, NULL, CURRENT_TIMESTAMP + INTERVAL '1 day', '1', CURRENT_TIMESTAMP, 'SYSTEM'),

-- Error Test Jobs
('JOB-DISABLED', 'Disabled Test Job', 'Job that is disabled', 'POPULATE', '*/60 * * * *', 'N', NULL, NULL, NULL, NULL, '0', CURRENT_TIMESTAMP, 'SYSTEM'),
('JOB-FAILED', 'Failed Test Job', 'Job in failed state', 'FINALIZE', '*/60 * * * *', 'Y', NULL, NULL, CURRENT_TIMESTAMP - INTERVAL '1 hour', NULL, '9', CURRENT_TIMESTAMP, 'SYSTEM');


-- =============================================================================
-- SECTION 2: JOB PARAMETERS
-- =============================================================================

DELETE FROM jobparameter WHERE jobkey LIKE 'JOB-%';

INSERT INTO jobparameter (jobkey, paramkey, paramvalue, paramtype, description) VALUES
-- Auto Populate parameters
('JOB-AUTO-POP', 'BATCH_SIZE', '100', 'INT', 'Number of POs to process per batch'),
('JOB-AUTO-POP', 'STATUS_FILTER', '0', 'STRING', 'PO status to filter'),
('JOB-AUTO-POP', 'MAX_RETRIES', '3', 'INT', 'Maximum retry attempts'),
('JOB-AUTO-POP', 'RETRY_DELAY_MS', '5000', 'INT', 'Delay between retries in ms'),

-- Nike-specific parameters
('JOB-AUTO-POP-NIKE', 'BATCH_SIZE', '50', 'INT', 'Smaller batch for Nike'),
('JOB-AUTO-POP-NIKE', 'LOTTABLE_REQUIRED', 'Y', 'STRING', 'Require lottable validation'),
('JOB-AUTO-POP-NIKE', 'PLUGIN_CLASS', 'NikePopulatePlugin', 'STRING', 'Plugin to execute'),

-- Auto Finalize parameters
('JOB-AUTO-FIN', 'BATCH_SIZE', '50', 'INT', 'Receipts per batch'),
('JOB-AUTO-FIN', 'STATUS_FILTER', '5', 'STRING', 'Receipt status to finalize'),
('JOB-AUTO-FIN', 'AUTO_CLOSE_PO', 'Y', 'STRING', 'Auto-close PO after finalize'),
('JOB-AUTO-FIN', 'RELEASE_PA', 'Y', 'STRING', 'Release putaway tasks'),

-- H&M fast-fashion parameters
('JOB-AUTO-FIN-HM', 'BATCH_SIZE', '200', 'INT', 'Large batch for fast fashion'),
('JOB-AUTO-FIN-HM', 'PRIORITY', 'HIGH', 'STRING', 'High priority processing'),
('JOB-AUTO-FIN-HM', 'SKIP_LOTTABLE', 'Y', 'STRING', 'No lottable for H&M'),

-- Putaway parameters
('JOB-PA-REL', 'TASK_LIMIT', '500', 'INT', 'Max tasks to release'),
('JOB-PA-REL', 'ZONE_FILTER', NULL, 'STRING', 'Process all zones'),

-- XDock parameters
('JOB-XDOCK-ALLOC', 'ALLOCATION_MODE', 'FIFO', 'STRING', 'First-in-first-out allocation'),
('JOB-XDOCK-ALLOC', 'MAX_ORDERS', '100', 'INT', 'Max orders to process'),

-- Archive parameters
('JOB-ARCHIVE', 'RETENTION_DAYS', '365', 'INT', 'Days to retain before archive'),
('JOB-ARCHIVE', 'ARCHIVE_TABLE', 'po_archive', 'STRING', 'Archive destination table');


-- =============================================================================
-- SECTION 3: JOB HISTORY (For testing job status checks)
-- =============================================================================

DELETE FROM jobhistory WHERE jobkey LIKE 'JOB-%';

INSERT INTO jobhistory (jobkey, runid, starttime, endtime, status, recordsprocessed, errorcount, errormessage) VALUES
-- Successful runs
('JOB-AUTO-POP', 'RUN-001', CURRENT_TIMESTAMP - INTERVAL '15 minutes', CURRENT_TIMESTAMP - INTERVAL '14 minutes', 'SUCCESS', 25, 0, NULL),
('JOB-AUTO-POP', 'RUN-002', CURRENT_TIMESTAMP - INTERVAL '30 minutes', CURRENT_TIMESTAMP - INTERVAL '28 minutes', 'SUCCESS', 18, 0, NULL),
('JOB-AUTO-FIN', 'RUN-003', CURRENT_TIMESTAMP - INTERVAL '15 minutes', CURRENT_TIMESTAMP - INTERVAL '13 minutes', 'SUCCESS', 12, 0, NULL),
('JOB-PA-REL', 'RUN-004', CURRENT_TIMESTAMP - INTERVAL '30 minutes', CURRENT_TIMESTAMP - INTERVAL '29 minutes', 'SUCCESS', 45, 0, NULL),
-- Failed runs (for error testing)
('JOB-FAILED', 'RUN-ERR-001', CURRENT_TIMESTAMP - INTERVAL '1 hour', CURRENT_TIMESTAMP - INTERVAL '59 minutes', 'FAILED', 0, 5, 'Database connection timeout'),
('JOB-FAILED', 'RUN-ERR-002', CURRENT_TIMESTAMP - INTERVAL '2 hours', CURRENT_TIMESTAMP - INTERVAL '119 minutes', 'FAILED', 3, 2, 'Validation error on receipt RCV-123'),
-- Partial success
('JOB-AUTO-POP', 'RUN-PART-001', CURRENT_TIMESTAMP - INTERVAL '45 minutes', CURRENT_TIMESTAMP - INTERVAL '43 minutes', 'PARTIAL', 20, 3, 'Warning: 3 records skipped due to validation');


-- =============================================================================
-- SECTION 4: JOB-SPECIFIC POs (For batch processing tests)
-- =============================================================================

DELETE FROM po WHERE pokey LIKE 'JOB-PO-%';
DELETE FROM podetail WHERE pokey LIKE 'JOB-PO-%';

-- POs ready for auto-population job
INSERT INTO po (pokey, storerkey, externpokey, potype, status, facility, expecteddate, adddate, addwho) VALUES
('JOB-PO-001', 'NIKE_KR', 'JOB-EXT-001', 'STANDARD', '0', 'KR01', CURRENT_TIMESTAMP + INTERVAL '3 days', CURRENT_TIMESTAMP - INTERVAL '1 hour', 'SYSTEM'),
('JOB-PO-002', 'NIKE_KR', 'JOB-EXT-002', 'STANDARD', '0', 'KR01', CURRENT_TIMESTAMP + INTERVAL '3 days', CURRENT_TIMESTAMP - INTERVAL '1 hour', 'SYSTEM'),
('JOB-PO-003', 'HM_KR', 'JOB-EXT-003', 'STANDARD', '0', 'KR02', CURRENT_TIMESTAMP + INTERVAL '2 days', CURRENT_TIMESTAMP - INTERVAL '1 hour', 'SYSTEM'),
('JOB-PO-004', 'ADIDAS_IN', 'JOB-EXT-004', 'STANDARD', '0', 'IN01', CURRENT_TIMESTAMP + INTERVAL '5 days', CURRENT_TIMESTAMP - INTERVAL '1 hour', 'SYSTEM'),
('JOB-PO-005', 'UNI_SG', 'JOB-EXT-005', 'STANDARD', '0', 'SG01', CURRENT_TIMESTAMP + INTERVAL '4 days', CURRENT_TIMESTAMP - INTERVAL '1 hour', 'SYSTEM');

INSERT INTO podetail (pokey, polinenumber, storerkey, sku, qtyordered, qtyreceived, unitprice, status, adddate, addwho) VALUES
('JOB-PO-001', '00001', 'NIKE_KR', 'NK-AIRMAX90-BLK', 100, 0, 89.99, '0', CURRENT_TIMESTAMP, 'SYSTEM'),
('JOB-PO-001', '00002', 'NIKE_KR', 'NK-AIRMAX90-WHT', 100, 0, 89.99, '0', CURRENT_TIMESTAMP, 'SYSTEM'),
('JOB-PO-002', '00001', 'NIKE_KR', 'NK-AF1-BLK', 75, 0, 109.99, '0', CURRENT_TIMESTAMP, 'SYSTEM'),
('JOB-PO-003', '00001', 'HM_KR', 'HM-BASIC-TEE-M', 500, 0, 4.99, '0', CURRENT_TIMESTAMP, 'SYSTEM'),
('JOB-PO-003', '00002', 'HM_KR', 'HM-BASIC-TEE-L', 500, 0, 4.99, '0', CURRENT_TIMESTAMP, 'SYSTEM'),
('JOB-PO-004', '00001', 'ADIDAS_IN', 'ADI-ULTRABOOST-BLK', 50, 0, 149.99, '0', CURRENT_TIMESTAMP, 'SYSTEM'),
('JOB-PO-005', '00001', 'UNI_SG', 'UNI-DOVE-SOAP-100', 1000, 0, 2.50, '0', CURRENT_TIMESTAMP, 'SYSTEM');


-- =============================================================================
-- SECTION 5: JOB-SPECIFIC RECEIPTS (For auto-finalize job)
-- =============================================================================

DELETE FROM receipt WHERE receiptkey LIKE 'JOB-RCV-%';
DELETE FROM receiptdetail WHERE receiptkey LIKE 'JOB-RCV-%';

-- Receipts ready for auto-finalization job
INSERT INTO receipt (receiptkey, pokey, storerkey, status, type, facility, receiptdate, adddate, addwho) VALUES
('JOB-RCV-001', 'JOB-PO-001', 'NIKE_KR', '5', 'Normal', 'KR01', CURRENT_TIMESTAMP - INTERVAL '30 minutes', CURRENT_TIMESTAMP - INTERVAL '30 minutes', 'SYSTEM'),
('JOB-RCV-002', 'JOB-PO-003', 'HM_KR', '5', 'Normal', 'KR02', CURRENT_TIMESTAMP - INTERVAL '15 minutes', CURRENT_TIMESTAMP - INTERVAL '15 minutes', 'SYSTEM'),
('JOB-RCV-003', 'JOB-PO-004', 'ADIDAS_IN', '5', 'Normal', 'IN01', CURRENT_TIMESTAMP - INTERVAL '45 minutes', CURRENT_TIMESTAMP - INTERVAL '45 minutes', 'SYSTEM');

INSERT INTO receiptdetail (receiptdetailkey, receiptkey, receiptlinenumber, pokey, polinenumber, storerkey, sku, qtyexpected, qtyreceived, status, toloc, toid, adddate, addwho) VALUES
('JOB-RCV-001-1', 'JOB-RCV-001', 1, 'JOB-PO-001', 1, 'NIKE_KR', 'NK-AIRMAX90-BLK', 100, 100, '5', 'KR01-RECV-01', 'LP-JOB-001', CURRENT_TIMESTAMP, 'SYSTEM'),
('JOB-RCV-001-2', 'JOB-RCV-001', 2, 'JOB-PO-001', 2, 'NIKE_KR', 'NK-AIRMAX90-WHT', 100, 100, '5', 'KR01-RECV-01', 'LP-JOB-002', CURRENT_TIMESTAMP, 'SYSTEM'),
('JOB-RCV-002-1', 'JOB-RCV-002', 1, 'JOB-PO-003', 1, 'HM_KR', 'HM-BASIC-TEE-M', 500, 500, '5', 'KR02-RECV-01', 'LP-JOB-003', CURRENT_TIMESTAMP, 'SYSTEM'),
('JOB-RCV-002-2', 'JOB-RCV-002', 2, 'JOB-PO-003', 2, 'HM_KR', 'HM-BASIC-TEE-L', 500, 500, '5', 'KR02-RECV-01', 'LP-JOB-004', CURRENT_TIMESTAMP, 'SYSTEM'),
('JOB-RCV-003-1', 'JOB-RCV-003', 1, 'JOB-PO-004', 1, 'ADIDAS_IN', 'ADI-ULTRABOOST-BLK', 50, 50, '5', 'IN01-RECV-01', 'LP-JOB-005', CURRENT_TIMESTAMP, 'SYSTEM');


-- =============================================================================
-- SECTION 6: JOB LOCKS (For concurrency testing)
-- =============================================================================

DELETE FROM joblock WHERE jobkey LIKE 'JOB-%';

-- Currently locked job (for concurrency test)
INSERT INTO joblock (jobkey, lockid, lockedby, locktime, expiresAt) VALUES
('JOB-AUTO-POP', 'LOCK-001', 'worker-01', CURRENT_TIMESTAMP - INTERVAL '5 minutes', CURRENT_TIMESTAMP + INTERVAL '10 minutes');


-- =============================================================================
-- SECTION 7: JOB NOTIFICATION CONFIGURATION
-- =============================================================================

DELETE FROM jobnotification WHERE jobkey LIKE 'JOB-%';

INSERT INTO jobnotification (jobkey, notifytype, recipient, onfailure, onsuccess, onpartial) VALUES
('JOB-AUTO-POP', 'EMAIL', 'wms-team@example.com', 'Y', 'N', 'Y'),
('JOB-AUTO-FIN', 'EMAIL', 'wms-team@example.com', 'Y', 'N', 'Y'),
('JOB-AUTO-FIN', 'SLACK', '#wms-alerts', 'Y', 'N', 'N'),
('JOB-XDOCK-ALLOC', 'EMAIL', 'xdock-team@example.com', 'Y', 'Y', 'Y');


-- =============================================================================
-- VERIFICATION
-- =============================================================================

SELECT 'JOBCONFIGURATION' as entity, COUNT(*) as count FROM jobconfiguration WHERE jobkey LIKE 'JOB-%'
UNION ALL
SELECT 'JOBPARAMETER', COUNT(*) FROM jobparameter WHERE jobkey LIKE 'JOB-%'
UNION ALL
SELECT 'JOBHISTORY', COUNT(*) FROM jobhistory WHERE jobkey LIKE 'JOB-%'
UNION ALL
SELECT 'JOB_PO', COUNT(*) FROM po WHERE pokey LIKE 'JOB-PO-%'
UNION ALL
SELECT 'JOB_RECEIPT', COUNT(*) FROM receipt WHERE receiptkey LIKE 'JOB-RCV-%';

-- =============================================================================
-- END OF JOB TEST DATA
-- =============================================================================
