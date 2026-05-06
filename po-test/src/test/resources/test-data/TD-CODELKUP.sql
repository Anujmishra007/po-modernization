-- ═══════════════════════════════════════════════════════════════════════════
-- TD-CODELKUP.sql - Code Lookup / Configuration Data
-- ═══════════════════════════════════════════════════════════════════════════
-- Purpose: Reference data for status codes, hold codes, etc.
-- Entities: CODELKUP
-- Used By: All flows (status validation, hold management)
-- ═══════════════════════════════════════════════════════════════════════════

SET search_path TO dbo, public;

-- ═══════════════════════════════════════════════════════════════════════════
-- Code Lookup Data
-- ═══════════════════════════════════════════════════════════════════════════

INSERT INTO codelkup (listname, code, description, shortdescription) VALUES
-- PO Status Codes
('POSTATUS', '0', 'Open - New PO awaiting processing', 'Open'),
('POSTATUS', '1', 'Verified - PO verified and ready', 'Verified'),
('POSTATUS', '5', 'In Progress - PO being received', 'In Progress'),
('POSTATUS', '6', 'Partially Received', 'Partial'),
('POSTATUS', '9', 'Closed - PO fully received', 'Closed'),
('POSTATUS', 'X', 'Cancelled - PO cancelled', 'Cancelled'),

-- Receipt Status Codes
('RECEIPTSTATUS', '0', 'New - Receipt created', 'New'),
('RECEIPTSTATUS', '1', 'Scheduled - Receipt scheduled for arrival', 'Scheduled'),
('RECEIPTSTATUS', '2', 'Arrived - Goods at dock', 'Arrived'),
('RECEIPTSTATUS', '3', 'Unloading - Goods being unloaded', 'Unloading'),
('RECEIPTSTATUS', '5', 'In Progress - Receipt being processed', 'In Progress'),
('RECEIPTSTATUS', '9', 'Finalized - Receipt finalized', 'Finalized'),
('RECEIPTSTATUS', 'X', 'Cancelled - Receipt cancelled', 'Cancelled'),

-- Task Status Codes
('TASKSTATUS', '0', 'Created - Task created', 'Created'),
('TASKSTATUS', '1', 'Released - Task released for work', 'Released'),
('TASKSTATUS', '2', 'Assigned - Task assigned to worker', 'Assigned'),
('TASKSTATUS', '5', 'In Progress - Task being executed', 'In Progress'),
('TASKSTATUS', '9', 'Completed - Task completed', 'Completed'),
('TASKSTATUS', 'X', 'Cancelled - Task cancelled', 'Cancelled'),

-- Hold Codes
('HOLDCODE', 'QC', 'Quality Control Hold', 'QC'),
('HOLDCODE', 'DAMAGE', 'Damaged Goods Hold', 'Damage'),
('HOLDCODE', 'RECALL', 'Product Recall Hold', 'Recall'),
('HOLDCODE', 'CUSTOMS', 'Customs Clearance Hold', 'Customs'),
('HOLDCODE', 'INSPECT', 'Inspection Required Hold', 'Inspect'),
('HOLDCODE', 'HAZMAT', 'Hazmat Processing Hold', 'Hazmat'),
('HOLDCODE', 'EXPIRY', 'Near Expiry Hold', 'Expiry'),
('HOLDCODE', 'GST', 'GST Documentation Hold', 'GST'),

-- Location Types
('LOCTYPE', 'RECV', 'Receiving Location', 'Receiving'),
('LOCTYPE', 'STAGE', 'Staging Location', 'Staging'),
('LOCTYPE', 'STORAGE', 'Storage Location', 'Storage'),
('LOCTYPE', 'XDOCK', 'Cross-Dock Location', 'Cross-Dock'),
('LOCTYPE', 'HAZMAT', 'Hazmat Storage Location', 'Hazmat'),
('LOCTYPE', 'COLD', 'Cold Storage Location', 'Cold'),
('LOCTYPE', 'PICK', 'Pick Location', 'Pick'),
('LOCTYPE', 'SHIP', 'Shipping Location', 'Shipping'),

-- Location Flags
('LOCFLAG', 'AVAILABLE', 'Location available for use', 'Available'),
('LOCFLAG', 'FULL', 'Location at capacity', 'Full'),
('LOCFLAG', 'HOLD', 'Location on hold', 'Hold'),
('LOCFLAG', 'DAMAGED', 'Location damaged', 'Damaged'),
('LOCFLAG', 'DISABLED', 'Location disabled', 'Disabled'),
('LOCFLAG', 'RESERVED', 'Location reserved', 'Reserved'),

-- Storer Types
('STORERTYPE', '1', 'Standard Storer', 'Standard'),
('STORERTYPE', '2', 'VAS Provider', 'VAS'),
('STORERTYPE', '3', 'Cross-Dock Only', 'Cross-Dock'),
('STORERTYPE', '9', 'Internal/System', 'Internal'),

-- Country Codes
('COUNTRY', 'KR', 'Korea', 'KR'),
('COUNTRY', 'IN', 'India', 'IN'),
('COUNTRY', 'SG', 'Singapore', 'SG'),
('COUNTRY', 'JP', 'Japan', 'JP'),
('COUNTRY', 'CN', 'China', 'CN'),
('COUNTRY', 'US', 'United States', 'US'),
('COUNTRY', 'VN', 'Vietnam', 'VN'),

-- Region Codes
('REGION', 'ASIA-KR', 'Asia - Korea', 'Korea'),
('REGION', 'ASIA-IN', 'Asia - India', 'India'),
('REGION', 'ASIA-SG', 'Asia - Singapore', 'Singapore'),
('REGION', 'ASIA-JP', 'Asia - Japan', 'Japan'),
('REGION', 'ASIA-CN', 'Asia - China', 'China'),
('REGION', 'US', 'United States', 'US'),
('REGION', 'EU', 'Europe', 'EU'),

-- UOM Codes
('UOM', 'EA', 'Each', 'Each'),
('UOM', 'CS', 'Case', 'Case'),
('UOM', 'PLT', 'Pallet', 'Pallet'),
('UOM', 'KG', 'Kilogram', 'KG'),
('UOM', 'LB', 'Pound', 'LB'),
('UOM', 'M3', 'Cubic Meter', 'M3'),

-- Allocation Strategy
('ALLOCSTRAT', 'FIFO', 'First In First Out', 'FIFO'),
('ALLOCSTRAT', 'LIFO', 'Last In First Out', 'LIFO'),
('ALLOCSTRAT', 'FEFO', 'First Expiry First Out', 'FEFO'),
('ALLOCSTRAT', 'PICK', 'Pick Location Priority', 'Pick'),

-- Putaway Strategy
('PUTAWAYSTRAT', 'DIRECTED', 'System Directed Putaway', 'Directed'),
('PUTAWAYSTRAT', 'MANUAL', 'Manual Location Selection', 'Manual'),
('PUTAWAYSTRAT', 'ZONE', 'Zone-Based Putaway', 'Zone'),
('PUTAWAYSTRAT', 'VELOCITY', 'Velocity-Based Putaway', 'Velocity')

ON CONFLICT (listname, code) DO NOTHING;

-- ═══════════════════════════════════════════════════════════════════════════
-- Verification
-- ═══════════════════════════════════════════════════════════════════════════

DO $$
BEGIN
    RAISE NOTICE 'TD-CODELKUP Data Loaded:';
    RAISE NOTICE '  Total Code Lookups: %', (SELECT COUNT(*) FROM codelkup);
    RAISE NOTICE '  By List:';
    RAISE NOTICE '    PO Status: %', (SELECT COUNT(*) FROM codelkup WHERE listname = 'POSTATUS');
    RAISE NOTICE '    Receipt Status: %', (SELECT COUNT(*) FROM codelkup WHERE listname = 'RECEIPTSTATUS');
    RAISE NOTICE '    Hold Codes: %', (SELECT COUNT(*) FROM codelkup WHERE listname = 'HOLDCODE');
    RAISE NOTICE '    Location Types: %', (SELECT COUNT(*) FROM codelkup WHERE listname = 'LOCTYPE');
END $$;
