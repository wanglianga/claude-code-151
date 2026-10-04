package com.citylibrary.pll.domain;

/**
 * 预约状态机：
 * QUEUED 排队中（可能已锁码待调拨，reserved_copy 决定）
 *   -> READY（到馆待取）-> FULFILLED（借出）
 *   -> EXPIRED（超时未取释放）/ CANCELLED（读者取消、闭馆取消）
 */
public enum HoldStatus { QUEUED, READY, FULFILLED, EXPIRED, CANCELLED }
