package com.zaze.server.common.utils

import com.zaze.server.common.log.ZLog
import java.util.HashMap

object TraceHelper {
    private var ENABLED = false
    private const val SYSTEM_TRACE = false
    private val sUpTimes: MutableMap<String, Long> = HashMap()
    private const val TAG = "TraceHelper"

    fun enable(enable: Boolean) {
        ENABLED = enable
    }

    fun beginSection(sectionName: String) {
        if (ENABLED) {
//            if (SYSTEM_TRACE) {
//                Trace.beginSection(sectionName)
//            }
            sUpTimes[sectionName] = System.currentTimeMillis()
        }
    }

    fun partitionSection(sectionName: String, partition: String) {
        if (ENABLED) {
            val time = sUpTimes[sectionName]
            if (time != null && time >= 0) {
//                if (SYSTEM_TRACE) {
//                    Trace.endSection()
//                    Trace.beginSection(sectionName)
//                }
                sUpTimes[sectionName] = System.currentTimeMillis()
                ZLog.d(
                    TAG,
                    sectionName + ">> " + partition + " : " + (System.currentTimeMillis() - time)
                )
            }
        }
    }

    fun endSection(sectionName: String) {
        if (ENABLED) {
            endSection(sectionName, "finish")
        }
    }

    fun endSection(sectionName: String, msg: String) {
        if (ENABLED) {
            val time = sUpTimes.remove(sectionName)
            if (time != null && time >= 0) {
//                if (SYSTEM_TRACE) {
//                    Trace.endSection()
//                }
                ZLog.d(TAG, sectionName + ">> " + msg + " : " + (System.currentTimeMillis() - time))
            }
        }
    }
}