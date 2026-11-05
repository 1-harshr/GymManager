package com.harsh.gymmanager.domain

class OverpaymentException(val amountDue: Long) :
    IllegalArgumentException("Payment exceeds the amount due of ₹$amountDue")
