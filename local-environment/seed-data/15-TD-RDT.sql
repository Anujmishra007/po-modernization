-- =============================================================================
-- TD-RDT.sql
-- RDT (RF Device Terminal) Test Data for PO Modernization E2E Testing
-- =============================================================================
-- Entry Point: RDT API (65 Test Cases)
-- Covers: F1-TC25, F2-TC05, F3-TC04, F6-TC05
-- =============================================================================

SET search_path TO dbo, public;

-- =============================================================================
-- SECTION 1: USER GROUPS (Roles) - Must be created before users
-- =============================================================================

DELETE FROM usergroup WHERE usergroupkey IN ('RECEIVER', 'PUTAWAY', 'PICKER', 'SUPERVISOR', 'AUDITOR');

INSERT INTO usergroup (usergroupkey, description, status) VALUES
('RECEIVER', 'Receiving Operators', '1'),
('PUTAWAY', 'Putaway Operators', '1'),
('PICKER', 'Picking Operators', '1'),
('SUPERVISOR', 'Floor Supervisors', '1'),
('AUDITOR', 'Inventory Auditors', '1')
ON CONFLICT (usergroupkey) DO NOTHING;


-- =============================================================================
-- SECTION 2: WMS USERS (Operators)
-- =============================================================================
-- Users who operate RDT devices for receiving, putaway, picking

DELETE FROM wmsuser WHERE userid LIKE 'RDT-%' OR userid LIKE 'TEST-%';

INSERT INTO wmsuser (userid, username, password, usergroup, facility, status, language, defaultmenu, adddate, addwho) VALUES
-- Standard RDT operators
('RDT-OPR-001', 'John Smith', 'hashed_pwd_001', 'RECEIVER', 'KR01', '1', 'EN', 'RECV_MENU', CURRENT_TIMESTAMP, 'SYSTEM'),
('RDT-OPR-002', 'Kim Min-Jun', 'hashed_pwd_002', 'RECEIVER', 'KR01', '1', 'KO', 'RECV_MENU', CURRENT_TIMESTAMP, 'SYSTEM'),
('RDT-OPR-003', 'Raj Patel', 'hashed_pwd_003', 'RECEIVER', 'IN01', '1', 'EN', 'RECV_MENU', CURRENT_TIMESTAMP, 'SYSTEM'),
('RDT-OPR-004', 'Lee Soo-Yeon', 'hashed_pwd_004', 'PUTAWAY', 'KR01', '1', 'KO', 'PA_MENU', CURRENT_TIMESTAMP, 'SYSTEM'),
('RDT-OPR-005', 'Ananya Sharma', 'hashed_pwd_005', 'PUTAWAY', 'IN01', '1', 'EN', 'PA_MENU', CURRENT_TIMESTAMP, 'SYSTEM'),
('RDT-OPR-006', 'Park Ji-Hoon', 'hashed_pwd_006', 'PICKER', 'KR02', '1', 'KO', 'PICK_MENU', CURRENT_TIMESTAMP, 'SYSTEM'),
('RDT-OPR-007', 'Priya Singh', 'hashed_pwd_007', 'PICKER', 'IN02', '1', 'EN', 'PICK_MENU', CURRENT_TIMESTAMP, 'SYSTEM'),
-- Supervisors
('RDT-SUP-001', 'Manager Kim', 'hashed_pwd_sup1', 'SUPERVISOR', 'KR01', '1', 'KO', 'SUPER_MENU', CURRENT_TIMESTAMP, 'SYSTEM'),
('RDT-SUP-002', 'Manager Patel', 'hashed_pwd_sup2', 'SUPERVISOR', 'IN01', '1', 'EN', 'SUPER_MENU', CURRENT_TIMESTAMP, 'SYSTEM'),
-- Error test users
('RDT-INACTIVE', 'Inactive User', 'hashed_pwd_x', 'RECEIVER', 'KR01', '0', 'EN', 'RECV_MENU', CURRENT_TIMESTAMP, 'SYSTEM'),
('RDT-LOCKED', 'Locked User', 'hashed_pwd_x', 'RECEIVER', 'KR01', '9', 'EN', 'RECV_MENU', CURRENT_TIMESTAMP, 'SYSTEM');


-- =============================================================================
-- SECTION 3: RDT DEVICES
-- =============================================================================

DELETE FROM device WHERE deviceid LIKE 'RDT-%' OR deviceid LIKE 'TEST-%';

INSERT INTO device (deviceid, devicename, devicetype, facility, status, ipaddress, macaddress, lastactivity, adddate, addwho) VALUES
-- Korea facility devices
('RDT-KR01-001', 'Korea Dock 1 Scanner', 'HANDHELD', 'KR01', '1', '192.168.1.101', 'AA:BB:CC:DD:EE:01', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 'SYSTEM'),
('RDT-KR01-002', 'Korea Dock 2 Scanner', 'HANDHELD', 'KR01', '1', '192.168.1.102', 'AA:BB:CC:DD:EE:02', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 'SYSTEM'),
('RDT-KR01-003', 'Korea Storage Scanner', 'HANDHELD', 'KR01', '1', '192.168.1.103', 'AA:BB:CC:DD:EE:03', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 'SYSTEM'),
('RDT-KR02-001', 'Korea DC2 Scanner', 'HANDHELD', 'KR02', '1', '192.168.2.101', 'AA:BB:CC:DD:EE:04', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 'SYSTEM'),
-- India facility devices
('RDT-IN01-001', 'India Dock Scanner', 'HANDHELD', 'IN01', '1', '192.168.3.101', 'AA:BB:CC:DD:EE:05', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 'SYSTEM'),
('RDT-IN01-002', 'India Storage Scanner', 'HANDHELD', 'IN01', '1', '192.168.3.102', 'AA:BB:CC:DD:EE:06', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 'SYSTEM'),
-- Error test devices
('RDT-OFFLINE', 'Offline Device', 'HANDHELD', 'KR01', '0', '192.168.1.200', 'AA:BB:CC:DD:EE:99', CURRENT_TIMESTAMP - INTERVAL '7 days', CURRENT_TIMESTAMP, 'SYSTEM'),
('RDT-MAINT', 'Maintenance Device', 'HANDHELD', 'KR01', '5', '192.168.1.201', 'AA:BB:CC:DD:EE:98', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 'SYSTEM');


-- =============================================================================
-- SECTION 4: RDT MENUS (Screen Configuration)
-- =============================================================================

DELETE FROM rdtmenu WHERE menukey LIKE '%_MENU';

INSERT INTO rdtmenu (menukey, menudesc, parentmenu, menuorder, screenid, accesslevel, status) VALUES
-- Main menus
('MAIN_MENU', 'Main Menu', NULL, 0, 'SCR_MAIN', 0, '1'),
-- Receiving menus
('RECV_MENU', 'Receiving Menu', 'MAIN_MENU', 1, 'SCR_RECV_MAIN', 1, '1'),
('RECV_ASN', 'Receive ASN', 'RECV_MENU', 1, 'SCR_RECV_ASN', 1, '1'),
('RECV_PO', 'Receive PO', 'RECV_MENU', 2, 'SCR_RECV_PO', 1, '1'),
('RECV_BLIND', 'Blind Receipt', 'RECV_MENU', 3, 'SCR_RECV_BLIND', 2, '1'),
('RECV_FINALIZE', 'Finalize Receipt', 'RECV_MENU', 4, 'SCR_RECV_FIN', 1, '1'),
-- Putaway menus
('PA_MENU', 'Putaway Menu', 'MAIN_MENU', 2, 'SCR_PA_MAIN', 1, '1'),
('PA_DIRECTED', 'Directed Putaway', 'PA_MENU', 1, 'SCR_PA_DIR', 1, '1'),
('PA_OVERRIDE', 'Override Location', 'PA_MENU', 2, 'SCR_PA_OVER', 2, '1'),
('PA_CONFIRM', 'Confirm Putaway', 'PA_MENU', 3, 'SCR_PA_CONF', 1, '1'),
-- Picking menus
('PICK_MENU', 'Picking Menu', 'MAIN_MENU', 3, 'SCR_PICK_MAIN', 1, '1'),
('PICK_ORDER', 'Pick by Order', 'PICK_MENU', 1, 'SCR_PICK_ORD', 1, '1'),
('PICK_BATCH', 'Batch Picking', 'PICK_MENU', 2, 'SCR_PICK_BAT', 1, '1'),
-- Supervisor menus
('SUPER_MENU', 'Supervisor Menu', 'MAIN_MENU', 9, 'SCR_SUPER', 3, '1'),
('SUPER_OVERRIDE', 'Override Functions', 'SUPER_MENU', 1, 'SCR_OVERRIDE', 3, '1');


-- =============================================================================
-- SECTION 5: RDT-SPECIFIC POs (For RDT Receipt Testing)
-- =============================================================================

DELETE FROM po WHERE pokey LIKE 'RDT-PO-%';
DELETE FROM podetail WHERE pokey LIKE 'RDT-PO-%';

-- RDT PO: Standard receive via RDT
INSERT INTO po (pokey, storerkey, externpokey, potype, status, facility, expecteddate, adddate, addwho) VALUES
('RDT-PO-001', 'NIKE_KR', 'RDT-EXT-001', 'STANDARD', '0', 'KR01', CURRENT_TIMESTAMP + INTERVAL '3 days', CURRENT_TIMESTAMP, 'SYSTEM'),
('RDT-PO-002', 'HM_KR', 'RDT-EXT-002', 'STANDARD', '0', 'KR02', CURRENT_TIMESTAMP + INTERVAL '2 days', CURRENT_TIMESTAMP, 'SYSTEM'),
('RDT-PO-003', 'ADIDAS_IN', 'RDT-EXT-003', 'STANDARD', '0', 'IN01', CURRENT_TIMESTAMP + INTERVAL '5 days', CURRENT_TIMESTAMP, 'SYSTEM');

-- PO details for RDT receiving
INSERT INTO podetail (pokey, polinenumber, storerkey, sku, qtyordered, qtyreceived, unitprice, status, adddate, addwho) VALUES
-- RDT-PO-001 (Nike)
('RDT-PO-001', '00001', 'NIKE_KR', 'NK-AIRMAX90-BLK', 50, 0, 89.99, '0', CURRENT_TIMESTAMP, 'SYSTEM'),
('RDT-PO-001', '00002', 'NIKE_KR', 'NK-AIRMAX90-WHT', 50, 0, 89.99, '0', CURRENT_TIMESTAMP, 'SYSTEM'),
-- RDT-PO-002 (H&M)
('RDT-PO-002', '00001', 'HM_KR', 'HM-BASIC-TEE-M', 200, 0, 4.99, '0', CURRENT_TIMESTAMP, 'SYSTEM'),
('RDT-PO-002', '00002', 'HM_KR', 'HM-SLIM-JEANS-32', 100, 0, 29.99, '0', CURRENT_TIMESTAMP, 'SYSTEM'),
-- RDT-PO-003 (Adidas)
('RDT-PO-003', '00001', 'ADIDAS_IN', 'ADI-ULTRABOOST-BLK', 75, 0, 149.99, '0', CURRENT_TIMESTAMP, 'SYSTEM');


-- =============================================================================
-- SECTION 6: RDT-SPECIFIC RECEIPTS (For RDT Finalize Testing)
-- =============================================================================

DELETE FROM receipt WHERE receiptkey LIKE 'RDT-RCV-%';
DELETE FROM receiptdetail WHERE receiptkey LIKE 'RDT-RCV-%';

-- Receipt ready for RDT finalization
INSERT INTO receipt (receiptkey, pokey, storerkey, status, type, facility, receiptdate, adddate, addwho) VALUES
('RDT-RCV-001', 'RDT-PO-001', 'NIKE_KR', '5', 'Normal', 'KR01', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 'SYSTEM'),
('RDT-RCV-002', 'RDT-PO-002', 'HM_KR', '5', 'Normal', 'KR02', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 'SYSTEM');

INSERT INTO receiptdetail (receiptkey, receiptlinenumber, pokey, polinenumber, storerkey, sku, qtyexpected, qtyreceived, status, toloc, toid, adddate, addwho) VALUES
-- RDT-RCV-001
('RDT-RCV-001', '00001', 'RDT-PO-001', '00001', 'NIKE_KR', 'NK-AIRMAX90-BLK', 50, 50, '5', 'KR01-RECV-01', 'LP-RDT-001', CURRENT_TIMESTAMP, 'SYSTEM'),
('RDT-RCV-001', '00002', 'RDT-PO-001', '00002', 'NIKE_KR', 'NK-AIRMAX90-WHT', 50, 50, '5', 'KR01-RECV-01', 'LP-RDT-002', CURRENT_TIMESTAMP, 'SYSTEM'),
-- RDT-RCV-002
('RDT-RCV-002', '00001', 'RDT-PO-002', '00001', 'HM_KR', 'HM-BASIC-TEE-M', 200, 200, '5', 'KR02-RECV-01', 'LP-RDT-003', CURRENT_TIMESTAMP, 'SYSTEM'),
('RDT-RCV-002', '00002', 'RDT-PO-002', '00002', 'HM_KR', 'HM-SLIM-JEANS-32', 100, 100, '5', 'KR02-RECV-01', 'LP-RDT-004', CURRENT_TIMESTAMP, 'SYSTEM');


-- =============================================================================
-- SECTION 7: RDT SESSION DATA
-- =============================================================================

DELETE FROM rdtsession WHERE sessionid LIKE 'SES-RDT-%';

-- Active RDT sessions for testing
INSERT INTO rdtsession (sessionid, userid, deviceid, facility, status, logintime, lastactivity, currentmenu, currentscreen) VALUES
('SES-RDT-001', 'RDT-OPR-001', 'RDT-KR01-001', 'KR01', 'ACTIVE', CURRENT_TIMESTAMP - INTERVAL '1 hour', CURRENT_TIMESTAMP, 'RECV_MENU', 'SCR_RECV_ASN'),
('SES-RDT-002', 'RDT-OPR-004', 'RDT-KR01-003', 'KR01', 'ACTIVE', CURRENT_TIMESTAMP - INTERVAL '30 minutes', CURRENT_TIMESTAMP, 'PA_MENU', 'SCR_PA_DIR'),
('SES-RDT-003', 'RDT-OPR-003', 'RDT-IN01-001', 'IN01', 'ACTIVE', CURRENT_TIMESTAMP - INTERVAL '2 hours', CURRENT_TIMESTAMP, 'RECV_MENU', 'SCR_RECV_PO');


-- =============================================================================
-- SECTION 8: RDT USER PERMISSIONS
-- =============================================================================

DELETE FROM userpermission WHERE userid LIKE 'RDT-%';

INSERT INTO userpermission (userid, permissionkey, granted, adddate, addwho) VALUES
-- Receiver permissions
('RDT-OPR-001', 'RECV_ASN', 'Y', CURRENT_TIMESTAMP, 'SYSTEM'),
('RDT-OPR-001', 'RECV_PO', 'Y', CURRENT_TIMESTAMP, 'SYSTEM'),
('RDT-OPR-001', 'RECV_FINALIZE', 'Y', CURRENT_TIMESTAMP, 'SYSTEM'),
('RDT-OPR-002', 'RECV_ASN', 'Y', CURRENT_TIMESTAMP, 'SYSTEM'),
('RDT-OPR-002', 'RECV_PO', 'Y', CURRENT_TIMESTAMP, 'SYSTEM'),
('RDT-OPR-002', 'RECV_FINALIZE', 'Y', CURRENT_TIMESTAMP, 'SYSTEM'),
-- Putaway permissions
('RDT-OPR-004', 'PA_DIRECTED', 'Y', CURRENT_TIMESTAMP, 'SYSTEM'),
('RDT-OPR-004', 'PA_CONFIRM', 'Y', CURRENT_TIMESTAMP, 'SYSTEM'),
('RDT-OPR-005', 'PA_DIRECTED', 'Y', CURRENT_TIMESTAMP, 'SYSTEM'),
('RDT-OPR-005', 'PA_CONFIRM', 'Y', CURRENT_TIMESTAMP, 'SYSTEM'),
-- Supervisor permissions (all)
('RDT-SUP-001', 'RECV_ASN', 'Y', CURRENT_TIMESTAMP, 'SYSTEM'),
('RDT-SUP-001', 'RECV_PO', 'Y', CURRENT_TIMESTAMP, 'SYSTEM'),
('RDT-SUP-001', 'RECV_FINALIZE', 'Y', CURRENT_TIMESTAMP, 'SYSTEM'),
('RDT-SUP-001', 'RECV_BLIND', 'Y', CURRENT_TIMESTAMP, 'SYSTEM'),
('RDT-SUP-001', 'PA_DIRECTED', 'Y', CURRENT_TIMESTAMP, 'SYSTEM'),
('RDT-SUP-001', 'PA_OVERRIDE', 'Y', CURRENT_TIMESTAMP, 'SYSTEM'),
('RDT-SUP-001', 'SUPER_OVERRIDE', 'Y', CURRENT_TIMESTAMP, 'SYSTEM');


-- =============================================================================
-- VERIFICATION
-- =============================================================================

SELECT 'WMSUSER' as entity, COUNT(*) as count FROM wmsuser WHERE userid LIKE 'RDT-%'
UNION ALL
SELECT 'DEVICE', COUNT(*) FROM device WHERE deviceid LIKE 'RDT-%'
UNION ALL
SELECT 'RDTMENU', COUNT(*) FROM rdtmenu
UNION ALL
SELECT 'RDT_PO', COUNT(*) FROM po WHERE pokey LIKE 'RDT-PO-%'
UNION ALL
SELECT 'RDT_RECEIPT', COUNT(*) FROM receipt WHERE receiptkey LIKE 'RDT-RCV-%'
UNION ALL
SELECT 'RDTSESSION', COUNT(*) FROM rdtsession WHERE sessionid LIKE 'SES-RDT-%';

-- =============================================================================
-- END OF RDT TEST DATA
-- =============================================================================
