package com.harsh.gymmanager.data.local.entity

/** How many times a value (a plan id, an amount, a length) was used; read-only, not a table. */
data class UsageRow(val value: Long, val uses: Int)
