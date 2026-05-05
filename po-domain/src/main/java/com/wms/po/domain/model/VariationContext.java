package com.wms.po.domain.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Variation context that captures the runtime variation dimensions:
 * - Version: V0 (legacy) or V2 (current)
 * - Region: ASIA-SG, ASIA-KR, ASIA-TH, ASIA-IN
 * - Client: NIKE, HM, ZARA, STANDARD
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class VariationContext {

    private String version;           // "V0" or "V2"
    private String region;            // "ASIA-SG", "ASIA-KR", "ASIA-TH", "ASIA-IN"
    private String client;            // "NIKE", "HM", "ZARA", "STANDARD"
    private String facility;          // "KR01", "SG02", etc.
    private String storerKey;         // Client identifier
    private boolean dualWriteEnabled; // Whether to write to legacy system

    @JsonIgnore
    public boolean isV0() {
        return "V0".equals(version);
    }

    @JsonIgnore
    public boolean isV2() {
        return "V2".equals(version);
    }

    @JsonIgnore
    public boolean isKorea() {
        return "ASIA-KR".equals(region);
    }

    @JsonIgnore
    public boolean isSingapore() {
        return "ASIA-SG".equals(region);
    }

    @JsonIgnore
    public boolean isThailand() {
        return "ASIA-TH".equals(region);
    }

    @JsonIgnore
    public boolean isIndia() {
        return "ASIA-IN".equals(region);
    }

    @JsonIgnore
    public boolean isNike() {
        return "NIKE".equals(client);
    }

    @JsonIgnore
    public boolean isHM() {
        return "HM".equals(client);
    }

    @JsonIgnore
    public boolean isStandard() {
        return "STANDARD".equals(client);
    }
}
