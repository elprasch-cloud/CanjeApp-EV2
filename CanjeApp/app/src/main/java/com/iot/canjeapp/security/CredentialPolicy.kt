package com.iot.canjeapp.security

/**
 * Política de credenciales de la aplicación (las "indicaciones" de usuario y
 * contraseña que se muestran en la pantalla de registro).
 *
 * Usuario legible:  formato nombre.apellido, 5–30 caracteres, minúsculas,
 *                   números y un punto (ej.: "matias.vargas").
 * Contraseña:       mínimo 8 caracteres, con al menos una mayúscula, una
 *                   minúscula, un número y un carácter especial.
 */
object CredentialPolicy {

    const val MIN_PASSWORD_LENGTH = 8

    private val USERNAME_REGEX = Regex("^[a-z]+\\.[a-z0-9]+$")

    fun normalizeUsername(raw: String): String = raw.trim().lowercase()

    fun isValidUsername(username: String): Boolean =
        username.length in 5..30 && USERNAME_REGEX.matches(username)

    /** Devuelve null si la contraseña es válida, o el motivo del rechazo. */
    fun passwordProblem(password: String): String? = when {
        password.length < MIN_PASSWORD_LENGTH -> "Debe tener al menos $MIN_PASSWORD_LENGTH caracteres"
        password.none { it.isUpperCase() } -> "Debe incluir al menos una letra mayúscula"
        password.none { it.isLowerCase() } -> "Debe incluir al menos una letra minúscula"
        password.none { it.isDigit() } -> "Debe incluir al menos un número"
        password.all { it.isLetterOrDigit() } -> "Debe incluir al menos un carácter especial (#, @, !, …)"
        else -> null
    }
}
