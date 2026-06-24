# Variation Resolver - End-to-End Guide

> **Document Purpose:** Comprehensive guide explaining the Variation Resolver architecture, how it determines V0/V2/Region/Client variations, and how it replaces legacy SP IF-ELSE chains.

---

## Table of Contents

1. [Executive Summary](#1-executive-summary)
2. [What is V0 vs V2?](#2-what-is-v0-vs-v2)
3. [Variation Resolver Architecture](#3-variation-resolver-architecture)
4. [VariationContext Model](#4-variationcontext-model)
5. [How Version is Determined](#5-how-version-is-determined)
6. [Configuration Service](#6-configuration-service)
7. [Plugin Manager](#7-plugin-manager)
8. [End-to-End Flow](#8-end-to-end-flow)
9. [What Legacy Code This Replaces](#9-what-legacy-code-this-replaces)
10. [Key Source Files](#10-key-source-files)

---

## 1. Executive Summary

### The Problem

Legacy stored procedures contain **massive IF-ELSE chains** to handle client/region-specific logic:

```sql
-- Legacy SP: 200+ lines of IF-ELSE
IF @StorerKey = 'NIKE'
    EXEC ispNikeLogic @Key
ELSE IF @StorerKey = 'HM'
    EXEC ispHMLogic @Key
ELSE IF @StorerKey = 'ADIDAS'
    -- ... 50+ more branches
```

### The Solution

The **Variation Resolver** centralizes all variation logic into a single component that:

1. **Determines Version** (V0 or V2 database)
2. **Determines Region** (ASIA-KR, ASIA-SG, ASIA-TH, etc.)
3. **Determines Client** (NIKE, HM, ZARA, etc.)
4. **Creates a VariationContext** that flows through the entire system

```java
// Modern Java: Single line replaces 200+ lines of IF-ELSE
VariationContext context = variationResolver.resolve(storerKey, facility);
```

### Key Metrics

| Aspect | Before (Legacy) | After (Variation Resolver) |
|--------|-----------------|---------------------------|
| Lines of Code | 200+ per SP | 1 line |
| Adding New Client | Modify 20+ SPs | Add YAML + Plugin |
| Testing | Integration only | Unit test each dimension |
| Debugging | Trace through SQL | Context logged at entry |

---

## 2. What is V0 vs V2?

### Critical Understanding

**V0 and V2 are NOT:**
- Different versions of Java code
- Legacy vs New application
- Old vs New stored procedures

**V0 and V2 ARE:**
- Two different **SQL Server databases**
- Both are **legacy systems**
- Both contain **stored procedures**
- Different **table schemas**

### Project Structure

```
WMS/
├── fbm-mwms-unified-wms-db/      ← V0 DATABASE (Legacy Unified)
│   └── 12,996 stored procedures
│   └── 932 tables
│   └── 464 triggers
│
├── FbM-fulfillment-mwms-wms-db/  ← V2 DATABASE (Current)
│   └── 12,139 stored procedures
│   └── 850 tables
│   └── 458 triggers
│
├── fbm-fulfillment-wms-service/  ← EXISTING JAVA SERVICE (calls V2 SPs)
│
└── po-modernization/             ← NEW JAVA MICROSERVICE (replaces SPs)
                                     But still writes to V0 or V2 database!
```

### Database Comparison

| Aspect | V0 (Legacy Unified) | V2 (Current) |
|--------|---------------------|--------------|
| Database Repo | `fbm-mwms-unified-wms-db` | `FbM-fulfillment-mwms-wms-db` |
| Stored Procedures | 12,996 | 12,139 |
| Tables | 932 | 850 |
| Triggers | 464 | 458 |
| PO Table Name | `dbo.PO` | `dbo.ORDERS` |
| Used By | Some legacy clients | Most clients |
| Special Features | Bartender, Vocollect, ASRS, TPB | Standard features |

### Why Both Exist

```
┌─────────────────────────────────────────────────────────────────────────────────┐
│                    HISTORY TIMELINE                                              │
├─────────────────────────────────────────────────────────────────────────────────┤
│                                                                                  │
│  2015-2018: V0 Created                                                           │
│  ─────────────────────                                                           │
│  • Unified system for ALL countries                                              │
│  • Single database                                                               │
│  • All clients onboarded here                                                    │
│                                                                                  │
│  2019-2023: V2 Created                                                           │
│  ─────────────────────                                                           │
│  • Refactored schema                                                             │
│  • New clients onboarded to V2                                                   │
│  • Some old clients migrated to V2                                               │
│  • Some old clients STILL on V0 (migration pending)                              │
│                                                                                  │
│  2024+: PO Modernization                                                         │
│  ─────────────────────────                                                       │
│  • Replaces SP logic with Java                                                   │
│  • Still writes to V0 OR V2 (depending on client)                                │
│  • Eventually will have own database                                             │
│                                                                                  │
└─────────────────────────────────────────────────────────────────────────────────┘
```

### Architecture Diagram

```
┌─────────────────────────────────────────────────────────────────────────────────┐
│                                                                                  │
│                              API REQUEST                                         │
│                              storerKey="NIKE_KR"                                 │
│                              facility="KR01"                                     │
│                                   │                                              │
│                                   v                                              │
│  ┌───────────────────────────────────────────────────────────────────────────┐  │
│  │                     NEW JAVA MICROSERVICE                                  │  │
│  │                     (po-modernization)                                     │  │
│  │                                                                            │  │
│  │   ┌─────────────────────────────────────────────────────────────────────┐ │  │
│  │   │  VARIATION RESOLVER                                                  │ │  │
│  │   │  • Determines: version="V2", region="ASIA-KR", client="NIKE"        │ │  │
│  │   └─────────────────────────────────────────────────────────────────────┘ │  │
│  │                                   │                                        │  │
│  │   ┌─────────────────────────────────────────────────────────────────────┐ │  │
│  │   │  BUSINESS LOGIC (Java)                                               │ │  │
│  │   │  • Drools Rules                                                      │ │  │
│  │   │  • Config from YAML                                                  │ │  │
│  │   │  • Plugins (Nike, Korea)                                             │ │  │
│  │   └─────────────────────────────────────────────────────────────────────┘ │  │
│  │                                   │                                        │  │
│  │   ┌─────────────────────────────────────────────────────────────────────┐ │  │
│  │   │  LEGACY BRIDGE SERVICE                                               │ │  │
│  │   │  if (context.isV0()) → v0Adapter.sync()                             │ │  │
│  │   │  else                → v2Adapter.sync()  ← This case                │ │  │
│  │   └─────────────────────────────────────────────────────────────────────┘ │  │
│  │                                                                            │  │
│  └───────────────────────────────────────────────────────────────────────────┘  │
│                                   │                                              │
│                    ┌──────────────┴──────────────┐                              │
│                    │                             │                              │
│                    v                             v                              │
│   ┌─────────────────────────────┐   ┌─────────────────────────────┐            │
│   │       V0 DATABASE           │   │       V2 DATABASE           │            │
│   │       (Not used here)       │   │       (Used for NIKE_KR)    │            │
│   │                             │   │                             │            │
│   │   fbm-mwms-unified-wms-db   │   │   FbM-fulfillment-mwms-     │            │
│   │                             │   │   wms-db                    │            │
│   └─────────────────────────────┘   └─────────────────────────────┘            │
│                                                                                  │
└─────────────────────────────────────────────────────────────────────────────────┘
```

---

## 3. Variation Resolver Architecture

### Core Components

```
┌─────────────────────────────────────────────────────────────────────────────────┐
│                    VARIATION RESOLVER COMPONENTS                                 │
├─────────────────────────────────────────────────────────────────────────────────┤
│                                                                                  │
│   ┌─────────────────────────────────────────────────────────────────────────┐   │
│   │                         VARIATION RESOLVER                               │   │
│   │                         (Entry Point)                                    │   │
│   │                                                                          │   │
│   │   Input: storerKey, facility                                             │   │
│   │   Output: VariationContext                                               │   │
│   │                                                                          │   │
│   │   ┌─────────────┐  ┌─────────────┐  ┌─────────────┐  ┌─────────────┐    │   │
│   │   │ Determine   │  │ Determine   │  │ Determine   │  │ Check       │    │   │
│   │   │ VERSION     │  │ REGION      │  │ CLIENT      │  │ DUAL-WRITE  │    │   │
│   │   │             │  │             │  │             │  │             │    │   │
│   │   │ V0 or V2    │  │ ASIA-KR     │  │ NIKE        │  │ true/false  │    │   │
│   │   │             │  │ ASIA-SG     │  │ HM          │  │             │    │   │
│   │   │             │  │ ASIA-TH     │  │ ZARA        │  │             │    │   │
│   │   │             │  │ ASIA-IN     │  │ STANDARD    │  │             │    │   │
│   │   └─────────────┘  └─────────────┘  └─────────────┘  └─────────────┘    │   │
│   │                                                                          │   │
│   └─────────────────────────────────────────────────────────────────────────┘   │
│                                         │                                        │
│                                         v                                        │
│   ┌─────────────────────────────────────────────────────────────────────────┐   │
│   │                         VARIATION CONTEXT                                │   │
│   │                         (Immutable Data Object)                          │   │
│   │                                                                          │   │
│   │   {                                                                      │   │
│   │     "version": "V2",                                                     │   │
│   │     "region": "ASIA-KR",                                                 │   │
│   │     "client": "NIKE",                                                    │   │
│   │     "facility": "KR01",                                                  │   │
│   │     "storerKey": "NIKE_KR",                                              │   │
│   │     "dualWriteEnabled": false                                            │   │
│   │   }                                                                      │   │
│   │                                                                          │   │
│   └─────────────────────────────────────────────────────────────────────────┘   │
│                                         │                                        │
│              ┌──────────────────────────┼──────────────────────────┐            │
│              │                          │                          │            │
│              v                          v                          v            │
│   ┌──────────────────┐     ┌──────────────────┐     ┌──────────────────┐       │
│   │ CONFIGURATION    │     │ PLUGIN           │     │ RULES            │       │
│   │ SERVICE          │     │ MANAGER          │     │ ENGINE           │       │
│   │                  │     │                  │     │                  │       │
│   │ Loads YAML:      │     │ Selects plugins: │     │ Fires rules:     │       │
│   │ base.yaml        │     │ • KoreaPlugin    │     │ • po-validation  │       │
│   │ + asia_kr.yaml   │     │ • NikePlugin     │     │ • lottable-rules │       │
│   │ + nike.yaml      │     │                  │     │                  │       │
│   └──────────────────┘     └──────────────────┘     └──────────────────┘       │
│                                                                                  │
└─────────────────────────────────────────────────────────────────────────────────┘
```

### File Locations

| Component | File Path |
|-----------|-----------|
| VariationResolver | `po-variation/src/main/java/com/wms/po/variation/context/VariationResolver.java` |
| VariationContext | `po-domain/src/main/java/com/wms/po/domain/model/VariationContext.java` |
| ConfigurationService | `po-variation/src/main/java/com/wms/po/variation/config/ConfigurationService.java` |
| PluginManager | `po-plugin/src/main/java/com/wms/po/plugin/registry/PluginManager.java` |
| LegacyBridgeService | `po-legacy-bridge/src/main/java/com/wms/po/legacy/service/LegacyBridgeService.java` |

---

## 4. VariationContext Model

### Class Definition

**File:** `po-domain/src/main/java/com/wms/po/domain/model/VariationContext.java`

```java
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class VariationContext {

    private String version;           // "V0" or "V2"
    private String region;            // "ASIA-SG", "ASIA-KR", "ASIA-TH", "ASIA-IN"
    private String client;            // "NIKE", "HM", "ZARA", "STANDARD"
    private String facility;          // "KR01", "SG02", etc.
    private String storerKey;         // Client identifier
    private boolean dualWriteEnabled; // Whether to write to legacy system

    // Convenience methods
    @JsonIgnore
    public boolean isV0() { return "V0".equals(version); }

    @JsonIgnore
    public boolean isV2() { return "V2".equals(version); }

    @JsonIgnore
    public boolean isKorea() { return "ASIA-KR".equals(region); }

    @JsonIgnore
    public boolean isSingapore() { return "ASIA-SG".equals(region); }

    @JsonIgnore
    public boolean isThailand() { return "ASIA-TH".equals(region); }

    @JsonIgnore
    public boolean isIndia() { return "ASIA-IN".equals(region); }

    @JsonIgnore
    public boolean isNike() { return "NIKE".equals(client); }

    @JsonIgnore
    public boolean isHM() { return "HM".equals(client); }

    @JsonIgnore
    public boolean isStandard() { return "STANDARD".equals(client); }
}
```

### Field Descriptions

| Field | Type | Purpose | Example Values |
|-------|------|---------|----------------|
| `version` | String | Which database to write to | `"V0"`, `"V2"` |
| `region` | String | Geographic region for plugins/config | `"ASIA-KR"`, `"ASIA-SG"`, `"ASIA-TH"` |
| `client` | String | Client/brand for plugins/config | `"NIKE"`, `"HM"`, `"ZARA"`, `"STANDARD"` |
| `facility` | String | Warehouse facility code | `"KR01"`, `"SG02"`, `"TH01"` |
| `storerKey` | String | Original storer identifier | `"NIKE_KR"`, `"HM_SG"` |
| `dualWriteEnabled` | boolean | Write to both new and legacy | `true`, `false` |

---

## 5. How Version is Determined

### VariationResolver Logic

**File:** `po-variation/src/main/java/com/wms/po/variation/context/VariationResolver.java`

```java
@Service
@RequiredArgsConstructor
@Slf4j
public class VariationResolver {

    @Value("${po.dualwrite.enabled:false}")
    private boolean dualWriteEnabled;

    @Value("${po.default.version:V2}")
    private String defaultVersion;

    public VariationContext resolve(String storerKey, String facility) {
        log.debug("Resolving variation context for storer={}, facility={}", storerKey, facility);

        // Validate inputs
        if (storerKey == null || storerKey.isBlank()) {
            throw new BusinessException(ErrorCode.CONFIG_STORER_NOT_FOUND,
                "Storer key is required for variation resolution");
        }

        String version = determineVersion(storerKey);
        String region = determineRegion(facility);
        String client = determineClient(storerKey);
        boolean dualWrite = isDualWriteEnabled(storerKey);

        VariationContext context = VariationContext.builder()
            .version(version)
            .region(region)
            .client(client)
            .facility(facility)
            .storerKey(storerKey)
            .dualWriteEnabled(dualWrite)
            .build();

        log.info("Resolved: version={}, region={}, client={}", version, region, client);
        return context;
    }
}
```

### Version Determination

```java
private String determineVersion(String storerKey) {
    if (storerKey != null) {
        // Pattern 1: Explicit V0 prefix/suffix
        if (storerKey.startsWith("V0_") || storerKey.endsWith("_V0")) {
            return "V0";
        }

        // Pattern 2: Known V0 storers (from config/database)
        if (isV0Storer(storerKey)) {
            return "V0";
        }
    }
    return defaultVersion; // Default to V2
}
```

### Region Determination

```java
private String determineRegion(String facility) {
    if (facility == null || facility.length() < 2) {
        return "ASIA-DEFAULT";
    }

    String prefix = facility.substring(0, 2).toUpperCase();

    return switch (prefix) {
        case "SG" -> "ASIA-SG";    // Singapore
        case "KR" -> "ASIA-KR";    // Korea
        case "TH" -> "ASIA-TH";    // Thailand
        case "IN" -> "ASIA-IN";    // India
        case "MY" -> "ASIA-MY";    // Malaysia
        case "VN" -> "ASIA-VN";    // Vietnam
        case "PH" -> "ASIA-PH";    // Philippines
        case "ID" -> "ASIA-ID";    // Indonesia
        case "AU" -> "APAC-AU";    // Australia
        case "NZ" -> "APAC-NZ";    // New Zealand
        default -> "ASIA-DEFAULT";
    };
}
```

### Client Determination

```java
private String determineClient(String storerKey) {
    if (storerKey == null) {
        return "STANDARD";
    }

    String upper = storerKey.toUpperCase();

    // Major client patterns
    if (upper.contains("NIKE") || upper.startsWith("NK")) return "NIKE";
    if (upper.contains("H&M") || upper.contains("HM")) return "HM";
    if (upper.contains("ZARA") || upper.contains("INDITEX")) return "ZARA";
    if (upper.contains("UNIQLO") || upper.startsWith("UQ")) return "UNIQLO";
    if (upper.contains("ADIDAS") || upper.startsWith("AD")) return "ADIDAS";
    if (upper.contains("PUMA")) return "PUMA";
    if (upper.contains("DECATHLON")) return "DECATHLON";
    if (upper.contains("GAP")) return "GAP";

    return "STANDARD";
}
```

### Resolution Examples

| storerKey | facility | version | region | client |
|-----------|----------|---------|--------|--------|
| `NIKE_KR` | `KR01` | V2 | ASIA-KR | NIKE |
| `NIKE_KR_V0` | `KR01` | V0 | ASIA-KR | NIKE |
| `V0_HM_SG` | `SG02` | V0 | ASIA-SG | HM |
| `HM_SG` | `SG02` | V2 | ASIA-SG | HM |
| `ZARA_TH` | `TH01` | V2 | ASIA-TH | ZARA |
| `GENERIC_CLIENT` | `IN01` | V2 | ASIA-IN | STANDARD |

---

## 6. Configuration Service

### Hierarchical Configuration Loading

**File:** `po-variation/src/main/java/com/wms/po/variation/config/ConfigurationService.java`

The ConfigurationService loads configuration with **hierarchical merge**:

```
base.yaml → regions/{region}.yaml → clients/{client}.yaml
```

### Merge Order

```
┌─────────────────────────────────────────────────────────────────────────────────┐
│                    CONFIGURATION MERGE ORDER                                     │
├─────────────────────────────────────────────────────────────────────────────────┤
│                                                                                  │
│   STEP 1: Load base.yaml                                                         │
│   ═══════════════════════                                                        │
│   {                                                                              │
│     "minShelfLifeDays": 0,                                                       │
│     "requireLottable03": false,                                                  │
│     "allowPartialShipment": true                                                 │
│   }                                                                              │
│                                         │                                        │
│                                         v                                        │
│   STEP 2: Merge regions/asia_kr.yaml                                             │
│   ══════════════════════════════════                                             │
│   {                                                                              │
│     "requireCustomsCode": true,         ← NEW field added                        │
│     "allowedFacilities": ["KR01", "KR02", "KR03"]                                │
│   }                                                                              │
│                                         │                                        │
│                                         v                                        │
│   STEP 3: Merge clients/nike.yaml                                                │
│   ═══════════════════════════════                                                │
│   {                                                                              │
│     "minShelfLifeDays": 90,             ← OVERRIDES base value                   │
│     "requireLottable03": true,          ← OVERRIDES base value                   │
│     "shelfLifeValidationEnabled": true  ← NEW field added                        │
│   }                                                                              │
│                                         │                                        │
│                                         v                                        │
│   FINAL MERGED CONFIG:                                                           │
│   ════════════════════                                                           │
│   {                                                                              │
│     "minShelfLifeDays": 90,             ← From Nike                              │
│     "requireLottable03": true,          ← From Nike                              │
│     "allowPartialShipment": true,       ← From Base                              │
│     "requireCustomsCode": true,         ← From Korea                             │
│     "allowedFacilities": ["KR01", "KR02", "KR03"],  ← From Korea                 │
│     "shelfLifeValidationEnabled": true  ← From Nike                              │
│   }                                                                              │
│                                                                                  │
│   Priority: CLIENT > REGION > BASE                                               │
│                                                                                  │
└─────────────────────────────────────────────────────────────────────────────────┘
```

### Key Code

```java
private VariationConfig loadConfig(ConfigKey key) {
    log.info("Loading configuration for region={}, client={}", key.region(), key.client());

    // 1. Load base config
    VariationConfig base = loadYaml("base.yaml");
    if (base == null) {
        base = createDefaultConfig();
    }

    // 2. Merge region config
    String regionFile = "regions/" + key.region().toLowerCase().replace("-", "_") + ".yaml";
    VariationConfig regionConfig = loadYaml(regionFile);
    if (regionConfig != null) {
        base = merge(base, regionConfig);
    }

    // 3. Merge client config
    String clientFile = "clients/" + key.client().toLowerCase() + ".yaml";
    VariationConfig clientConfig = loadYaml(clientFile);
    if (clientConfig != null) {
        base = merge(base, clientConfig);
    }

    return base;
}
```

### Caching

Configurations are cached using Caffeine:

```java
this.configCache = Caffeine.newBuilder()
    .expireAfterWrite(Duration.ofMinutes(5))   // Expire after 5 minutes
    .refreshAfterWrite(Duration.ofMinutes(1))  // Refresh after 1 minute
    .maximumSize(100)                          // Max 100 entries
    .build(this::loadConfig);
```

---

## 7. Plugin Manager

### Plugin Selection by Context

**File:** `po-plugin/src/main/java/com/wms/po/plugin/registry/PluginManager.java`

The PluginManager selects which plugins to run based on the VariationContext:

```java
@Service
@RequiredArgsConstructor
public class PluginManager {

    private final List<ClientPlugin> clientPlugins;   // Nike, HM, Zara, etc.
    private final List<RegionPlugin> regionPlugins;   // Korea, Singapore, India
    private final List<LifecycleHook> lifecycleHooks; // Audit, Metrics

    public PluginResult runPrePopulate(PopulateRequest request, VariationContext context) {

        // 1. Run REGION plugins first (order 50)
        for (RegionPlugin plugin : getApplicableRegionPlugins(context)) {
            PluginResult result = plugin.prePopulate(request, context);
            if (!result.isShouldContinue()) {
                return result; // Stop if plugin says so
            }
        }

        // 2. Run CLIENT plugins (order 100)
        for (ClientPlugin plugin : getApplicableClientPlugins(context)) {
            PluginResult result = plugin.prePopulate(request, context);
            if (!result.isShouldContinue()) {
                return result;
            }
        }

        // 3. Run lifecycle hooks
        for (LifecycleHook hook : getOrderedHooks()) {
            if (hook.appliesTo(context)) {
                hook.onPrePopulate(request, context);
            }
        }

        return PluginResult.success();
    }
}
```

### Plugin Selection Logic

```java
private List<RegionPlugin> getApplicableRegionPlugins(VariationContext context) {
    return regionPlugins.stream()
        .filter(p -> p.appliesTo(context.getRegion()))  // Match region
        .sorted(Comparator.comparingInt(RegionPlugin::getOrder))  // Sort by order
        .collect(Collectors.toList());
}

private List<ClientPlugin> getApplicableClientPlugins(VariationContext context) {
    return clientPlugins.stream()
        .filter(p -> p.appliesTo(context.getClient()))  // Match client
        .sorted(Comparator.comparingInt(ClientPlugin::getOrder))
        .collect(Collectors.toList());
}
```

### Example Plugins

**KoreaRegionPlugin:**

```java
@Component
public class KoreaRegionPlugin implements RegionPlugin {

    @Override
    public String getRegionCode() { return "ASIA-KR"; }

    @Override
    public int getOrder() { return 50; }  // Runs before client plugins

    @Override
    public PluginResult prePopulate(PopulateRequest request, VariationContext context) {
        log.info("Korea plugin: Checking customs clearance");

        // Korea-specific validations
        for (String poKey : request.getPoKeys()) {
            boolean cleared = checkCustomsClearance(poKey);
            if (!cleared) {
                log.warn("PO {} pending customs clearance", poKey);
            }
        }

        return PluginResult.success();
    }
}
```

**NikeClientPlugin:**

```java
@Component
public class NikeClientPlugin implements ClientPlugin {

    @Override
    public String getClientCode() { return "NIKE"; }

    @Override
    public int getOrder() { return 100; }  // Runs after region plugins

    @Override
    public PluginResult prePopulate(PopulateRequest request, VariationContext context) {
        log.info("Nike plugin: Validating style codes");

        // Nike-specific validations
        // Check Nike order matching
        // Validate shelf life (Nike requires 90+ days)

        return PluginResult.success();
    }
}
```

### Plugin Execution Order

For `context = { region: "ASIA-KR", client: "NIKE" }`:

```
1. KoreaRegionPlugin.prePopulate()     [order=50]
   └── Check customs clearance
   └── Validate lottable03 requirement

2. NikeClientPlugin.prePopulate()      [order=100]
   └── Validate Nike style codes
   └── Check Nike order matching

3. AuditLoggingHook.onPrePopulate()    [lifecycle hook]
   └── Log audit trail

4. MetricsHook.onPrePopulate()         [lifecycle hook]
   └── Record metrics
```

---

## 8. End-to-End Flow

### Complete Request Flow

```
┌─────────────────────────────────────────────────────────────────────────────────┐
│                    COMPLETE END-TO-END FLOW                                      │
├─────────────────────────────────────────────────────────────────────────────────┤
│                                                                                  │
│  STEP 1: API REQUEST                                                             │
│  ═══════════════════                                                             │
│                                                                                  │
│  POST /api/v1/po/populate                                                        │
│  {                                                                               │
│    "storerKey": "NIKE_KR",                                                       │
│    "facility": "KR01",                                                           │
│    "poKeys": ["PO-001", "PO-002"]                                               │
│  }                                                                               │
│                                   │                                              │
│                                   v                                              │
│  STEP 2: VARIATION RESOLUTION                                                    │
│  ═════════════════════════════                                                   │
│                                                                                  │
│  variationResolver.resolve("NIKE_KR", "KR01")                                   │
│                                                                                  │
│  ┌─────────────────────────────────────────────────────────────────────────┐    │
│  │  determineVersion("NIKE_KR")   → "V2"  (no V0 pattern)                  │    │
│  │  determineRegion("KR01")       → "ASIA-KR"  (KR prefix)                 │    │
│  │  determineClient("NIKE_KR")    → "NIKE"  (contains NIKE)                │    │
│  │  isDualWriteEnabled()          → false                                   │    │
│  └─────────────────────────────────────────────────────────────────────────┘    │
│                                                                                  │
│  Result: VariationContext {                                                      │
│    version: "V2",                                                                │
│    region: "ASIA-KR",                                                            │
│    client: "NIKE",                                                               │
│    facility: "KR01",                                                             │
│    storerKey: "NIKE_KR",                                                         │
│    dualWriteEnabled: false                                                       │
│  }                                                                               │
│                                   │                                              │
│                                   v                                              │
│  STEP 3: CONFIGURATION LOADING                                                   │
│  ══════════════════════════════                                                  │
│                                                                                  │
│  configService.getConfig(context)                                               │
│                                                                                  │
│  ┌─────────────────────────────────────────────────────────────────────────┐    │
│  │  Load: base.yaml                                                         │    │
│  │  Merge: regions/asia_kr.yaml   → requireCustomsCode: true               │    │
│  │  Merge: clients/nike.yaml      → minShelfLifeDays: 90                   │    │
│  └─────────────────────────────────────────────────────────────────────────┘    │
│                                   │                                              │
│                                   v                                              │
│  STEP 4: VALIDATION (Rules Engine)                                               │
│  ═════════════════════════════════                                               │
│                                                                                  │
│  rulesEngine.validate(poFact, context)                                          │
│                                                                                  │
│  ┌─────────────────────────────────────────────────────────────────────────┐    │
│  │  po-validation.drl rules fire:                                           │    │
│  │    • "Korea customs reference required" (context.isKorea() == true)     │    │
│  │    • "Nike style-color format" (context.isNike() == true)               │    │
│  │                                                                          │    │
│  │  lottable-rules.drl rules fire:                                          │    │
│  │    • "Lottable03 required" (config.requireLottable03 == true)           │    │
│  └─────────────────────────────────────────────────────────────────────────┘    │
│                                   │                                              │
│                                   v                                              │
│  STEP 5: PLUGIN EXECUTION                                                        │
│  ═════════════════════════                                                       │
│                                                                                  │
│  pluginManager.runPrePopulate(request, context)                                 │
│                                                                                  │
│  ┌─────────────────────────────────────────────────────────────────────────┐    │
│  │  1. KoreaRegionPlugin.prePopulate()  [order=50]                         │    │
│  │     └── Check customs clearance status                                   │    │
│  │                                                                          │    │
│  │  2. NikeClientPlugin.prePopulate()   [order=100]                        │    │
│  │     └── Validate Nike style codes                                        │    │
│  │     └── Check Nike order matching                                        │    │
│  └─────────────────────────────────────────────────────────────────────────┘    │
│                                   │                                              │
│                                   v                                              │
│  STEP 6: BUSINESS LOGIC                                                          │
│  ═══════════════════════                                                         │
│                                                                                  │
│  PopulatePOWorkflow executes:                                                    │
│    • Create Receipt Header                                                       │
│    • Create Receipt Details                                                      │
│    • Apply Lottable Mappings                                                     │
│    • Update PO Status                                                            │
│                                   │                                              │
│                                   v                                              │
│  STEP 7: LEGACY BRIDGE (Database Write)                                          │
│  ═══════════════════════════════════════                                         │
│                                                                                  │
│  legacyBridgeService.syncReceipt(receiptKey, context)                           │
│                                                                                  │
│  ┌─────────────────────────────────────────────────────────────────────────┐    │
│  │  if (context.isV0()) {                                                   │    │
│  │      v0Adapter.syncReceipt(receiptKey);  // Write to V0 DB              │    │
│  │  } else {                                                                │    │
│  │      v2Adapter.syncReceipt(receiptKey);  // Write to V2 DB  ← THIS      │    │
│  │  }                                                                       │    │
│  └─────────────────────────────────────────────────────────────────────────┘    │
│                                   │                                              │
│                                   v                                              │
│  STEP 8: POST-POPULATE PLUGINS                                                   │
│  ══════════════════════════════                                                  │
│                                                                                  │
│  pluginManager.runPostPopulate(receiptKey, request, context)                    │
│                                                                                  │
│  ┌─────────────────────────────────────────────────────────────────────────┐    │
│  │  1. KoreaRegionPlugin.postPopulate()                                     │    │
│  │     └── Update customs tracking table                                    │    │
│  │                                                                          │    │
│  │  2. NikeClientPlugin.postPopulate()                                      │    │
│  │     └── Send Nike notification                                           │    │
│  │     └── Update Nike order status                                         │    │
│  └─────────────────────────────────────────────────────────────────────────┘    │
│                                   │                                              │
│                                   v                                              │
│  STEP 9: RESPONSE                                                                │
│  ═════════════════                                                               │
│                                                                                  │
│  {                                                                               │
│    "success": true,                                                              │
│    "receiptKey": "RCV-20240501-001",                                            │
│    "message": "PO populated successfully"                                        │
│  }                                                                               │
│                                                                                  │
└─────────────────────────────────────────────────────────────────────────────────┘
```

---

## 9. What Legacy Code This Replaces

### Before: Legacy Stored Procedure

```sql
-- WM.lsp_FinalizeReceipt_Wrapper (lines 450-650)
-- 200+ lines of IF-ELSE chains

-- Determine client-specific logic
IF @StorerKey LIKE 'NIKE%'
BEGIN
    -- Nike-specific validations
    EXEC ispNikePreFinalize @ReceiptKey

    -- Nike shelf life check (90 days minimum)
    IF @ShelfLifeDays < 90
        RAISERROR('Nike requires minimum 90 days shelf life', 16, 1)

    -- Nike region-specific
    IF @Facility LIKE 'KR%'
    BEGIN
        -- Korea + Nike specific
        EXEC ispKoreaCustomsCheck @ReceiptKey
        SET @RequireLottable03 = 1
    END
    ELSE IF @Facility LIKE 'SG%'
    BEGIN
        -- Singapore + Nike specific
        EXEC ispSingaporeTradeNet @ReceiptKey
    END
    ELSE IF @Facility LIKE 'TH%'
    BEGIN
        -- Thailand + Nike specific
        EXEC ispThailandBOICheck @ReceiptKey
    END
END
ELSE IF @StorerKey LIKE 'HM%' OR @StorerKey LIKE 'H&M%'
BEGIN
    -- H&M-specific validations
    EXEC ispHMPreFinalize @ReceiptKey

    IF @Facility LIKE 'KR%'
        EXEC ispKoreaHMSpecific @ReceiptKey
    ELSE IF @Facility LIKE 'TH%'
        EXEC ispThailandHMSpecific @ReceiptKey
END
ELSE IF @StorerKey LIKE 'ADIDAS%'
BEGIN
    EXEC ispAdidasPreFinalize @ReceiptKey
    -- ... more IF-ELSE
END
ELSE IF @StorerKey LIKE 'ZARA%' OR @StorerKey LIKE 'INDITEX%'
BEGIN
    EXEC ispZaraPreFinalize @ReceiptKey
    -- ... more IF-ELSE
END
ELSE IF @StorerKey LIKE 'PUMA%'
BEGIN
    -- ... more branches
END
-- ... 50+ more ELSE IF branches for different clients
-- ... Each with nested IF-ELSE for different regions
```

### After: Modern Java

```java
// Single line replaces 200+ lines of IF-ELSE
VariationContext context = variationResolver.resolve(storerKey, facility);

// Configuration loaded automatically based on context
VariationConfig config = configService.getConfig(context);

// Plugins selected automatically based on context
pluginManager.runPrePopulate(request, context);

// Rules fire based on context
rulesEngine.validate(poFact, context);

// Write to correct database based on context
legacyBridgeService.syncReceipt(receiptKey, context);
```

### Comparison

| Aspect | Before (SP) | After (Java) |
|--------|-------------|--------------|
| Lines of code | 200+ per SP | ~10 lines |
| Client IF-ELSE | 50+ branches | 0 (plugin-based) |
| Region IF-ELSE | Nested in each client | 0 (plugin-based) |
| Adding client | Modify 20+ SPs | Add YAML + Plugin file |
| Adding region | Modify each client branch | Add region plugin |
| Testing | Integration only | Unit test each component |
| Debugging | Trace through SQL | Context logged at entry |
| Change time | 2-4 weeks | < 1 day |

---

## 10. Key Source Files

### Core Files

| Component | File Path | Purpose |
|-----------|-----------|---------|
| **VariationResolver** | `po-variation/.../context/VariationResolver.java` | Resolves context from request |
| **VariationContext** | `po-domain/.../model/VariationContext.java` | Immutable context data |
| **ConfigurationService** | `po-variation/.../config/ConfigurationService.java` | Hierarchical YAML loading |
| **PluginManager** | `po-plugin/.../registry/PluginManager.java` | Plugin selection & execution |
| **LegacyBridgeService** | `po-legacy-bridge/.../LegacyBridgeService.java` | V0/V2 database routing |
| **V0Adapter** | `po-legacy-bridge/.../adapter/V0Adapter.java` | V0 database operations |
| **V2Adapter** | `po-legacy-bridge/.../adapter/V2Adapter.java` | V2 database operations |

### Plugin Files

| Plugin | File Path | Purpose |
|--------|-----------|---------|
| **KoreaRegionPlugin** | `po-plugin/.../region/KoreaRegionPlugin.java` | Korea-specific logic |
| **SingaporeRegionPlugin** | `po-plugin/.../region/SingaporeRegionPlugin.java` | Singapore-specific logic |
| **IndiaRegionPlugin** | `po-plugin/.../region/IndiaRegionPlugin.java` | India-specific logic |
| **NikeClientPlugin** | `po-plugin/.../client/NikeClientPlugin.java` | Nike-specific logic |
| **HMClientPlugin** | `po-plugin/.../client/HMClientPlugin.java` | H&M-specific logic |
| **ZaraClientPlugin** | `po-plugin/.../client/ZaraClientPlugin.java` | Zara-specific logic |

### Configuration Files

| Config | File Path | Purpose |
|--------|-----------|---------|
| **Base Config** | `po-config/.../resources/config/base.yaml` | Default settings |
| **Korea Config** | `po-config/.../resources/config/regions/asia_kr.yaml` | Korea overrides |
| **Singapore Config** | `po-config/.../resources/config/regions/asia_sg.yaml` | Singapore overrides |
| **Nike Config** | `po-config/.../resources/config/clients/nike.yaml` | Nike overrides |
| **H&M Config** | `po-config/.../resources/config/clients/hm.yaml` | H&M overrides |

---

## Summary

The **Variation Resolver** is the central component that:

1. **Determines** version (V0/V2), region, and client from the request
2. **Creates** an immutable VariationContext that flows through the system
3. **Drives** configuration loading (hierarchical YAML merge)
4. **Selects** which plugins to execute (region + client)
5. **Routes** database writes to the correct legacy database (V0 or V2)

This replaces **200+ lines of IF-ELSE chains** in legacy stored procedures with **clean, testable, modular Java code**.

---

*Document generated: May 2024*
*PO Modernization Project - Fulfillment by Maersk*
