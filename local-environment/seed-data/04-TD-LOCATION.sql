-- ═══════════════════════════════════════════════════════════════════════════
-- TD-LOCATION.sql - Location Master Data + Zones
-- ═══════════════════════════════════════════════════════════════════════════
-- Test Data Set: TD-LOCATION
-- Required: 15 Locations per facility
-- Entities: LOC, ZONE (putaway zones)
-- Used By: F2-F6 (ASN, Receipt, Cross-Dock, Lottable, Putaway)
-- ═══════════════════════════════════════════════════════════════════════════

SET search_path TO dbo, public;

-- ═══════════════════════════════════════════════════════════════════════════
-- Putaway Zones
-- ═══════════════════════════════════════════════════════════════════════════

-- Create PUTAWAYZONE table if not exists
CREATE TABLE IF NOT EXISTS putawayzone (
    facility VARCHAR(20) NOT NULL,
    putawayzone VARCHAR(20) NOT NULL,
    descr VARCHAR(100),
    priority INT DEFAULT 1,
    status VARCHAR(5) DEFAULT '1',
    PRIMARY KEY (facility, putawayzone)
);

INSERT INTO putawayzone (facility, putawayzone, descr, priority, status) VALUES
-- Korea DC 1 Zones
('KR01', 'RECEIVING', 'Receiving Dock Area', 1, '1'),
('KR01', 'STAGING', 'Staging Area', 2, '1'),
('KR01', 'ZONE-A', 'Storage Zone A - Footwear', 3, '1'),
('KR01', 'ZONE-B', 'Storage Zone B - Apparel', 4, '1'),
('KR01', 'ZONE-C', 'Storage Zone C - General', 5, '1'),
('KR01', 'CROSSDOCK', 'Cross-Dock Area', 6, '1'),
('KR01', 'HAZMAT', 'Hazmat Storage', 7, '1'),
('KR01', 'COLDSTORE', 'Cold Storage', 8, '1'),

-- Korea DC 2 Zones
('KR02', 'RECEIVING', 'Receiving Dock Area', 1, '1'),
('KR02', 'STAGING', 'Staging Area', 2, '1'),
('KR02', 'ZONE-A', 'Storage Zone A', 3, '1'),
('KR02', 'ZONE-B', 'Storage Zone B', 4, '1'),
('KR02', 'CROSSDOCK', 'Cross-Dock Area', 5, '1'),

-- India DC 1 Zones
('IN01', 'RECEIVING', 'Receiving Dock Area', 1, '1'),
('IN01', 'STAGING', 'Staging Area', 2, '1'),
('IN01', 'ZONE-A', 'Storage Zone A', 3, '1'),
('IN01', 'ZONE-B', 'Storage Zone B', 4, '1'),
('IN01', 'CROSSDOCK', 'Cross-Dock Area', 5, '1'),

-- Singapore DC 1 Zones
('SG01', 'RECEIVING', 'Receiving Dock Area', 1, '1'),
('SG01', 'STAGING', 'Staging Area', 2, '1'),
('SG01', 'ZONE-A', 'Storage Zone A', 3, '1'),
('SG01', 'ZONE-B', 'Storage Zone B', 4, '1'),
('SG01', 'CROSSDOCK', 'Cross-Dock Area', 5, '1'),

-- Test Facility Zones
('TEST01', 'RECEIVING', 'Test Receiving', 1, '1'),
('TEST01', 'STAGING', 'Test Staging', 2, '1'),
('TEST01', 'ZONE-A', 'Test Zone A', 3, '1'),
('TEST01', 'ZONE-B', 'Test Zone B', 4, '1'),
('TEST01', 'CROSSDOCK', 'Test Cross-Dock', 5, '1'),
('TEST01', 'HAZMAT', 'Test Hazmat', 6, '1'),
('TEST01', 'ERROR-ZONE', 'Error Test Zone', 7, '9')  -- Inactive
ON CONFLICT (facility, putawayzone) DO NOTHING;

-- ═══════════════════════════════════════════════════════════════════════════
-- Location Master (15+ Locations per facility)
-- ═══════════════════════════════════════════════════════════════════════════

INSERT INTO loc (loc, facility, loctype, putawayzone, locationflag, status, maxweight, maxcube) VALUES
-- Korea DC 1 - Receiving Locations (4)
('KR01-RECV-01', 'KR01', 'RECV', 'RECEIVING', 'AVAILABLE', '1', 5000, 100),
('KR01-RECV-02', 'KR01', 'RECV', 'RECEIVING', 'AVAILABLE', '1', 5000, 100),
('KR01-RECV-03', 'KR01', 'RECV', 'RECEIVING', 'AVAILABLE', '1', 5000, 100),
('KR01-RECV-04', 'KR01', 'RECV', 'RECEIVING', 'AVAILABLE', '1', 5000, 100),

-- Korea DC 1 - Staging Locations (4)
('KR01-STAGE-01', 'KR01', 'STAGE', 'STAGING', 'AVAILABLE', '1', 3000, 50),
('KR01-STAGE-02', 'KR01', 'STAGE', 'STAGING', 'AVAILABLE', '1', 3000, 50),
('KR01-STAGE-03', 'KR01', 'STAGE', 'STAGING', 'AVAILABLE', '1', 3000, 50),
('KR01-STAGE-04', 'KR01', 'STAGE', 'STAGING', 'AVAILABLE', '1', 3000, 50),

-- Korea DC 1 - Zone A Storage (Footwear) (6)
('KR01-A-01-01', 'KR01', 'STORAGE', 'ZONE-A', 'AVAILABLE', '1', 1000, 20),
('KR01-A-01-02', 'KR01', 'STORAGE', 'ZONE-A', 'AVAILABLE', '1', 1000, 20),
('KR01-A-02-01', 'KR01', 'STORAGE', 'ZONE-A', 'AVAILABLE', '1', 1000, 20),
('KR01-A-02-02', 'KR01', 'STORAGE', 'ZONE-A', 'AVAILABLE', '1', 1000, 20),
('KR01-A-03-01', 'KR01', 'STORAGE', 'ZONE-A', 'AVAILABLE', '1', 1000, 20),
('KR01-A-03-02', 'KR01', 'STORAGE', 'ZONE-A', 'AVAILABLE', '1', 1000, 20),

-- Korea DC 1 - Zone B Storage (Apparel) (6)
('KR01-B-01-01', 'KR01', 'STORAGE', 'ZONE-B', 'AVAILABLE', '1', 800, 30),
('KR01-B-01-02', 'KR01', 'STORAGE', 'ZONE-B', 'AVAILABLE', '1', 800, 30),
('KR01-B-02-01', 'KR01', 'STORAGE', 'ZONE-B', 'AVAILABLE', '1', 800, 30),
('KR01-B-02-02', 'KR01', 'STORAGE', 'ZONE-B', 'AVAILABLE', '1', 800, 30),
('KR01-B-03-01', 'KR01', 'STORAGE', 'ZONE-B', 'AVAILABLE', '1', 800, 30),
('KR01-B-03-02', 'KR01', 'STORAGE', 'ZONE-B', 'AVAILABLE', '1', 800, 30),

-- Korea DC 1 - Cross-Dock Locations (4)
('KR01-XDOCK-01', 'KR01', 'XDOCK', 'CROSSDOCK', 'AVAILABLE', '1', 2000, 40),
('KR01-XDOCK-02', 'KR01', 'XDOCK', 'CROSSDOCK', 'AVAILABLE', '1', 2000, 40),
('KR01-XDOCK-03', 'KR01', 'XDOCK', 'CROSSDOCK', 'AVAILABLE', '1', 2000, 40),
('KR01-XDOCK-04', 'KR01', 'XDOCK', 'CROSSDOCK', 'AVAILABLE', '1', 2000, 40),

-- Korea DC 1 - Hazmat (2)
('KR01-HAZMAT-01', 'KR01', 'HAZMAT', 'HAZMAT', 'AVAILABLE', '1', 500, 10),
('KR01-HAZMAT-02', 'KR01', 'HAZMAT', 'HAZMAT', 'AVAILABLE', '1', 500, 10),

-- India DC 1 - Core Locations (15)
('IN01-RECV-01', 'IN01', 'RECV', 'RECEIVING', 'AVAILABLE', '1', 5000, 100),
('IN01-RECV-02', 'IN01', 'RECV', 'RECEIVING', 'AVAILABLE', '1', 5000, 100),
('IN01-STAGE-01', 'IN01', 'STAGE', 'STAGING', 'AVAILABLE', '1', 3000, 50),
('IN01-STAGE-02', 'IN01', 'STAGE', 'STAGING', 'AVAILABLE', '1', 3000, 50),
('IN01-A-01-01', 'IN01', 'STORAGE', 'ZONE-A', 'AVAILABLE', '1', 1000, 20),
('IN01-A-01-02', 'IN01', 'STORAGE', 'ZONE-A', 'AVAILABLE', '1', 1000, 20),
('IN01-A-02-01', 'IN01', 'STORAGE', 'ZONE-A', 'AVAILABLE', '1', 1000, 20),
('IN01-A-02-02', 'IN01', 'STORAGE', 'ZONE-A', 'AVAILABLE', '1', 1000, 20),
('IN01-B-01-01', 'IN01', 'STORAGE', 'ZONE-B', 'AVAILABLE', '1', 800, 30),
('IN01-B-01-02', 'IN01', 'STORAGE', 'ZONE-B', 'AVAILABLE', '1', 800, 30),
('IN01-B-02-01', 'IN01', 'STORAGE', 'ZONE-B', 'AVAILABLE', '1', 800, 30),
('IN01-B-02-02', 'IN01', 'STORAGE', 'ZONE-B', 'AVAILABLE', '1', 800, 30),
('IN01-XDOCK-01', 'IN01', 'XDOCK', 'CROSSDOCK', 'AVAILABLE', '1', 2000, 40),
('IN01-XDOCK-02', 'IN01', 'XDOCK', 'CROSSDOCK', 'AVAILABLE', '1', 2000, 40),
('IN01-XDOCK-03', 'IN01', 'XDOCK', 'CROSSDOCK', 'AVAILABLE', '1', 2000, 40),

-- Singapore DC 1 - Core Locations (15)
('SG01-RECV-01', 'SG01', 'RECV', 'RECEIVING', 'AVAILABLE', '1', 5000, 100),
('SG01-RECV-02', 'SG01', 'RECV', 'RECEIVING', 'AVAILABLE', '1', 5000, 100),
('SG01-STAGE-01', 'SG01', 'STAGE', 'STAGING', 'AVAILABLE', '1', 3000, 50),
('SG01-STAGE-02', 'SG01', 'STAGE', 'STAGING', 'AVAILABLE', '1', 3000, 50),
('SG01-A-01-01', 'SG01', 'STORAGE', 'ZONE-A', 'AVAILABLE', '1', 1000, 20),
('SG01-A-01-02', 'SG01', 'STORAGE', 'ZONE-A', 'AVAILABLE', '1', 1000, 20),
('SG01-A-02-01', 'SG01', 'STORAGE', 'ZONE-A', 'AVAILABLE', '1', 1000, 20),
('SG01-A-02-02', 'SG01', 'STORAGE', 'ZONE-A', 'AVAILABLE', '1', 1000, 20),
('SG01-B-01-01', 'SG01', 'STORAGE', 'ZONE-B', 'AVAILABLE', '1', 800, 30),
('SG01-B-01-02', 'SG01', 'STORAGE', 'ZONE-B', 'AVAILABLE', '1', 800, 30),
('SG01-B-02-01', 'SG01', 'STORAGE', 'ZONE-B', 'AVAILABLE', '1', 800, 30),
('SG01-B-02-02', 'SG01', 'STORAGE', 'ZONE-B', 'AVAILABLE', '1', 800, 30),
('SG01-XDOCK-01', 'SG01', 'XDOCK', 'CROSSDOCK', 'AVAILABLE', '1', 2000, 40),
('SG01-XDOCK-02', 'SG01', 'XDOCK', 'CROSSDOCK', 'AVAILABLE', '1', 2000, 40),
('SG01-XDOCK-03', 'SG01', 'XDOCK', 'CROSSDOCK', 'AVAILABLE', '1', 2000, 40),

-- Test Facility - Locations (20)
('TEST-RECV-01', 'TEST01', 'RECV', 'RECEIVING', 'AVAILABLE', '1', 5000, 100),
('TEST-RECV-02', 'TEST01', 'RECV', 'RECEIVING', 'AVAILABLE', '1', 5000, 100),
('TEST-STAGE-01', 'TEST01', 'STAGE', 'STAGING', 'AVAILABLE', '1', 3000, 50),
('TEST-STAGE-02', 'TEST01', 'STAGE', 'STAGING', 'AVAILABLE', '1', 3000, 50),
('TEST-LOC-01', 'TEST01', 'STORAGE', 'ZONE-A', 'AVAILABLE', '1', 1000, 20),
('TEST-LOC-02', 'TEST01', 'STORAGE', 'ZONE-A', 'AVAILABLE', '1', 1000, 20),
('TEST-LOC-03', 'TEST01', 'STORAGE', 'ZONE-A', 'AVAILABLE', '1', 1000, 20),
('TEST-LOC-04', 'TEST01', 'STORAGE', 'ZONE-B', 'AVAILABLE', '1', 1000, 20),
('TEST-LOC-05', 'TEST01', 'STORAGE', 'ZONE-B', 'AVAILABLE', '1', 1000, 20),
('TEST-LOC-06', 'TEST01', 'STORAGE', 'ZONE-B', 'AVAILABLE', '1', 1000, 20),
('TEST-XDOCK-01', 'TEST01', 'XDOCK', 'CROSSDOCK', 'AVAILABLE', '1', 2000, 40),
('TEST-XDOCK-02', 'TEST01', 'XDOCK', 'CROSSDOCK', 'AVAILABLE', '1', 2000, 40),
('HAZMAT-01', 'TEST01', 'HAZMAT', 'HAZMAT', 'AVAILABLE', '1', 500, 10),
('HAZMAT-02', 'TEST01', 'HAZMAT', 'HAZMAT', 'AVAILABLE', '1', 500, 10),
-- Error/Edge Case Locations
('TEST-LOC-FULL', 'TEST01', 'STORAGE', 'ZONE-A', 'FULL', '1', 1000, 20),
('TEST-LOC-HOLD', 'TEST01', 'STORAGE', 'ZONE-A', 'HOLD', '1', 1000, 20),
('TEST-LOC-DAMAGED', 'TEST01', 'STORAGE', 'ZONE-A', 'DAMAGED', '1', 1000, 20),
('TEST-LOC-ERR', 'TEST01', 'STORAGE', 'ZONE-A', 'DISABLED', '9', 1000, 20),
('TEST-LOC-NOZONE', 'TEST01', 'STORAGE', 'ERROR-ZONE', 'AVAILABLE', '1', 1000, 20),
('TEST-LOC-MAXWGT', 'TEST01', 'STORAGE', 'ZONE-A', 'AVAILABLE', '1', 1, 0.01)  -- Tiny capacity for edge tests
ON CONFLICT (loc) DO NOTHING;

-- ═══════════════════════════════════════════════════════════════════════════
-- Verification
-- ═══════════════════════════════════════════════════════════════════════════

DO $$
BEGIN
    RAISE NOTICE 'TD-LOCATION Data Loaded:';
    RAISE NOTICE '  Putaway Zones: %', (SELECT COUNT(*) FROM putawayzone);
    RAISE NOTICE '  Locations: %', (SELECT COUNT(*) FROM loc);
    RAISE NOTICE '  By Facility:';
    RAISE NOTICE '    KR01: %', (SELECT COUNT(*) FROM loc WHERE facility = 'KR01');
    RAISE NOTICE '    IN01: %', (SELECT COUNT(*) FROM loc WHERE facility = 'IN01');
    RAISE NOTICE '    SG01: %', (SELECT COUNT(*) FROM loc WHERE facility = 'SG01');
    RAISE NOTICE '    TEST01: %', (SELECT COUNT(*) FROM loc WHERE facility = 'TEST01');
END $$;
