-- ═══════════════════════════════════════════════════════════════════════════
-- TD-SKU.sql - SKU Master Data + Pack Definitions + Location Mapping
-- ═══════════════════════════════════════════════════════════════════════════
-- Test Data Set: TD-SKU
-- Required: 20+ SKUs (various configs)
-- Entities: SKU, PACK, SKUXLOC
-- Used By: F1-F10 (All flows)
-- ═══════════════════════════════════════════════════════════════════════════

SET search_path TO dbo, public;

-- ═══════════════════════════════════════════════════════════════════════════
-- SKU Master (25+ SKUs with various configurations)
-- ═══════════════════════════════════════════════════════════════════════════

INSERT INTO sku (storerkey, sku, descr, lotcontrol, status, stdgrosswgt, stdnetwgt, stdcube, hazmatcode, shelflife) VALUES
-- Nike Korea SKUs (Lot Controlled - Style/Color tracking)
('NIKE_KR', 'NK-AIRMAX90-BLK', 'Nike Air Max 90 Black', 'Y', '1', 1.2, 1.0, 0.015, NULL, NULL),
('NIKE_KR', 'NK-AIRMAX90-WHT', 'Nike Air Max 90 White', 'Y', '1', 1.2, 1.0, 0.015, NULL, NULL),
('NIKE_KR', 'NK-AF1-BLK', 'Nike Air Force 1 Black', 'Y', '1', 1.3, 1.1, 0.016, NULL, NULL),
('NIKE_KR', 'NK-AF1-WHT', 'Nike Air Force 1 White', 'Y', '1', 1.3, 1.1, 0.016, NULL, NULL),
('NIKE_KR', 'NK-DRIFIT-M', 'Nike Dri-FIT Shirt Medium', 'Y', '1', 0.3, 0.25, 0.005, NULL, NULL),
('NIKE_KR', 'NK-DRIFIT-L', 'Nike Dri-FIT Shirt Large', 'Y', '1', 0.35, 0.3, 0.006, NULL, NULL),

-- H&M Korea SKUs (No Lot Control - Fast Fashion)
('HM_KR', 'HM-BASIC-TEE-S', 'Basic T-Shirt Small', 'N', '1', 0.2, 0.18, 0.003, NULL, NULL),
('HM_KR', 'HM-BASIC-TEE-M', 'Basic T-Shirt Medium', 'N', '1', 0.22, 0.2, 0.0035, NULL, NULL),
('HM_KR', 'HM-BASIC-TEE-L', 'Basic T-Shirt Large', 'N', '1', 0.25, 0.22, 0.004, NULL, NULL),
('HM_KR', 'HM-SLIM-JEANS-32', 'Slim Fit Jeans 32', 'N', '1', 0.8, 0.75, 0.012, NULL, NULL),
('HM_KR', 'HM-SLIM-JEANS-34', 'Slim Fit Jeans 34', 'N', '1', 0.85, 0.8, 0.013, NULL, NULL),

-- Adidas Korea SKUs (Lot Controlled)
('ADIDAS_KR', 'AD-ULTRABOOST-BLK', 'Adidas Ultraboost Black', 'Y', '1', 1.1, 0.95, 0.014, NULL, NULL),
('ADIDAS_KR', 'AD-ULTRABOOST-WHT', 'Adidas Ultraboost White', 'Y', '1', 1.1, 0.95, 0.014, NULL, NULL),
('ADIDAS_KR', 'AD-STAN-SMITH', 'Adidas Stan Smith', 'Y', '1', 1.0, 0.85, 0.013, NULL, NULL),

-- Adidas India SKUs (Lot Controlled)
('ADIDAS_IN', 'ADI-ULTRABOOST-BLK', 'Adidas Ultraboost Black India', 'Y', '1', 1.1, 0.95, 0.014, NULL, NULL),
('ADIDAS_IN', 'ADI-SUPERSTAR-WHT', 'Adidas Superstar White India', 'Y', '1', 1.0, 0.85, 0.013, NULL, NULL),

-- Nike India SKUs (copies for regional testing)
('NIKE_IN', 'NK-AIRMAX90-BLK', 'Nike Air Max 90 Black', 'Y', '1', 1.2, 1.0, 0.015, NULL, NULL),
('NIKE_IN', 'NK-AF1-BLK', 'Nike Air Force 1 Black', 'Y', '1', 1.3, 1.1, 0.016, NULL, NULL),

-- Unilever India SKUs (FMCG - Perishable with Shelf Life)
('UNILEVER_IN', 'UL-SOAP-LUX', 'Lux Beauty Soap 100g', 'Y', '1', 0.12, 0.1, 0.001, NULL, 730),
('UNILEVER_IN', 'UL-SHAMPOO-DOVE', 'Dove Shampoo 200ml', 'Y', '1', 0.25, 0.22, 0.002, NULL, 1095),
('UNILEVER_IN', 'UL-CREAM-FAIR', 'Fair & Lovely Cream 50g', 'Y', '1', 0.08, 0.05, 0.0008, NULL, 365),

-- Unilever Singapore SKUs (FMCG - Perishable with Shelf Life)
('UNI_SG', 'UNI-DOVE-SOAP-100', 'Dove Soap 100g Singapore', 'Y', '1', 0.12, 0.1, 0.001, NULL, 730),

-- Samsung Korea SKUs (Electronics - High Value)
('SAMSUNG_KR', 'SAM-GALAXY-S24', 'Samsung Galaxy S24', 'Y', '1', 0.2, 0.18, 0.002, NULL, NULL),
('SAMSUNG_KR', 'SAM-GALAXY-WATCH', 'Samsung Galaxy Watch 6', 'Y', '1', 0.1, 0.08, 0.001, NULL, NULL),

-- Test Storers SKUs (for automated testing)
('TEST_STORER_001', 'TEST-SKU-001', 'Test SKU Valid Standard', 'Y', '1', 1.0, 0.9, 0.01, NULL, NULL),
('TEST_STORER_001', 'TEST-SKU-002', 'Test SKU No Lot Control', 'N', '1', 0.5, 0.45, 0.005, NULL, NULL),
('TEST_STORER_001', 'TEST-SKU-003', 'Test SKU Perishable', 'Y', '1', 0.3, 0.25, 0.003, NULL, 180),
('TEST_STORER_001', 'TEST-SKU-004', 'Test SKU Hazmat', 'Y', '1', 2.0, 1.8, 0.02, 'HAZMAT-CL3', NULL),
('TEST_STORER_001', 'TEST-SKU-005', 'Test SKU Heavy', 'Y', '1', 50.0, 48.0, 0.5, NULL, NULL),
('TEST_STORER_001', 'TEST-SKU-ERR', 'Test SKU Inactive', 'Y', '9', 1.0, 0.9, 0.01, NULL, NULL),

('TEST_STORER_002', 'TEST-SKU-001', 'Test SKU India', 'Y', '1', 1.0, 0.9, 0.01, NULL, NULL),
('TEST_STORER_002', 'TEST-SKU-002', 'Test SKU India GST', 'Y', '1', 1.0, 0.9, 0.01, NULL, NULL),

('TEST_STORER_003', 'TEST-SKU-001', 'Test SKU Singapore', 'Y', '1', 1.0, 0.9, 0.01, NULL, NULL),

-- Error test storer SKU (inactive storer)
('TEST_STORER_ERR', 'TEST-SKU-001', 'Test SKU Error Storer', 'Y', '9', 1.0, 0.9, 0.01, NULL, NULL)
ON CONFLICT (storerkey, sku) DO NOTHING;

-- ═══════════════════════════════════════════════════════════════════════════
-- Pack Definitions (Unit of Measure conversions)
-- ═══════════════════════════════════════════════════════════════════════════

-- Create PACK table if not exists
CREATE TABLE IF NOT EXISTS pack (
    storerkey VARCHAR(50) NOT NULL,
    packkey VARCHAR(50) NOT NULL,
    descr VARCHAR(100),
    packuom1 VARCHAR(10),
    packuom2 VARCHAR(10),
    packuom3 VARCHAR(10),
    casecnt INT DEFAULT 1,
    pallet INT DEFAULT 1,
    innerpack INT DEFAULT 1,
    PRIMARY KEY (storerkey, packkey)
);

INSERT INTO pack (storerkey, packkey, descr, packuom1, packuom2, packuom3, casecnt, pallet, innerpack) VALUES
-- Nike Packs
('NIKE_KR', 'NK-STD-SHOE', 'Standard Shoe Pack', 'EA', 'CS', 'PLT', 12, 48, 1),
('NIKE_KR', 'NK-STD-APPAREL', 'Standard Apparel Pack', 'EA', 'CS', 'PLT', 24, 96, 6),

-- H&M Packs
('HM_KR', 'HM-FAST-PACK', 'Fast Fashion Pack', 'EA', 'CS', 'PLT', 50, 200, 10),
('HM_KR', 'HM-BULK-PACK', 'Bulk Fashion Pack', 'EA', 'CS', 'PLT', 100, 400, 20),

-- Unilever Packs
('UNILEVER_IN', 'UL-SOAP-PACK', 'Soap Case Pack', 'EA', 'CS', 'PLT', 72, 2880, 12),
('UNILEVER_IN', 'UL-LIQUID-PACK', 'Liquid Case Pack', 'EA', 'CS', 'PLT', 24, 960, 6),

-- Samsung Packs
('SAMSUNG_KR', 'SAM-PHONE-PACK', 'Phone Case Pack', 'EA', 'CS', 'PLT', 10, 100, 1),
('SAMSUNG_KR', 'SAM-WATCH-PACK', 'Watch Case Pack', 'EA', 'CS', 'PLT', 20, 200, 2),

-- Test Packs
('TEST_STORER_001', 'TEST-PACK-STD', 'Test Standard Pack', 'EA', 'CS', 'PLT', 10, 100, 1),
('TEST_STORER_001', 'TEST-PACK-BULK', 'Test Bulk Pack', 'EA', 'CS', 'PLT', 50, 500, 10),
('TEST_STORER_002', 'TEST-PACK-STD', 'Test Standard Pack India', 'EA', 'CS', 'PLT', 10, 100, 1)
ON CONFLICT (storerkey, packkey) DO NOTHING;

-- ═══════════════════════════════════════════════════════════════════════════
-- SKU x Location Mapping (Preferred putaway locations)
-- ═══════════════════════════════════════════════════════════════════════════

-- Create SKUXLOC table if not exists
CREATE TABLE IF NOT EXISTS skuxloc (
    storerkey VARCHAR(50) NOT NULL,
    sku VARCHAR(50) NOT NULL,
    loc VARCHAR(50) NOT NULL,
    loctype VARCHAR(20),
    priority INT DEFAULT 1,
    minqty DECIMAL(15,5) DEFAULT 0,
    maxqty DECIMAL(15,5) DEFAULT 99999,
    replenishqty DECIMAL(15,5) DEFAULT 0,
    status VARCHAR(5) DEFAULT '1',
    PRIMARY KEY (storerkey, sku, loc)
);

INSERT INTO skuxloc (storerkey, sku, loc, loctype, priority, minqty, maxqty) VALUES
-- Nike Korea SKU Locations
('NIKE_KR', 'NK-AIRMAX90-BLK', 'A-01-01', 'STORAGE', 1, 10, 500),
('NIKE_KR', 'NK-AIRMAX90-WHT', 'A-01-02', 'STORAGE', 1, 10, 500),
('NIKE_KR', 'NK-AF1-BLK', 'A-02-01', 'STORAGE', 1, 10, 500),
('NIKE_KR', 'NK-AF1-WHT', 'A-02-02', 'STORAGE', 1, 10, 500),
('NIKE_KR', 'NK-DRIFIT-M', 'B-01-01', 'STORAGE', 1, 50, 2000),
('NIKE_KR', 'NK-DRIFIT-L', 'B-01-02', 'STORAGE', 1, 50, 2000),

-- H&M Korea SKU Locations (High velocity - multiple locations)
('HM_KR', 'HM-BASIC-TEE-M', 'B-02-01', 'STORAGE', 1, 100, 5000),
('HM_KR', 'HM-BASIC-TEE-M', 'B-02-02', 'STORAGE', 2, 100, 5000),
('HM_KR', 'HM-SLIM-JEANS-32', 'C-01-01', 'STORAGE', 1, 50, 2000),

-- Test SKU Locations
('TEST_STORER_001', 'TEST-SKU-001', 'TEST-LOC-01', 'STORAGE', 1, 0, 1000),
('TEST_STORER_001', 'TEST-SKU-002', 'TEST-LOC-02', 'STORAGE', 1, 0, 1000),
('TEST_STORER_001', 'TEST-SKU-003', 'TEST-LOC-01', 'STORAGE', 2, 0, 500),
('TEST_STORER_001', 'TEST-SKU-004', 'HAZMAT-01', 'HAZMAT', 1, 0, 100),
('TEST_STORER_002', 'TEST-SKU-001', 'TEST-LOC-01', 'STORAGE', 1, 0, 1000)
ON CONFLICT (storerkey, sku, loc) DO NOTHING;

-- ═══════════════════════════════════════════════════════════════════════════
-- Verification
-- ═══════════════════════════════════════════════════════════════════════════

DO $$
BEGIN
    RAISE NOTICE 'TD-SKU Data Loaded:';
    RAISE NOTICE '  SKUs: %', (SELECT COUNT(*) FROM sku);
    RAISE NOTICE '  Packs: %', (SELECT COUNT(*) FROM pack);
    RAISE NOTICE '  SKU x Location: %', (SELECT COUNT(*) FROM skuxloc);
END $$;
