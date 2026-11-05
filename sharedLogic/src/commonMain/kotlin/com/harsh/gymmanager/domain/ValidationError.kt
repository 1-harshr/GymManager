package com.harsh.gymmanager.domain

/** Why a model was rejected. The UI turns each into words in the person's language. */
enum class ValidationError {
    NAME_REQUIRED,
    PLAN_NAME_REQUIRED,
    GYM_NAME_REQUIRED,
    DURATION_TOO_SHORT,
    PRICE_NEGATIVE,
    DISCOUNT_TOO_LARGE,
    END_BEFORE_START,
    AMOUNT_NOT_POSITIVE,
}

class ValidationException(val error: ValidationError) : IllegalArgumentException(error.name)

/** Like `require`, but carries a [ValidationError] instead of an English sentence. */
internal fun ensure(condition: Boolean, error: ValidationError) {
    if (!condition) throw ValidationException(error)
}
