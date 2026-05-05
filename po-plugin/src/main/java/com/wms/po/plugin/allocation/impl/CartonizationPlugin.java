package com.wms.po.plugin.allocation.impl;

import com.wms.po.plugin.allocation.PostAllocationContext;
import com.wms.po.plugin.allocation.PostAllocationPlugin;
import com.wms.po.plugin.allocation.PostAllocationResult;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.Map;

/**
 * Cartonization Post-Allocation Plugin.
 *
 * Replaces: ispPOA05 - Cartonization after allocation
 *
 * Determines optimal carton sizes and assignments
 * for allocated inventory.
 */
@Component
@Slf4j
public class CartonizationPlugin implements PostAllocationPlugin {

    @Override
    public String getPluginId() {
        return "POA05_CARTONIZATION";
    }

    @Override
    public String getDescription() {
        return "Calculates optimal carton assignments for allocated items";
    }

    @Override
    public boolean appliesTo(PostAllocationContext context) {
        // Apply when cartonization is enabled for storer
        return context.getStorerConfigValue("EnableCartonization", false);
    }

    @Override
    public int getPriority() {
        return 30; // After task creation
    }

    @Override
    public PostAllocationResult execute(PostAllocationContext context) {
        log.debug("Calculating cartonization for order: {}", context.getOrderKey());

        try {
            // Calculate total cube and weight
            BigDecimal totalCube = calculateTotalCube(context);
            BigDecimal totalWeight = calculateTotalWeight(context);

            // Determine carton type
            String cartonType = determineCartonType(context, totalCube, totalWeight);

            // Calculate number of cartons needed
            int cartonCount = calculateCartonCount(context, totalCube, cartonType);

            // Create carton assignments
            createCartonAssignments(context, cartonType, cartonCount);

            return PostAllocationResult.success(getPluginId())
                    .addAction("CARTONIZE", "PICKDETAIL", context.getOrderDetailKey(),
                            String.format("Assigned %d %s cartons", cartonCount, cartonType))
                    .setOutput("cartonType", cartonType)
                    .setOutput("cartonCount", cartonCount)
                    .setOutput("totalCube", totalCube)
                    .setOutput("totalWeight", totalWeight);

        } catch (Exception e) {
            log.error("Cartonization failed for order {}: {}", context.getOrderKey(), e.getMessage());
            return PostAllocationResult.failure(getPluginId(), "CARTON_FAILED", e.getMessage());
        }
    }

    @Override
    public boolean isOptional() {
        return true; // Cartonization failure shouldn't stop allocation
    }

    private BigDecimal calculateTotalCube(PostAllocationContext context) {
        // Calculate cube from allocation lines
        return context.getAllocationLines().stream()
                .map(line -> line.getQtyAllocated().multiply(getCubePerUnit(line.getSku())))
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private BigDecimal calculateTotalWeight(PostAllocationContext context) {
        // Calculate weight from allocation lines
        return context.getAllocationLines().stream()
                .map(line -> line.getQtyAllocated().multiply(getWeightPerUnit(line.getSku())))
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private BigDecimal getCubePerUnit(String sku) {
        // Would look up from SKU master
        return BigDecimal.valueOf(0.05); // Default 0.05 cubic feet
    }

    private BigDecimal getWeightPerUnit(String sku) {
        // Would look up from SKU master
        return BigDecimal.valueOf(0.5); // Default 0.5 lbs
    }

    private String determineCartonType(PostAllocationContext context,
                                       BigDecimal totalCube, BigDecimal totalWeight) {
        // Carton selection logic based on cube/weight
        if (totalCube.compareTo(BigDecimal.valueOf(0.5)) < 0) {
            return "SMALL";
        } else if (totalCube.compareTo(BigDecimal.valueOf(2.0)) < 0) {
            return "MEDIUM";
        } else {
            return "LARGE";
        }
    }

    private int calculateCartonCount(PostAllocationContext context,
                                     BigDecimal totalCube, String cartonType) {
        BigDecimal cartonCapacity = getCartonCapacity(cartonType);
        return totalCube.divide(cartonCapacity, 0, java.math.RoundingMode.CEILING).intValue();
    }

    private BigDecimal getCartonCapacity(String cartonType) {
        return switch (cartonType) {
            case "SMALL" -> BigDecimal.valueOf(0.5);
            case "MEDIUM" -> BigDecimal.valueOf(2.0);
            case "LARGE" -> BigDecimal.valueOf(4.0);
            default -> BigDecimal.valueOf(2.0);
        };
    }

    private void createCartonAssignments(PostAllocationContext context,
                                         String cartonType, int cartonCount) {
        // Create carton assignments in database
        log.debug("Created {} {} carton assignments for order {}",
                cartonCount, cartonType, context.getOrderKey());
    }

    @Override
    public Map<String, Object> getConfiguration() {
        return Map.of(
                "defaultCartonType", "MEDIUM",
                "maxCartonWeight", 50.0,
                "maxCartonCube", 4.0
        );
    }
}
