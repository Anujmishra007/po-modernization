-- V1__initial_schema.sql
-- PO Modernization - Initial PostgreSQL Schema
-- Column names match JPA entity conventions (uppercase, no underscores)

-- Create dbo schema (matching SQL Server convention)
CREATE SCHEMA IF NOT EXISTS dbo;

-- ============================================
-- PO (Purchase Order Header)
-- ============================================
CREATE TABLE dbo.po (
    pokey VARCHAR(50) PRIMARY KEY,
    externpokey VARCHAR(50),
    storerkey VARCHAR(50) NOT NULL,
    facility VARCHAR(50),
    potype VARCHAR(20) DEFAULT 'STANDARD',
    status VARCHAR(1) DEFAULT '0',
    buyerkey VARCHAR(50),
    buyername VARCHAR(100),
    supplierkey VARCHAR(50),
    suppliername VARCHAR(100),
    orderdate TIMESTAMP,
    expecteddate TIMESTAMP,
    closedate TIMESTAMP,
    notes TEXT,
    adddate TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    addwho VARCHAR(50),
    editdate TIMESTAMP,
    editwho VARCHAR(50),
    version INT DEFAULT 0
);

CREATE INDEX idx_po_storerkey ON dbo.po(storerkey);
CREATE INDEX idx_po_facility ON dbo.po(facility);
CREATE INDEX idx_po_status ON dbo.po(status);

-- ============================================
-- PODETAIL (Purchase Order Line Items)
-- ============================================
CREATE TABLE dbo.podetail (
    pokey VARCHAR(50) NOT NULL,
    polinenumber INT NOT NULL,
    podetailkey VARCHAR(50),
    externpokey VARCHAR(50),
    storerkey VARCHAR(50) NOT NULL,
    sku VARCHAR(50) NOT NULL,
    skudescription VARCHAR(200),
    qtyordered DECIMAL(18,4) DEFAULT 0,
    qtyadjusted DECIMAL(18,4) DEFAULT 0,
    qtyreceived DECIMAL(18,4) DEFAULT 0,
    packkey VARCHAR(50) DEFAULT 'STD',
    uom VARCHAR(10) DEFAULT 'EA',
    unitprice DECIMAL(18,4),
    polinestatus VARCHAR(20) DEFAULT 'OPEN',
    facility VARCHAR(50),
    toid VARCHAR(50),
    channel VARCHAR(50),
    lottable01 VARCHAR(100),
    lottable02 VARCHAR(100),
    lottable03 VARCHAR(100),
    lottable04 VARCHAR(100),
    lottable05 VARCHAR(100),
    lottable06 VARCHAR(100),
    lottable07 VARCHAR(100),
    lottable08 VARCHAR(100),
    lottable09 VARCHAR(100),
    lottable10 VARCHAR(100),
    notes TEXT,
    adddate TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    addwho VARCHAR(50),
    editdate TIMESTAMP,
    editwho VARCHAR(50),
    version INT DEFAULT 0,
    PRIMARY KEY (pokey, polinenumber),
    FOREIGN KEY (pokey) REFERENCES dbo.po(pokey)
);

CREATE INDEX idx_podetail_sku ON dbo.podetail(storerkey, sku);

-- ============================================
-- SKU (Product Master)
-- ============================================
CREATE TABLE dbo.sku (
    storerkey VARCHAR(50) NOT NULL,
    sku VARCHAR(50) NOT NULL,
    skudescription VARCHAR(200),
    weight DECIMAL(18,4),
    length DECIMAL(18,4),
    width DECIMAL(18,4),
    height DECIMAL(18,4),
    cube DECIMAL(18,4),
    skugroup VARCHAR(50),
    hazmatcode VARCHAR(50),
    storagetype VARCHAR(20) DEFAULT 'STANDARD',
    stdpackkey VARCHAR(50) DEFAULT 'STD',
    stduom VARCHAR(10) DEFAULT 'EA',
    status VARCHAR(1) DEFAULT '0',
    shelflifedays INT,
    shelflifecode VARCHAR(10),
    adddate TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    addwho VARCHAR(50),
    editdate TIMESTAMP,
    editwho VARCHAR(50),
    PRIMARY KEY (storerkey, sku)
);

-- ============================================
-- RECEIPT (ASN Header)
-- ============================================
CREATE TABLE dbo.receipt (
    receiptkey VARCHAR(50) PRIMARY KEY,
    externreceiptkey VARCHAR(50),
    pokey VARCHAR(50),
    storerkey VARCHAR(50) NOT NULL,
    facility VARCHAR(50) NOT NULL,
    receipttype VARCHAR(20) DEFAULT 'STANDARD',
    status VARCHAR(1) DEFAULT '0',
    receiptdate DATE,
    expectedreceiptdate DATE,
    carrierkey VARCHAR(50),
    carriername VARCHAR(100),
    trailernumber VARCHAR(50),
    sealnumber VARCHAR(50),
    notes TEXT,
    adddate TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    addwho VARCHAR(50),
    editdate TIMESTAMP,
    editwho VARCHAR(50),
    version INT DEFAULT 0,
    FOREIGN KEY (pokey) REFERENCES dbo.po(pokey)
);

CREATE INDEX idx_receipt_pokey ON dbo.receipt(pokey);
CREATE INDEX idx_receipt_storerkey ON dbo.receipt(storerkey);

-- ============================================
-- RECEIPTDETAIL (ASN Line Items)
-- ============================================
CREATE TABLE dbo.receiptdetail (
    receiptkey VARCHAR(50) NOT NULL,
    receiptlinenumber INT NOT NULL,
    receiptdetailkey VARCHAR(50),
    pokey VARCHAR(50),
    polinenumber INT,
    storerkey VARCHAR(50) NOT NULL,
    sku VARCHAR(50) NOT NULL,
    qtyexpected DECIMAL(18,4) DEFAULT 0,
    qtyreceived DECIMAL(18,4) DEFAULT 0,
    qtyrejected DECIMAL(18,4) DEFAULT 0,
    packkey VARCHAR(50) DEFAULT 'STD',
    uom VARCHAR(10) DEFAULT 'EA',
    toloc VARCHAR(50),
    toid VARCHAR(50),
    lottable01 VARCHAR(100),
    lottable02 VARCHAR(100),
    lottable03 VARCHAR(100),
    lottable04 VARCHAR(100),
    lottable05 VARCHAR(100),
    lottable06 VARCHAR(100),
    lottable07 VARCHAR(100),
    lottable08 VARCHAR(100),
    lottable09 VARCHAR(100),
    lottable10 VARCHAR(100),
    status VARCHAR(1) DEFAULT '0',
    notes TEXT,
    adddate TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    addwho VARCHAR(50),
    editdate TIMESTAMP,
    editwho VARCHAR(50),
    version INT DEFAULT 0,
    PRIMARY KEY (receiptkey, receiptlinenumber),
    FOREIGN KEY (receiptkey) REFERENCES dbo.receipt(receiptkey)
);

CREATE INDEX idx_receiptdetail_sku ON dbo.receiptdetail(storerkey, sku);

-- ============================================
-- STORER (Client/Owner Master)
-- ============================================
CREATE TABLE dbo.storer (
    storerkey VARCHAR(50) PRIMARY KEY,
    company VARCHAR(100) NOT NULL,
    type VARCHAR(20) DEFAULT 'OWNER',
    status VARCHAR(1) DEFAULT '1',
    address1 VARCHAR(200),
    city VARCHAR(100),
    country VARCHAR(50),
    contact1 VARCHAR(100),
    phone1 VARCHAR(50),
    email1 VARCHAR(100),
    adddate TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    addwho VARCHAR(50),
    editdate TIMESTAMP,
    editwho VARCHAR(50)
);

-- ============================================
-- NCOUNTER (Key Generation)
-- ============================================
CREATE TABLE dbo.ncounter (
    countername VARCHAR(50) PRIMARY KEY,
    prefix VARCHAR(10),
    countervalue BIGINT DEFAULT 0,
    suffix VARCHAR(10),
    format VARCHAR(50),
    lastused TIMESTAMP
);

INSERT INTO dbo.ncounter (countername, prefix, countervalue, format) VALUES
('RECEIPT', 'RCV', 1000, 'PREFIX-VALUE'),
('PO', 'PO', 1000, 'PREFIX-VALUE'),
('RESERVATION', 'RES', 1000, 'PREFIX-VALUE');

-- ============================================
-- CODELKUP (Configuration Lookup)
-- ============================================
CREATE TABLE dbo.codelkup (
    listname VARCHAR(50) NOT NULL,
    code VARCHAR(50) NOT NULL,
    shortdescription VARCHAR(100),
    longdescription VARCHAR(500),
    value1 VARCHAR(200),
    value2 VARCHAR(200),
    value3 VARCHAR(200),
    status VARCHAR(1) DEFAULT '1',
    PRIMARY KEY (listname, code)
);

-- ============================================
-- Sample Data for Testing
-- ============================================

-- Insert test storers
INSERT INTO dbo.storer (storerkey, company, type, country) VALUES
('NIKE', 'Nike Inc.', 'OWNER', 'US'),
('HM', 'H&M', 'OWNER', 'SE'),
('TEST_STORER', 'Test Company', 'OWNER', 'US');

-- Insert test SKUs
INSERT INTO dbo.sku (storerkey, sku, skudescription, weight, stduom) VALUES
('NIKE', 'SKU-001', 'Nike Running Shoe', 0.5, 'EA'),
('NIKE', 'SKU-002', 'Nike T-Shirt', 0.2, 'EA'),
('HM', 'ART-12345', 'H&M Cotton Shirt', 0.25, 'EA'),
('TEST_STORER', 'TEST-SKU-001', 'Test Product 1', 1.0, 'EA');

-- Insert test PO
INSERT INTO dbo.po (pokey, storerkey, facility, status, orderdate, addwho) VALUES
('PO-TEST-001', 'NIKE', 'KR01', '0', CURRENT_TIMESTAMP, 'SYSTEM'),
('PO-TEST-002', 'HM', 'IN01', '0', CURRENT_TIMESTAMP, 'SYSTEM');

-- Insert test PO details
INSERT INTO dbo.podetail (pokey, polinenumber, storerkey, sku, qtyordered, uom, lottable01, lottable02, lottable03, addwho) VALUES
('PO-TEST-001', 1, 'NIKE', 'SKU-001', 100, 'EA', 'AB123456-001', 'BLK', 'M', 'SYSTEM'),
('PO-TEST-001', 2, 'NIKE', 'SKU-002', 200, 'EA', 'CD789012-002', 'WHT', 'L', 'SYSTEM'),
('PO-TEST-002', 1, 'HM', 'ART-12345', 500, 'EA', '1234567', 'BLUE', NULL, 'SYSTEM');
