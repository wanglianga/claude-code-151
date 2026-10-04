package com.example.library.service;

import com.example.library.domain.Item;
import com.example.library.domain.Library;

/**
 * 预约分配结果。
 * mode = LOCAL：本馆在架，etaHours=0；
 * mode = TRANSFER：需跨馆调拨，etaHours 为预计在途小时；
 * null 分配（Allocation 对象为 null）表示无任何可用副本，读者进入排队。
 */
public record Allocation(Item item, Library source, String mode, long etaHours) {
}
