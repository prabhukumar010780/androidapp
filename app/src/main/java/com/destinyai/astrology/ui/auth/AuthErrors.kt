package com.destinyai.astrology.ui.auth

/** Thrown when a guest session that has been archived (403 from backend) is used. */
class ArchivedGuestError(msg: String) : Exception(msg)

/** Thrown when birth data conflicts with an existing registered account (409). */
class RegisteredUserConflictError(val conflictEmail: String) : Exception("birth_data_taken")

/** Thrown when an account has been permanently deleted. */
class AccountDeletedError : Exception("account_deleted")
