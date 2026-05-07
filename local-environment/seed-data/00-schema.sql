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
    descr VARCHAR(100),
    description VARCHAR(100),
    priority INTEGER DEFAULT 1,
    status VARCHAR(10) DEFAULT '1',
    adddate TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- Lot Master
CREATE TABLE IF NOT EXISTS lot (
    lot VARCHAR(50) PRIMARY KEY,
    storerkey VARCHAR(50) NOT NULL REFERENCES storer(storerkey),
    sku VARCHAR(50) NOT NULL,
    status VARCHAR(10) DEFAULT '1',
    manufacturedate TIMESTAMP,
    expirationdate TIMESTAMP,
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
    addwho VARCHAR(50),
    editdate TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    editwho VARCHAR(50),
    FOREIGN KEY (storerkey, sku) REFERENCES sku(storerkey, sku)
);

-- Putaway Strategy
CREATE TABLE IF NOT EXISTS putawaystrategy (
    strategykey VARCHAR(50) PRIMARY KEY,
    storerkey VARCHAR(50) REFERENCES storer(storerkey),
    sku VARCHAR(50),
    putawayzone VARCHAR(20),
    priority INTEGER DEFAULT 1,
    description VARCHAR(200),
    status VARCHAR(10) DEFAULT '1',
    adddate TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- Orders Header (Sales Orders and Purchase Orders)
CREATE TABLE IF NOT EXISTS orders (
    orderkey VARCHAR(50) PRIMARY KEY,
    storerkey VARCHAR(50) NOT NULL REFERENCES storer(storerkey),
    facility VARCHAR(20) REFERENCES facility(facility),
    externorderkey VARCHAR(50),
    ordertype VARCHAR(10) DEFAULT 'PO',
    status VARCHAR(10) DEFAULT '0',
    orderdate TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    expecteddate TIMESTAMP,
    expectedshipdate TIMESTAMP,
    closeddate TIMESTAMP,
    priority INTEGER DEFAULT 5,
    supplierkey VARCHAR(50),
    suppliername VARCHAR(100),
    buyerref VARCHAR(100),
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

-- Order Detail (Sales Orders and Purchase Orders)
CREATE TABLE IF NOT EXISTS orderdetail (
    orderkey VARCHAR(50) NOT NULL,
    orderlinenumber VARCHAR(10) NOT NULL,
    storerkey VARCHAR(50) NOT NULL,
    sku VARCHAR(50) NOT NULL,
    qtyordered DECIMAL(18,4) DEFAULT 0,
    qtyreceived DECIMAL(18,4) DEFAULT 0,
    qtyallocated DECIMAL(18,4) DEFAULT 0,
    qtypicked DECIMAL(18,4) DEFAULT 0,
    qtyshipped DECIMAL(18,4) DEFAULT 0,
    unitprice DECIMAL(18,4) DEFAULT 0,
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
    addwho VARCHAR(50),
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
    lot VARCHAR(50),
    id VARCHAR(50),
    qty DECIMAL(18,4) DEFAULT 0,
    qtyallocated DECIMAL(18,4) DEFAULT 0,
    qtyavailable DECIMAL(18,4) DEFAULT 0,
    qtyonhold DECIMAL(18,4) DEFAULT 0,
    qtypicked DECIMAL(18,4) DEFAULT 0,
    qtyintransit DECIMAL(18,4) DEFAULT 0,
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
    addwho VARCHAR(50),
    editdate TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    editwho VARCHAR(50),
    FOREIGN KEY (storerkey, sku) REFERENCES sku(storerkey, sku)
);

-- Inventory Hold
CREATE TABLE IF NOT EXISTS inventoryhold (
    inventoryholdkey VARCHAR(50) PRIMARY KEY,
    holdkey VARCHAR(50),
    lotxlocxidkey VARCHAR(50) REFERENCES lotxlocxid(lotxlocxidkey),
    storerkey VARCHAR(50),
    sku VARCHAR(50),
    lot VARCHAR(50),
    loc VARCHAR(50),
    id VARCHAR(50),
    holdcode VARCHAR(20) NOT NULL,
    holdqty DECIMAL(18,4) DEFAULT 0,
    status VARCHAR(10) DEFAULT 'ACTIVE',
    reason VARCHAR(200),
    holddate TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    holdby VARCHAR(50),
    adddate TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    addwho VARCHAR(50),
    editdate TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    editwho VARCHAR(50)
);

-- Inventory Transaction
CREATE TABLE IF NOT EXISTS inventorytransaction (
    transactionkey VARCHAR(50) PRIMARY KEY,
    storerkey VARCHAR(50) NOT NULL,
    sku VARCHAR(50) NOT NULL,
    lot VARCHAR(50),
    loc VARCHAR(50),
    id VARCHAR(50),
    transactiontype VARCHAR(20) NOT NULL,
    qty DECIMAL(18,4) DEFAULT 0,
    sourcekey VARCHAR(50),
    sourcetype VARCHAR(20),
    fromloc VARCHAR(50),
    toloc VARCHAR(50),
    fromstatus VARCHAR(10),
    tostatus VARCHAR(10),
    status VARCHAR(10) DEFAULT '1',
    adddate TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    addwho VARCHAR(50),
    editdate TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    editwho VARCHAR(50)
);

-- Task (Putaway, Pick, etc.)
CREATE TABLE IF NOT EXISTS task (
    taskkey VARCHAR(50) PRIMARY KEY,
    tasktype VARCHAR(20) NOT NULL,
    storerkey VARCHAR(50) NOT NULL,
    sku VARCHAR(50),
    lot VARCHAR(50),
    fromloc VARCHAR(50),
    toloc VARCHAR(50),
    fromid VARCHAR(50),
    toid VARCHAR(50),
    qty DECIMAL(18,4),
    status VARCHAR(10) DEFAULT '0',
    priority INTEGER DEFAULT 5,
    assigneduser VARCHAR(50),
    assignedto VARCHAR(50),
    receiptkey VARCHAR(50),
    pokey VARCHAR(50),
    orderkey VARCHAR(50),
    orderline INTEGER,
    adddate TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    addwho VARCHAR(50),
    editdate TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    editwho VARCHAR(50)
);

-- Task Detail
CREATE TABLE IF NOT EXISTS taskdetail (
    taskdetailkey VARCHAR(50) PRIMARY KEY,
    taskkey VARCHAR(50) NOT NULL REFERENCES task(taskkey),
    storerkey VARCHAR(50) NOT NULL,
    sku VARCHAR(50),
    lot VARCHAR(50),
    fromloc VARCHAR(50),
    toloc VARCHAR(50),
    fromid VARCHAR(50),
    toid VARCHAR(50),
    qty DECIMAL(18,4),
    qtyuom VARCHAR(10) DEFAULT 'EA',
    status VARCHAR(10) DEFAULT '0',
    adddate TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    addwho VARCHAR(50),
    editdate TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    editwho VARCHAR(50)
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
    innerpack INTEGER DEFAULT 1,
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
-- PO Tables (Purchase Orders - separate from Sales Orders)
-- ═══════════════════════════════════════════════════════════

-- PO Header
CREATE TABLE IF NOT EXISTS po (
    pokey VARCHAR(50) PRIMARY KEY,
    storerkey VARCHAR(50) NOT NULL REFERENCES storer(storerkey),
    facility VARCHAR(20) REFERENCES facility(facility),
    externpokey VARCHAR(50),
    potype VARCHAR(20) DEFAULT 'STANDARD',
    status VARCHAR(10) DEFAULT '0',
    expecteddate TIMESTAMP,
    closeddate TIMESTAMP,
    supplierkey VARCHAR(50),
    suppliername VARCHAR(100),
    buyerref VARCHAR(100),
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

-- PO Detail
CREATE TABLE IF NOT EXISTS podetail (
    pokey VARCHAR(50) NOT NULL,
    polinenumber VARCHAR(10) NOT NULL,
    storerkey VARCHAR(50) NOT NULL,
    sku VARCHAR(50) NOT NULL,
    qtyordered DECIMAL(18,4) DEFAULT 0,
    qtyreceived DECIMAL(18,4) DEFAULT 0,
    unitprice DECIMAL(18,4) DEFAULT 0,
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
    addwho VARCHAR(50),
    editwho VARCHAR(50),
    PRIMARY KEY (pokey, polinenumber),
    FOREIGN KEY (pokey) REFERENCES po(pokey),
    FOREIGN KEY (storerkey, sku) REFERENCES sku(storerkey, sku)
);

-- ═══════════════════════════════════════════════════════════
-- Job Configuration Tables
-- ═══════════════════════════════════════════════════════════

-- Job Configuration
CREATE TABLE IF NOT EXISTS jobconfiguration (
    jobkey VARCHAR(50) PRIMARY KEY,
    jobname VARCHAR(100) NOT NULL,
    description VARCHAR(500),
    jobtype VARCHAR(50),
    schedule VARCHAR(100),
    enabled VARCHAR(1) DEFAULT 'Y',
    facility VARCHAR(20),
    storerkey VARCHAR(50),
    lastrun TIMESTAMP,
    nextrun TIMESTAMP,
    status VARCHAR(10) DEFAULT '1',
    adddate TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    addwho VARCHAR(50)
);

-- Job Parameters
CREATE TABLE IF NOT EXISTS jobparameter (
    jobkey VARCHAR(50) NOT NULL,
    paramkey VARCHAR(50) NOT NULL,
    paramvalue VARCHAR(500),
    paramtype VARCHAR(20) DEFAULT 'STRING',
    description VARCHAR(200),
    PRIMARY KEY (jobkey, paramkey),
    FOREIGN KEY (jobkey) REFERENCES jobconfiguration(jobkey)
);

-- Job History
CREATE TABLE IF NOT EXISTS jobhistory (
    jobkey VARCHAR(50) NOT NULL,
    runid VARCHAR(50) NOT NULL,
    starttime TIMESTAMP,
    endtime TIMESTAMP,
    status VARCHAR(20),
    recordsprocessed INTEGER DEFAULT 0,
    errorcount INTEGER DEFAULT 0,
    errormessage VARCHAR(2000),
    PRIMARY KEY (jobkey, runid),
    FOREIGN KEY (jobkey) REFERENCES jobconfiguration(jobkey)
);

-- Job Lock (for concurrency)
CREATE TABLE IF NOT EXISTS joblock (
    jobkey VARCHAR(50) PRIMARY KEY,
    lockid VARCHAR(50),
    lockedby VARCHAR(50),
    locktime TIMESTAMP,
    expiresat TIMESTAMP,
    FOREIGN KEY (jobkey) REFERENCES jobconfiguration(jobkey)
);

-- Job Notification
CREATE TABLE IF NOT EXISTS jobnotification (
    jobkey VARCHAR(50) NOT NULL,
    notifytype VARCHAR(20) NOT NULL,
    recipient VARCHAR(200),
    onfailure VARCHAR(1) DEFAULT 'Y',
    onsuccess VARCHAR(1) DEFAULT 'N',
    onpartial VARCHAR(1) DEFAULT 'Y',
    PRIMARY KEY (jobkey, notifytype, recipient),
    FOREIGN KEY (jobkey) REFERENCES jobconfiguration(jobkey)
);

-- ═══════════════════════════════════════════════════════════
-- RDT (RF Device Terminal) Tables
-- ═══════════════════════════════════════════════════════════

-- User Group (Roles)
CREATE TABLE IF NOT EXISTS usergroup (
    usergroupkey VARCHAR(50) PRIMARY KEY,
    description VARCHAR(200),
    status VARCHAR(10) DEFAULT '1'
);

-- WMS User
CREATE TABLE IF NOT EXISTS wmsuser (
    userid VARCHAR(50) PRIMARY KEY,
    username VARCHAR(100) NOT NULL,
    password VARCHAR(200),
    usergroup VARCHAR(50) REFERENCES usergroup(usergroupkey),
    facility VARCHAR(20),
    status VARCHAR(10) DEFAULT '1',
    language VARCHAR(10) DEFAULT 'EN',
    defaultmenu VARCHAR(50),
    adddate TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    addwho VARCHAR(50)
);

-- Device
CREATE TABLE IF NOT EXISTS device (
    deviceid VARCHAR(50) PRIMARY KEY,
    devicename VARCHAR(100),
    devicetype VARCHAR(20),
    facility VARCHAR(20),
    status VARCHAR(10) DEFAULT '1',
    ipaddress VARCHAR(50),
    macaddress VARCHAR(50),
    lastactivity TIMESTAMP,
    adddate TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    addwho VARCHAR(50)
);

-- RDT Menu
CREATE TABLE IF NOT EXISTS rdtmenu (
    menukey VARCHAR(50) PRIMARY KEY,
    menudesc VARCHAR(100),
    parentmenu VARCHAR(50),
    menuorder INTEGER DEFAULT 0,
    screenid VARCHAR(50),
    accesslevel INTEGER DEFAULT 0,
    status VARCHAR(10) DEFAULT '1'
);

-- RDT Session
CREATE TABLE IF NOT EXISTS rdtsession (
    sessionid VARCHAR(50) PRIMARY KEY,
    userid VARCHAR(50) REFERENCES wmsuser(userid),
    deviceid VARCHAR(50) REFERENCES device(deviceid),
    facility VARCHAR(20),
    status VARCHAR(20) DEFAULT 'ACTIVE',
    logintime TIMESTAMP,
    lastactivity TIMESTAMP,
    currentmenu VARCHAR(50),
    currentscreen VARCHAR(50)
);

-- User Permission
CREATE TABLE IF NOT EXISTS userpermission (
    userid VARCHAR(50) NOT NULL,
    permissionkey VARCHAR(50) NOT NULL,
    granted VARCHAR(1) DEFAULT 'Y',
    adddate TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    addwho VARCHAR(50),
    PRIMARY KEY (userid, permissionkey),
    FOREIGN KEY (userid) REFERENCES wmsuser(userid)
);

-- ═══════════════════════════════════════════════════════════
-- Outbound / Sales Order Tables
-- ═══════════════════════════════════════════════════════════

-- XDock Linkage (PO to Sales Order mapping)
CREATE TABLE IF NOT EXISTS xdocklinkage (
    linkkey VARCHAR(50) PRIMARY KEY,
    pokey VARCHAR(50),
    polinenumber VARCHAR(10),
    orderkey VARCHAR(50),
    orderlinenumber VARCHAR(10),
    storerkey VARCHAR(50),
    sku VARCHAR(50),
    linkqty DECIMAL(18,4) DEFAULT 0,
    status VARCHAR(10) DEFAULT '0',
    adddate TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    addwho VARCHAR(50)
);

-- Allocation
CREATE TABLE IF NOT EXISTS allocation (
    allocationkey VARCHAR(50) PRIMARY KEY,
    orderkey VARCHAR(50),
    orderlinenumber VARCHAR(10),
    storerkey VARCHAR(50),
    sku VARCHAR(50),
    loc VARCHAR(50),
    id VARCHAR(50),
    qty DECIMAL(18,4) DEFAULT 0,
    status VARCHAR(10) DEFAULT '0',
    allocateddate TIMESTAMP,
    allocatedby VARCHAR(50),
    adddate TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    addwho VARCHAR(50)
);

-- Ship-To Address
CREATE TABLE IF NOT EXISTS shiptoaddress (
    addresskey VARCHAR(50) PRIMARY KEY,
    orderkey VARCHAR(50),
    company VARCHAR(200),
    address1 VARCHAR(200),
    address2 VARCHAR(200),
    city VARCHAR(100),
    state VARCHAR(50),
    zip VARCHAR(20),
    country VARCHAR(10),
    contactname VARCHAR(100),
    phone VARCHAR(50),
    email VARCHAR(100)
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
