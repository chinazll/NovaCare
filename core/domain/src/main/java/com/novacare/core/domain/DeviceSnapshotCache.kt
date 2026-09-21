package com.novacare.core.domain

import javax.inject.Inject
import javax.inject.Singleton

/**
 * 扫描结果缓存（进程内）
 *
 * 首页扫一次，其它页面直接复用 —— 这是「≤ 2 次点击、≤ 10 秒」体验指标的落地保障。
 * 缓存只存在于内存：不持久化、不跨进程，避免拿到过期数据。
 */
@Singleton
class DeviceSnapshotCache @Inject constructor() {

    @Volatile
    var last: DeviceSnapshot? = null
        private set

    fun put(snapshot: DeviceSnapshot) {
        last = snapshot
    }

    fun clear() {
        last = null
    }
}
