package com.harsh.gymmanager.domain

/** The gym's data online couldn't be reached, usually for want of an internet connection. */
class NoConnectionException(cause: Throwable? = null) : Exception("No connection", cause)
