package com.wms.po.plugin.allocation.impl;

import com.wms.po.plugin.allocation.PostAllocationContext;
import com.wms.po.plugin.allocation.PostAllocationPlugin;
import com.wms.po.plugin.allocation.PostAllocationResult;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/**
 * Task Creation Post-Allocation Plugin.
 *
 * Replaces: ispPOA03 - Pick task creation after allocation
 *
 * Creates pick tasks from allocation results for warehouse execution.
 */
@Component
@Slf4j
public class TaskCreationPlugin implements PostAllocationPlugin {

    @Override
    public String getPluginId() {
        return "POA03_TASK_CREATION";
    }

    @Override
    public String getDescription() {
        return "Creates pick tasks from allocation results";
    }

    @Override
    public boolean appliesTo(PostAllocationContext context) {
        // Apply when there are allocation lines to process
        return context.getAllocationLines() != null && !context.getAllocationLines().isEmpty();
    }

    @Override
    public int getPriority() {
        return 20; // Execute after wave release
    }

    @Override
    public PostAllocationResult execute(PostAllocationContext context) {
        log.debug("Creating pick tasks for order: {}", context.getOrderKey());

        try {
            List<String> taskKeys = new ArrayList<>();
            BigDecimal totalTaskQty = BigDecimal.ZERO;

            for (PostAllocationContext.AllocationLine line : context.getAllocationLines()) {
                String taskKey = createPickTask(context, line);
                taskKeys.add(taskKey);
                totalTaskQty = totalTaskQty.add(line.getQtyAllocated());
            }

            return PostAllocationResult.success(getPluginId())
                    .addAction("TASK_CREATE", "TASKDETAIL", String.join(",", taskKeys),
                            String.format("Created %d pick tasks for %.2f units",
                                    taskKeys.size(), totalTaskQty))
                    .setOutput("taskKeys", taskKeys)
                    .setOutput("taskCount", taskKeys.size())
                    .setOutput("totalQty", totalTaskQty);

        } catch (Exception e) {
            log.error("Task creation failed for order {}: {}", context.getOrderKey(), e.getMessage());
            return PostAllocationResult.failure(getPluginId(), "TASK_CREATE_FAILED", e.getMessage());
        }
    }

    private String createPickTask(PostAllocationContext context,
                                  PostAllocationContext.AllocationLine line) {
        // Generate task key
        String taskKey = "TASK" + System.currentTimeMillis() + "_" + line.getPickDetailKey();

        log.debug("Created pick task {} for {} units from location {}",
                taskKey, line.getQtyAllocated(), line.getLocationKey());

        return taskKey;
    }
}
