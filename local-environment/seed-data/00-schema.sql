-- PO Modernization Test Database Schema
-- This file runs first (00- prefix) to create schema before data

-- Create schemas
CREATE SCHEMA IF NOT EXISTS dbo;

-- Set search path
SET search_path TO dbo, public;

-- ═══════════════════════════════════════════════════════════
-- Core Tables
-- ═══════════════════════════════════════════════════════════

-- Storer Master
CREATE TABLE IF NOT EXISTS storer (
    storerkey VARCHAR(50) PRIMARY KEY,
    company VARCHAR(100) NOT NULL,
    type VARCHAR(10) DEFAULT '1',
    status VARCHAR(10) DEFAULT '1',
    country VARCHAR(10),
    address1 VARCHAR(200),
    address2 VARCHAR(200),
    city VARCHAR(100),
    state VARCHAR(50),
    zip VARCHAR(20),
    defaultfacility VARCHAR(20),
    susr1 VARCHAR(50),
    susr2 VARCHAR(50),
    susr3 VARCHAR(50),
    susr4 VARCHAR(50),
    susr5 VARCHAR(50),
    adddate TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    editdate TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- Facility Master
CREATE TABLE IF NOT EXISTS facility (
    facility VARCHAR(20) PRIMARY KEY,
    storerkey VARCHAR(50) REFERENCES storer(storerkey),
    facilityname VARCHAR(100),
    address1 VARCHAR(200),
    city VARCHAR(100),
    country VARCHAR(10),
    status VARCHAR(10) DEFAULT '1',
    adddate TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- SKU Master
CREATE TABLE IF NOT EXISTS sku (
    storerkey VARCHAR(50) NOT NULL,
    sku VARCHAR(50) NOT NULL,
    descr VARCHAR(200),
    stdcube DECIMAL(18,6),
    stdgrosswgt DECIMAL(18,6),
    stdnetwgt DECIMAL(18,6),
    lotcontrol VARCHAR(10) DEFAULT 'Y',
    shelflife INTEGER,
    hazmatcode VARCHAR(20),
    status VARCHAR(10) DEFAULT '1',
    adddate TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (storerkey, sku),
    FOREIGN KEY (storerkey) REFERENCES storer(storerkey)
);

-- Location Master
CREATE TABLE IF NOT EXISTS loc (
    loc VARCHAR(50) PRIMARY KEY,
    facility VARCHAR(20) REFERENCES facility(facility),
    loctype VARCHAR(10),
    putawayzone VARCHAR(20),
    locationflag VARCHAR(10) DEFAULT 'AVAILABLE',
    maxweight DECIMAL(18,4),
    maxcube DECIMAL(18,4),
    status VARCHAR(10) DEFAULT '1',
    adddate TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- Putaway Zone
CREATE TABLE IF NOT EXISTS putawayzone (
    putawayzone VARCHAR(20) PRIMARY KEY,
    facility VARCHAR(20),
    description VARCHAR(100),
    status VARCHAR(10) DEFAULT '1',
    adddate TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- Purchase Order Header
CREATE TABLE IF NOT EXISTS orders (
    orderkey VARCHAR(50) PRIMARY KEY,
    storerkey VARCHAR(50) NOT NULL REFERENCES storer(storerkey),
    facility VARCHAR(20) REFERENCES facility(facility),
    externorderkey VARCHAR(50),
    ordertype VARCHAR(10) DEFAULT 'PO',
    status VARCHAR(10) DEFAULT '0',
    orderdate TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    expecteddate TIMESTAMP,
    supplierkey VARCHAR(50),
    suppliername VARCHAR(100),
    notes VARCHAR(500),
    susr1 VARCHAR(50),
    susr2 VARCHAR(50),
    susr3 VARCHAR(50),
    susr4 VARCHAR(50),
    susr5 VARCHAR(50),
    adddate TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    editdate TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    addwho VARCHAR(50),
    editwho VARCHAR(50)
);

-- Purchase Order Detail
CREATE TABLE IF NOT EXISTS orderdetail (
    orderkey VARCHAR(50) NOT NULL,
    orderlinenumber INTEGER NOT NULL,
    storerkey VARCHAR(50) NOT NULL,
    sku VARCHAR(50) NOT NULL,
    qtyordered DECIMAL(18,4) DEFAULT 0,
    qtyreceived DECIMAL(18,4) DEFAULT 0,
    uom VARCHAR(10) DEFAULT 'EA',
    status VARCHAR(10) DEFAULT '0',
    lottable01 VARCHAR(50),
    lottable02 VARCHAR(50),
    lottable03 VARCHAR(50),
    lottable04 VARCHAR(50),
    lottable05 VARCHAR(50),
    lottable06 VARCHAR(50),
    lottable07 VARCHAR(50),
    lottable08 VARCHAR(50),
    lottable09 VARCHAR(50),
    lottable10 VARCHAR(50),
    adddate TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    editdate TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (orderkey, orderlinenumber),
    FOREIGN KEY (orderkey) REFERENCES orders(orderkey),
    FOREIGN KEY (storerkey, sku) REFERENCES sku(storerkey, sku)
);

-- Receipt Header
CREATE TABLE IF NOT EXISTS receipt (
    receiptkey VARCHAR(50) PRIMARY KEY,
    orderkey VARCHAR(50) REFERENCES orders(orderkey),
    storerkey VARCHAR(50) NOT NULL REFERENCES storer(storerkey),
    facility VARCHAR(20) REFERENCES facility(facility),
    externreceiptkey VARCHAR(50),
    type VARCHAR(10) DEFAULT 'NORMAL',
    status VARCHAR(10) DEFAULT '0',
    receiptdate TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    finalizationdate TIMESTAMP,
    adddate TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    editdate TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    addwho VARCHAR(50),
    editwho VARCHAR(50)
);

-- Receipt Detail
CREATE TABLE IF NOT EXISTS receiptdetail (
    receiptkey VARCHAR(50) NOT NULL,
    receiptlinenumber INTEGER NOT NULL,
    orderkey VARCHAR(50),
    orderlinenumber INTEGER,
    storerkey VARCHAR(50) NOT NULL,
    sku VARCHAR(50) NOT NULL,
    qtyexpected DECIMAL(18,4) DEFAULT 0,
    qtyreceived DECIMAL(18,4) DEFAULT 0,
    status VARCHAR(10) DEFAULT '0',
    toloc VARCHAR(50),
    lottable01 VARCHAR(50),
    lottable02 VARCHAR(50),
    lottable03 VARCHAR(50),
    lottable04 VARCHAR(50),
    lottable05 VARCHAR(50),
    lottable06 VARCHAR(50),
    lottable07 VARCHAR(50),
    lottable08 VARCHAR(50),
    lottable09 VARCHAR(50),
    lottable10 VARCHAR(50),
    adddate TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    editdate TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (receiptkey, receiptlinenumber),
    FOREIGN KEY (receiptkey) REFERENCES receipt(receiptkey),
    FOREIGN KEY (storerkey, sku) REFERENCES sku(storerkey, sku)
);

-- Inventory (LOTxLOCxID)
CREATE TABLE IF NOT EXISTS lotxlocxid (
    lotxlocxidkey VARCHAR(50) PRIMARY KEY,
    storerkey VARCHAR(50) NOT NULL,
    sku VARCHAR(50) NOT NULL,
    loc VARCHAR(50) NOT NULL REFERENCES loc(loc),
    id VARCHAR(50),
    qty DECIMAL(18,4) DEFAULT 0,
    qtyallocated DECIMAL(18,4) DEFAULT 0,
    qtyavailable DECIMAL(18,4) DEFAULT 0,
    lottable01 VARCHAR(50),
    lottable02 VARCHAR(50),
    lottable03 VARCHAR(50),
    lottable04 VARCHAR(50),
    lottable05 VARCHAR(50),
    lottable06 VARCHAR(50),
    lottable07 VARCHAR(50),
    lottable08 VARCHAR(50),
    lottable09 VARCHAR(50),
    lottable10 VARCHAR(50),
    status VARCHAR(10) DEFAULT 'OK',
    adddate TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    editdate TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (storerkey, sku) REFERENCES sku(storerkey, sku)
);

-- Inventory Hold
CREATE TABLE IF NOT EXISTS inventoryhold (
    inventoryholdkey VARCHAR(50) PRIMARY KEY,
    lotxlocxidkey VARCHAR(50) REFERENCES lotxlocxid(lotxlocxidkey),
    holdcode VARCHAR(20) NOT NULL,
    status VARCHAR(10) DEFAULT 'ACTIVE',
    reason VARCHAR(200),
    adddate TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    addwho VARCHAR(50)
);

-- Task (Putaway, Pick, etc.)
CREATE TABLE IF NOT EXISTS task (
    taskkey VARCHAR(50) PRIMARY KEY,
    tasktype VARCHAR(20) NOT NULL,
    storerkey VARCHAR(50) NOT NULL,
    sku VARCHAR(50),
    fromloc VARCHAR(50),
    toloc VARCHAR(50),
    qty DECIMAL(18,4),
    status VARCHAR(10) DEFAULT '0',
    priority INTEGER DEFAULT 5,
    assigneduser VARCHAR(50),
    adddate TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    editdate TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- Code Lookup (Configuration)
CREATE TABLE IF NOT EXISTS codelkup (
    listname VARCHAR(50) NOT NULL,
    code VARCHAR(50) NOT NULL,
    description VARCHAR(200),
    shortdescription VARCHAR(50),
    shortdescr VARCHAR(50),
    longdescr VARCHAR(500),
    adddate TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (listname, code)
);

-- Pack (for UOM conversion)
CREATE TABLE IF NOT EXISTS pack (
    storerkey VARCHAR(50) NOT NULL,
    packkey VARCHAR(50) NOT NULL,
    descr VARCHAR(200),
    packuom1 VARCHAR(10),
    packuom2 VARCHAR(10),
    packuom3 VARCHAR(10),
    casecnt INTEGER DEFAULT 1,
    pallet INTEGER DEFAULT 1,
    adddate TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (storerkey, packkey)
);

-- SKU x Location (for location-specific SKU config)
CREATE TABLE IF NOT EXISTS skuloc (
    storerkey VARCHAR(50) NOT NULL,
    sku VARCHAR(50) NOT NULL,
    loc VARCHAR(50) NOT NULL,
    maxqty DECIMAL(18,4),
    minqty DECIMAL(18,4),
    replenishqty DECIMAL(18,4),
    status VARCHAR(10) DEFAULT '1',
    PRIMARY KEY (storerkey, sku, loc)
);

-- ═══════════════════════════════════════════════════════════
-- Indexes for Performance
-- ═══════════════════════════════════════════════════════════

CREATE INDEX IF NOT EXISTS idx_orders_storerkey ON orders(storerkey);
CREATE INDEX IF NOT EXISTS idx_orders_status ON orders(status);
CREATE INDEX IF NOT EXISTS idx_orderdetail_sku ON orderdetail(storerkey, sku);
CREATE INDEX IF NOT EXISTS idx_receipt_orderkey ON receipt(orderkey);
CREATE INDEX IF NOT EXISTS idx_receipt_status ON receipt(status);
CREATE INDEX IF NOT EXISTS idx_lotxlocxid_sku ON lotxlocxid(storerkey, sku);
CREATE INDEX IF NOT EXISTS idx_lotxlocxid_loc ON lotxlocxid(loc);
CREATE INDEX IF NOT EXISTS idx_task_status ON task(status);

-- ═══════════════════════════════════════════════════════════
-- Sequences for Key Generation
-- ═══════════════════════════════════════════════════════════

CREATE SEQUENCE IF NOT EXISTS seq_orderkey START WITH 1000000;
CREATE SEQUENCE IF NOT EXISTS seq_receiptkey START WITH 2000000;
CREATE SEQUENCE IF NOT EXISTS seq_lotxlocxidkey START WITH 3000000;
CREATE SEQUENCE IF NOT EXISTS seq_taskkey START WITH 4000000;
