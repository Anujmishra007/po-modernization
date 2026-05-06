# EDI Test Data Files

This directory contains EDI 850 (Purchase Order) and EDI 856 (Advance Ship Notice) sample files for E2E testing.

## File Inventory

### EDI 850 - Purchase Order

| File | Description | Test Use |
|------|-------------|----------|
| `EDI-850-SAMPLE-001.txt` | Standard PO with 3 lines | Happy path - basic EDI parsing |
| `EDI-850-SAMPLE-002-NIKE.txt` | Nike PO with lottable tracking | Client-specific plugin testing |
| `EDI-850-SAMPLE-003-HM.txt` | H&M fast fashion PO | High volume, no lot control |
| `EDI-850-SAMPLE-004-ERROR-MISSING-SEGMENT.txt` | Missing mandatory segments | Error handling - validation |
| `EDI-850-SAMPLE-005-ERROR-MALFORMED.txt` | Malformed/invalid data | Error handling - parsing |

### EDI 856 - Advance Ship Notice (ASN)

| File | Description | Test Use |
|------|-------------|----------|
| `EDI-856-SAMPLE-001.txt` | Nike ASN with hierarchical structure | ASN population, lottable tracking |
| `EDI-856-SAMPLE-002-HM.txt` | H&M fast fashion ASN | High volume, fast-track processing |

## EDI 850 Structure (Purchase Order)

```
ISA - Interchange Control Header
  GS - Functional Group Header
    ST - Transaction Set Header (850 = Purchase Order)
      BEG - Beginning Segment for PO
      REF - Reference Identification (Storer, Facility, etc.)
      DTM - Date/Time Reference
      N1 - Party Identification (Buyer, Ship-To, Vendor)
      N3 - Party Address
      N4 - Party Geographic Location
      PO1 - Baseline Item Data (SKU, Qty, Price)
      PID - Product Description
      CTT - Transaction Totals
    SE - Transaction Set Trailer
  GE - Functional Group Trailer
IEA - Interchange Control Trailer
```

## EDI 856 Structure (Advance Ship Notice)

```
ISA - Interchange Control Header
  GS - Functional Group Header
    ST - Transaction Set Header (856 = Ship Notice)
      BSN - Beginning Segment for Ship Notice
      DTM - Date/Time Reference (Ship date, Delivery date)
      HL - Hierarchical Level
        Level S (Shipment)
          TD1 - Carrier Details (Quantity, Weight)
          TD5 - Routing (Carrier, Mode)
          TD3 - Equipment (Trailer Number)
          REF - Reference IDs (BOL, Container)
          N1 - Ship-From/Ship-To Party
        Level O (Order)
          PRF - Purchase Order Reference
          REF - Reference IDs (Storer, Season)
        Level P (Pack)
          MAN - Marks and Numbers (Carton Labels)
        Level I (Item)
          LIN - Item Identification (UPC, SKU)
          SN1 - Item Detail (Shipment)
          REF - Lottable Fields
      CTT - Transaction Totals
    SE - Transaction Set Trailer
  GE - Functional Group Trailer
IEA - Interchange Control Trailer
```

## Reference IDs Used

| REF Qualifier | Description | Example |
|---------------|-------------|---------|
| IA | Storer Key | TEST_STORER_001 |
| DP | Facility | TEST01 |
| CO | Season/Collection | SEASON-S24 |
| FF | Fast Fashion Flag | FASTTRACK |
| LT | Lottable Field | STYLE-AM90-2024 |

## Using in Tests

```java
// Load EDI file in Karate
def ediContent = read('classpath:test-data/edi/EDI-850-SAMPLE-001.txt')

// Send to EDI endpoint
Given url baseUrl + '/api/v1/edi/inbound'
And header Content-Type = 'application/x-edi'
And request ediContent
When method post
Then status 200
```

## Validation Rules

1. **ISA/IEA** - Must have matching control numbers
2. **GS/GE** - Must have matching group control numbers
3. **ST/SE** - Transaction count must match segment count
4. **BEG** - PO number is mandatory
5. **REF-IA** - Storer key must exist in database
6. **REF-DP** - Facility must be valid for storer
7. **PO1** - SKU must exist, quantity must be positive
8. **DTM-002** - Expected date must be valid and in future

## Error Scenarios

### Missing Segment (SAMPLE-004)
- Missing: REF*DP (Facility)
- Missing: N1*ST (Ship-To party)
- Expected: Validation error - missing mandatory data

### Malformed Data (SAMPLE-005)
- Invalid date format in DTM
- Non-numeric quantity in PO1
- Invalid price format
- Missing required fields
- Wrong segment count in SE
- Expected: Parse error - invalid format
