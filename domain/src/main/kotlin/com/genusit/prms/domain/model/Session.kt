package com.genusit.prms.domain.model

/** The signed-in user as kept on the device. */
data class Session(val token: String, val role: Role, val name: String)

/** Result of a sign-in attempt that reached the server and was accepted. */
sealed interface LoginOutcome {
    data class SignedIn(val session: Session) : LoginOutcome
    /** Super Admin (or an unknown role) – these accounts work in the web panel only. */
    data object UseWebPanel : LoginOutcome
}
