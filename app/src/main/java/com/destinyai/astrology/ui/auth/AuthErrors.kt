package com.destinyai.astrology.ui.auth

/**
 * Thrown when a guest session that has been archived (409 detail.error="archived_guest") is used.
 *
 * iOS parity (QuotaManager.swift:9-19 ArchivedGuestError):
 * carries upgraded_to_email + provider so the UI can render the friendly
 * "Your guest account was upgraded to <email>" message keyed off the provider.
 */
class ArchivedGuestError(
    val upgradedToEmail: String? = null,
    val provider: String? = null,
    msg: String = "archived_guest",
) : Exception(msg)

/**
 * Thrown when a guest's birth data matches an existing registered user
 * (409 detail.error="registered_user_conflict").
 *
 * iOS parity (QuotaManager.swift:23-44 RegisteredUserConflictError):
 * carries masked_email + provider so the GuestSignInPromptView can show the
 * provider-specific friendly message ("Sign in with Apple" vs "…Google" etc.).
 * [conflictEmail] retained as deprecated alias for legacy call sites.
 */
class RegisteredUserConflictError(
    val maskedEmail: String? = null,
    val provider: String? = null,
) : Exception("registered_user_conflict") {
    @Deprecated("Use maskedEmail", ReplaceWith("maskedEmail"))
    val conflictEmail: String get() = maskedEmail.orEmpty()
}

/**
 * Thrown when a soft-deleted account tries to sign in or register
 * (403 detail.error="account_deleted").
 *
 * iOS parity (QuotaManager.swift:48-54 AccountDeletedError): carries the
 * server-supplied message string so the UI can show the canonical wording.
 */
class AccountDeletedError(
    val serverMessage: String? = null,
) : Exception(serverMessage ?: "account_deleted")

/**
 * Thrown when birth data already belongs to another registered user
 * (POST /subscription/profile 409 detail.error="birth_data_taken").
 *
 * iOS parity (QuotaManager.swift:58-79 BirthDataTakenError):
 * carries existing_email + provider so the GuestSignInPromptView can render
 * the provider-specific "This birth data is linked to <email>" message.
 */
class BirthDataTakenError(
    val existingEmail: String? = null,
    val provider: String? = null,
) : Exception("birth_data_taken")
