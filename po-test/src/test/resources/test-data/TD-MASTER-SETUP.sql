-- ═══════════════════════════════════════════════════════════════
-- PO MODERNIZATION - MASTER TEST DATA SETUP
-- ═══════════════════════════════════════════════════════════════
-- Version: 2.0
-- Purpose: Complete test data setup for all 5 entry points
-- Usage: Run this script to set up all test data in local DB
--
-- Entry Points Covered:
--   1. API Gateway (Web) - REST API testing
--   2. EDI Interface - EDI 850/856 testing
--   3. DB Triggers - Event-driven testing
--   4. SQL Jobs - Scheduled batch testing
--   5. RDT API - Handheld device testing
-- ═══════════════════════════════════════════════════════════════

SET search_path TO dbo, public;

-- ═══════════════════════════════════════════════════════════════
-- LOAD ORDER (Respecting foreign key dependencies)
-- ═══════════════════════════════════════════════════════════════
--
-- 1. Reference Data (TD-CODELKUP.sql)
-- 2. Master Data:
--    - Storers & Facilities (TD-STORER.sql)
--    - Locations (TD-LOCATION.sql)
--    - SKUs & Packs (TD-SKU.sql)
-- 3. Client Config (TD-CLIENT.sql)
-- 4. Transaction Data:
--    - Happy Path POs (TD-PO-HAPPY.sql)
--    - Error POs (TD-PO-ERROR.sql)
--    - Happy Path Receipts (TD-RCV-HAPPY.sql)
--    - Error Receipts (TD-RCV-ERROR.sql)
-- 5. Entry Point Specific:
--    - Inventory (TD-INVENTORY.sql)
--    - Tasks (TD-TASK.sql)
--    - Jobs (TD-JOB.sql)
--    - Triggers (TD-TRIGGER.sql)
--    - RDT (TD-RDT.sql)
--    - Orders/XDock (TD-ORDER.sql)
-- ═══════════════════════════════════════════════════════════════

BEGIN;

\echo '=============================================='
\echo '  LOADING TEST DATA - ALL 5 ENTRY POINTS'
\echo '=============================================='

-- ═══════════════════════════════════════════════════════════════
-- STEP 1: Reference Data (No dependencies)
-- ═══════════════════════════════════════════════════════════════
\echo ''
\echo '>>> Step 1/12: Loading Reference Data (CODELKUP)...'
\i TD-CODELKUP.sql

-- ═══════════════════════════════════════════════════════════════
-- STEP 2-4: Core Master Data
-- ═══════════════════════════════════════════════════════════════
\echo ''
\echo '>>> Step 2/12: Loading Storer Data...'
\i TD-STORER.sql

\echo ''
\echo '>>> Step 3/12: Loading Location Data...'
\i TD-LOCATION.sql

\echo ''
\echo '>>> Step 4/12: Loading SKU Data...'
\i TD-SKU.sql

-- ═══════════════════════════════════════════════════════════════
-- STEP 5: Client Configuration
-- ═══════════════════════════════════════════════════════════════
\echo ''
\echo '>>> Step 5/12: Loading Client Configuration...'
\i TD-CLIENT.sql

-- ═══════════════════════════════════════════════════════════════
-- STEP 6-9: Transaction Test Data
-- ═══════════════════════════════════════════════════════════════
\echo ''
\echo '>>> Step 6/12: Loading Happy Path POs...'
\i TD-PO-HAPPY.sql

\echo ''
\echo '>>> Step 7/12: Loading Error/Edge Case POs...'
\i TD-PO-ERROR.sql

\echo ''
\echo '>>> Step 8/12: Loading Happy Path Receipts...'
\i TD-RCV-HAPPY.sql

\echo ''
\echo '>>> Step 9/12: Loading Error/Edge Case Receipts...'
\i TD-RCV-ERROR.sql

-- ═══════════════════════════════════════════════════════════════
-- STEP 10-12: Entry Point Specific Data
-- ═══════════════════════════════════════════════════════════════
\echo ''
\echo '>>> Step 10/12: Loading Inventory Data (Triggers)...'
\i TD-INVENTORY.sql

\echo ''
\echo '>>> Step 11/12: Loading Task Data (RDT, Putaway)...'
\i TD-TASK.sql

\echo ''
\echo '>>> Step 12/12: Loading Entry Point Data (Jobs, RDT, Triggers, Orders)...'
\i TD-JOB.sql
\i TD-TRIGGER.sql
\i TD-RDT.sql
\i TD-ORDER.sql

COMMIT;

\echo ''
\echo '=============================================='
\echo '  TEST DATA SETUP COMPLETE'
\echo '=============================================='
\echo ''
\echo 'Entry Point Coverage:'
\echo '  1. API Gateway (Web)  - POs, Receipts, Master Data'
\echo '  2. EDI Interface      - See edi/ folder for samples'
\echo '  3. DB Triggers        - Trigger config, audit data'
\echo '  4. SQL Jobs           - Job config, batch data'
\echo '  5. RDT API            - Users, devices, sessions'
\echo ''
\echo 'Data Loaded:'
\echo '  - Reference Data (CODELKUP): Status codes, hold codes'
\echo '  - Storers: 15+ storers with addresses'
\echo '  - Facilities: 12+ facilities across regions'
\echo '  - Locations: 70+ locations with putaway zones'
\echo '  - SKUs: 25+ SKUs with pack definitions'
\echo '  - Client Config: 4 client plugins'
\echo '  - Happy Path POs: 10 POs'
\echo '  - Error/Edge POs: 8+ POs'
\echo '  - Happy Path Receipts: 5 receipts'
\echo '  - Error/Edge Receipts: 7 receipts'
\echo '  - Inventory: 20+ LOTxLOCxID records'
\echo '  - Tasks: 10+ putaway/pick tasks'
\echo '  - Jobs: 12+ job configurations'
\echo '  - RDT Users: 10+ operators'
\echo '  - Orders: 10+ sales orders for XDock'
\echo ''
\echo 'EDI Test Files (edi/ folder):'
\echo '  - EDI-850-SAMPLE-*.txt (Purchase Orders)'
\echo '  - EDI-856-SAMPLE-*.txt (ASN/Ship Notice)'
\echo ''

-- ═══════════════════════════════════════════════════════════════
-- VERIFICATION QUERY
-- ═══════════════════════════════════════════════════════════════

SELECT 'Data Summary' as report;
SELECT 'STORER' as entity, COUNT(*) as count FROM storer
UNION ALL SELECT 'SKU', COUNT(*) FROM sku
UNION ALL SELECT 'LOC', COUNT(*) FROM loc
UNION ALL SELECT 'PO', COUNT(*) FROM po
UNION ALL SELECT 'RECEIPT', COUNT(*) FROM receipt
UNION ALL SELECT 'LOTXLOCXID', COUNT(*) FROM lotxlocxid
UNION ALL SELECT 'TASK', COUNT(*) FROM task
UNION ALL SELECT 'WMSUSER', COUNT(*) FROM wmsuser
UNION ALL SELECT 'ORDERS', COUNT(*) FROM orders
UNION ALL SELECT 'JOBCONFIGURATION', COUNT(*) FROM jobconfiguration;

-- ═══════════════════════════════════════════════════════════════
-- END OF MASTER TEST DATA SETUP
-- ═══════════════════════════════════════════════════════════════
