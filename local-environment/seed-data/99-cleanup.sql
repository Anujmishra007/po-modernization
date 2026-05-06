-- PO Modernization Test Data Cleanup
-- Run this to reset test data between test runs
-- Usage: psql -h localhost -p 5433 -U wms -d po_test -f 99-cleanup.sql

SET search_path TO dbo, public;

-- ═══════════════════════════════════════════════════════════
-- Delete in reverse dependency order
-- ═══════════════════════════════════════════════════════════

-- Delete transactional data
TRUNCATE TABLE inventoryhold CASCADE;
TRUNCATE TABLE lotxlocxid CASCADE;
TRUNCATE TABLE task CASCADE;
TRUNCATE TABLE receiptdetail CASCADE;
TRUNCATE TABLE receipt CASCADE;
TRUNCATE TABLE orderdetail CASCADE;
TRUNCATE TABLE orders CASCADE;

-- Reset sequences
ALTER SEQUENCE seq_orderkey RESTART WITH 1000000;
ALTER SEQUENCE seq_receiptkey RESTART WITH 2000000;
ALTER SEQUENCE seq_lotxlocxidkey RESTART WITH 3000000;
ALTER SEQUENCE seq_taskkey RESTART WITH 4000000;

-- Verify cleanup
DO $$
BEGIN
    RAISE NOTICE 'Cleanup Complete:';
    RAISE NOTICE '  Orders: %', (SELECT COUNT(*) FROM orders);
    RAISE NOTICE '  Receipts: %', (SELECT COUNT(*) FROM receipt);
    RAISE NOTICE '  Inventory: %', (SELECT COUNT(*) FROM lotxlocxid);
    RAISE NOTICE '  Tasks: %', (SELECT COUNT(*) FROM task);
END $$;

-- Re-run test data
\i /docker-entrypoint-initdb.d/01-test-data.sql
