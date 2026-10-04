package com.citylibrary.pll.domain;

/** 调拨：CREATED 待出库 -> PICKED_UP 已取书出库 -> ARRIVED 到馆；任何阶段可 LOST；未出库可 CANCELLED。 */
public enum TransferStatus { CREATED, PICKED_UP, ARRIVED, LOST, CANCELLED }
