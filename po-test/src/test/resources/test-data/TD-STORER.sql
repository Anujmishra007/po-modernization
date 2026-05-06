-- ═══════════════════════════════════════════════════════════════════════════
-- TD-STORER.sql - Storer Master Data + Addresses
-- ═══════════════════════════════════════════════════════════════════════════
-- Test Data Set: TD-STORER
-- Required: 5+ Storers (multi-region)
-- Entities: STORER, STORERADDRESS
-- Used By: F1-F10 (All flows)
-- ═══════════════════════════════════════════════════════════════════════════

SET search_path TO dbo, public;

-- ═══════════════════════════════════════════════════════════════════════════
-- Storer Master (15 Storers - Multi-Region Coverage)
-- ═══════════════════════════════════════════════════════════════════════════

INSERT INTO storer (storerkey, company, type, status, country, defaultfacility, susr1, susr2) VALUES
-- Korea Storers (ASIA-KR region)
('NIKE_KR', 'Nike Korea Ltd', '1', '1', 'KR', 'KR01', 'FOOTWEAR', 'PREMIUM'),
('HM_KR', 'H&M Korea', '1', '1', 'KR', 'KR02', 'APPAREL', 'FASTFASHION'),
('ADIDAS_KR', 'Adidas Korea', '1', '1', 'KR', 'KR01', 'FOOTWEAR', 'PREMIUM'),
('SAMSUNG_KR', 'Samsung Electronics', '1', '1', 'KR', 'KR01', 'ELECTRONICS', 'HIGHVALUE'),

-- India Storers (ASIA-IN region)
('NIKE_IN', 'Nike India Pvt Ltd', '1', '1', 'IN', 'IN01', 'FOOTWEAR', 'PREMIUM'),
('HM_IN', 'H&M India', '1', '1', 'IN', 'IN01', 'APPAREL', 'FASTFASHION'),
('UNILEVER_IN', 'Unilever India', '1', '1', 'IN', 'IN02', 'FMCG', 'STANDARD'),
('TATA_IN', 'Tata Consumer', '1', '1', 'IN', 'IN02', 'FMCG', 'STANDARD'),

-- Singapore Storers (ASIA-SG region)
('NIKE_SG', 'Nike Singapore', '1', '1', 'SG', 'SG01', 'FOOTWEAR', 'PREMIUM'),
('ADIDAS_SG', 'Adidas Singapore', '1', '1', 'SG', 'SG01', 'FOOTWEAR', 'PREMIUM'),
('DYSON_SG', 'Dyson Singapore', '1', '1', 'SG', 'SG01', 'ELECTRONICS', 'HIGHVALUE'),

-- Test Storers (for automated tests)
('TEST_STORER_001', 'Test Storer 001', '1', '1', 'KR', 'TEST01', 'TEST', 'AUTOMATED'),
('TEST_STORER_002', 'Test Storer 002', '1', '1', 'IN', 'TEST02', 'TEST', 'AUTOMATED'),
('TEST_STORER_003', 'Test Storer 003', '1', '1', 'SG', 'TEST03', 'TEST', 'AUTOMATED'),
('TEST_STORER_ERR', 'Error Test Storer', '1', '9', 'KR', 'TEST01', 'TEST', 'ERROR')  -- Inactive for error tests
ON CONFLICT (storerkey) DO NOTHING;

-- ═══════════════════════════════════════════════════════════════════════════
-- Storer Address (Billing & Shipping addresses)
-- ═══════════════════════════════════════════════════════════════════════════

-- Create STORERADDRESS table if not exists (for PostgreSQL compatibility)
CREATE TABLE IF NOT EXISTS storeraddress (
    storerkey VARCHAR(50) NOT NULL,
    addresstype VARCHAR(10) NOT NULL,  -- 'BILL', 'SHIP', 'REMIT'
    address1 VARCHAR(100),
    address2 VARCHAR(100),
    city VARCHAR(50),
    state VARCHAR(50),
    zip VARCHAR(20),
    country VARCHAR(10),
    contact VARCHAR(100),
    phone VARCHAR(30),
    email VARCHAR(100),
    PRIMARY KEY (storerkey, addresstype)
);

INSERT INTO storeraddress (storerkey, addresstype, address1, address2, city, state, zip, country, contact, phone, email) VALUES
-- Nike Korea
('NIKE_KR', 'BILL', '123 Nike Tower', 'Gangnam-gu', 'Seoul', 'Seoul', '06164', 'KR', 'Kim Finance', '+82-2-1234-5678', 'finance@nike.kr'),
('NIKE_KR', 'SHIP', '456 Nike Warehouse', 'Incheon Port', 'Incheon', 'Incheon', '22301', 'KR', 'Park Logistics', '+82-32-123-4567', 'logistics@nike.kr'),

-- H&M Korea
('HM_KR', 'BILL', '789 H&M Building', 'Myeongdong', 'Seoul', 'Seoul', '04538', 'KR', 'Lee Accounts', '+82-2-9876-5432', 'accounts@hm.kr'),
('HM_KR', 'SHIP', '321 H&M DC', 'Pyeongtaek', 'Gyeonggi', 'Gyeonggi-do', '17802', 'KR', 'Choi Warehouse', '+82-31-555-1234', 'warehouse@hm.kr'),

-- Nike India
('NIKE_IN', 'BILL', '100 Nike Campus', 'BKC', 'Mumbai', 'Maharashtra', '400051', 'IN', 'Sharma Finance', '+91-22-12345678', 'finance@nike.in'),
('NIKE_IN', 'SHIP', '200 Nike Logistics Hub', 'Bhiwandi', 'Mumbai', 'Maharashtra', '421302', 'IN', 'Patel Logistics', '+91-22-87654321', 'logistics@nike.in'),

-- Unilever India
('UNILEVER_IN', 'BILL', 'Unilever House', 'Andheri East', 'Mumbai', 'Maharashtra', '400093', 'IN', 'Gupta Accounts', '+91-22-44556677', 'accounts@unilever.in'),
('UNILEVER_IN', 'SHIP', 'Unilever DC', 'Taloja', 'Navi Mumbai', 'Maharashtra', '410208', 'IN', 'Singh DC', '+91-22-99887766', 'dc@unilever.in'),

-- Nike Singapore
('NIKE_SG', 'BILL', '1 Nike Plaza', 'Marina Bay', 'Singapore', 'Singapore', '018935', 'SG', 'Tan Finance', '+65-6123-4567', 'finance@nike.sg'),
('NIKE_SG', 'SHIP', '50 Nike DC', 'Jurong', 'Singapore', 'Singapore', '628729', 'SG', 'Lim Logistics', '+65-6765-4321', 'logistics@nike.sg'),

-- Test Storers
('TEST_STORER_001', 'BILL', '999 Test Street', 'Test District', 'Test City', 'Test State', '99999', 'KR', 'Test Contact', '+82-99-999-9999', 'test@test.kr'),
('TEST_STORER_001', 'SHIP', '888 Test Warehouse', 'Test Zone', 'Test City', 'Test State', '88888', 'KR', 'Test Warehouse', '+82-88-888-8888', 'warehouse@test.kr'),
('TEST_STORER_002', 'BILL', '777 Test Road', 'Test Area', 'Test Metro', 'Test Region', '77777', 'IN', 'Test India', '+91-77-7777777', 'test@test.in'),
('TEST_STORER_002', 'SHIP', '666 Test Godown', 'Test Industrial', 'Test Metro', 'Test Region', '66666', 'IN', 'Test Godown', '+91-66-6666666', 'godown@test.in')
ON CONFLICT (storerkey, addresstype) DO NOTHING;

-- ═══════════════════════════════════════════════════════════════════════════
-- Facility Master (Extended)
-- ═══════════════════════════════════════════════════════════════════════════

INSERT INTO facility (facility, storerkey, facilityname, country, status, address1, city) VALUES
-- Korea Facilities
('KR01', 'NIKE_KR', 'Korea DC 1 - Incheon', 'KR', '1', 'Incheon Free Trade Zone', 'Incheon'),
('KR02', 'HM_KR', 'Korea DC 2 - Pyeongtaek', 'KR', '1', 'Pyeongtaek Port Logistics', 'Pyeongtaek'),
('KR03', 'ADIDAS_KR', 'Korea DC 3 - Busan', 'KR', '1', 'Busan New Port', 'Busan'),

-- India Facilities
('IN01', 'NIKE_IN', 'India DC 1 - Mumbai', 'IN', '1', 'Bhiwandi Logistics Park', 'Mumbai'),
('IN02', 'UNILEVER_IN', 'India DC 2 - Chennai', 'IN', '1', 'Sriperumbudur Industrial', 'Chennai'),
('IN03', 'TATA_IN', 'India DC 3 - Delhi NCR', 'IN', '1', 'Gurgaon Logistics Hub', 'Gurgaon'),

-- Singapore Facilities
('SG01', 'NIKE_SG', 'Singapore DC 1 - Jurong', 'SG', '1', 'Jurong Port Road', 'Singapore'),
('SG02', 'DYSON_SG', 'Singapore DC 2 - Changi', 'SG', '1', 'Changi Logistics Park', 'Singapore'),

-- Test Facilities
('TEST01', 'TEST_STORER_001', 'Test Facility 1', 'KR', '1', 'Test Address 1', 'Test City'),
('TEST02', 'TEST_STORER_002', 'Test Facility 2', 'IN', '1', 'Test Address 2', 'Test City'),
('TEST03', 'TEST_STORER_003', 'Test Facility 3', 'SG', '1', 'Test Address 3', 'Test City'),
('TEST_ERR', 'TEST_STORER_ERR', 'Error Test Facility', 'KR', '9', 'Error Address', 'Error City')  -- Inactive
ON CONFLICT (facility) DO NOTHING;

-- ═══════════════════════════════════════════════════════════════════════════
-- Verification
-- ═══════════════════════════════════════════════════════════════════════════

DO $$
BEGIN
    RAISE NOTICE 'TD-STORER Data Loaded:';
    RAISE NOTICE '  Storers: %', (SELECT COUNT(*) FROM storer);
    RAISE NOTICE '  Storer Addresses: %', (SELECT COUNT(*) FROM storeraddress);
    RAISE NOTICE '  Facilities: %', (SELECT COUNT(*) FROM facility);
END $$;
