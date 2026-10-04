package com.example.library.web;

import com.example.library.service.TransferService;
import com.example.library.web.dto.*;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/transfers")
@Tag(name = "3. 跨馆调拨物流", description = "出库批次/包裹编号/ETA、到馆签收、丢包结案")
public class TransferController {

    private final TransferService transfers;

    public TransferController(TransferService transfers) {
        this.transfers = transfers;
    }

    @GetMapping("/tasks/pending")
    @Operation(summary = "物流员待出库任务列表")
    public List<TaskView> pending() {
        return transfers.pendingPickupViews();
    }

    @PostMapping("/pickups")
    @Operation(summary = "物流员取书出库：记录出库馆、目的馆、批次、包裹编号、ETA，并将馆藏置为调拨中")
    public BatchView pickup(@Valid @RequestBody PickupRequest req) {
        return transfers.pickupView(req.taskIds(), req.staffName());
    }

    @PostMapping("/arrivals")
    @Operation(summary = "按包裹编号签收到馆：入预约架通知读者；预约已失效则释放给队列下一位")
    public TaskView arrive(@Valid @RequestBody ArriveRequest req) {
        return transfers.arriveView(req.packageNo());
    }

    @PostMapping("/tasks/{taskId}/lost")
    @Operation(summary = "登记物流丢包：馆藏置丢失、生成物流赔偿单、为读者保位并尝试重新调拨")
    public TaskView lost(@PathVariable Long taskId, @RequestBody(required = false) LostTaskRequest req) {
        return transfers.reportLostView(taskId, req == null ? null : req.note());
    }

    @GetMapping("/tasks/{taskId}")
    @Operation(summary = "查看单个调拨任务")
    public TaskView task(@PathVariable Long taskId) {
        return transfers.taskView(taskId);
    }

    @GetMapping("/batches/{batchId}/tasks")
    @Operation(summary = "查看批次内所有包裹")
    public List<TaskView> batchTasks(@PathVariable Long batchId) {
        return transfers.batchTaskViews(batchId);
    }

    @GetMapping("/batches/open")
    @Operation(summary = "查看开放中的物流批次")
    public List<BatchView> openBatches() {
        return transfers.openBatchViews();
    }

    @PostMapping("/batches/{batchId}/close")
    @Operation(summary = "批次内包裹全部结案后关闭批次")
    public BatchView close(@PathVariable Long batchId) {
        return transfers.closeBatchView(batchId);
    }
}
