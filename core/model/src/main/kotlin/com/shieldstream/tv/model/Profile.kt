package com.shieldstream.tv.model

/**
 * Perfil de la app (como en Netflix): cada uno tiene su "Seguir viendo", sus vistos y, si quiere, su cuenta de
 * Trakt para sincronizar todo eso entre las dos Shields.
 *
 * [avatar] es el índice de [ProfileAvatars] (color del avatar). [traktUser] es solo para mostrar ("@usuario");
 * los tokens de Trakt van cifrados aparte (TokenStore).
 */
data class Profile(
    val id: String,
    val name: String,
    val avatar: Int,
    val traktUser: String? = null,
    /** PIN de 4 cifras para entrar (hash, nunca el PIN): null = sin PIN. Ver [ProfilePin]. */
    val pinHash: String? = null,
    /** Perfil infantil: Inicio, categorías y búsqueda solo con contenido para niños. */
    val kids: Boolean = false,
    /** Preferencia de audio del perfil, independiente del idioma de los menús. */
    val spanishFirst: Boolean = kids || !Lang.isEnglish,
    /** Se aplica tanto a Reproducir como a la cola de alternativas y al panel de fuentes. */
    val spanishOnlySources: Boolean = kids,
    /** Botones de categoría ocultos en el Inicio de este perfil (nombres de Category). */
    val hiddenCategories: Set<String> = Category.DEFAULT_HIDDEN,
    /** Idioma de subtítulos automático; OFF no cambia la selección normal del reproductor. */
    val subtitlePreference: SubtitlePreference = SubtitlePreference.OFF,
) {
    val initial: String get() = name.trim().firstOrNull()?.uppercase() ?: "?"

    val locked: Boolean get() = pinHash != null

    companion object {
        /** Como Netflix: hasta 5 perfiles. */
        const val MAX = 5

        /**
         * Perfil que se crea solo la primera vez. Usa el progreso que ya había antes de existir los perfiles
         * (mismo archivo), así nadie pierde su "Seguir viendo" al actualizar.
         */
        const val DEFAULT_ID = "default"
    }
}

/**
 * PIN de perfil (como el de Netflix o Nuvio): 4 cifras. Se guarda solo un hash con el id del perfil, así el mismo
 * PIN en dos perfiles no da el mismo valor y en el archivo de ajustes no se lee el PIN.
 */
object ProfilePin {
    const val LENGTH = 4

    fun hash(profileId: String, pin: String): String {
        val bytes = java.security.MessageDigest.getInstance("SHA-256").digest("bambi-pin:$profileId:$pin".toByteArray())
        return bytes.joinToString("") { "%02x".format(it) }
    }

    fun matches(profile: Profile, pin: String): Boolean = profile.pinHash == null || profile.pinHash == hash(profile.id, pin)
}

/** PIN que autoriza administrar perfiles. Preferir un perfil adulto; aceptar cualquier PIN como fallback. */
object ProfileManagement {
    fun pinProfile(profiles: List<Profile>): Profile? =
        profiles.firstOrNull { !it.kids && it.locked } ?: profiles.firstOrNull { it.locked }
}

/** Colores de los avatares: [primero, segundo] del degradado. */
object ProfileAvatars {
    val colors: List<Pair<Long, Long>> = listOf(
        0xFFE50914 to 0xFF8C0610, // rojo
        0xFF2F80ED to 0xFF1B4C95, // azul
        0xFF27AE60 to 0xFF14683A, // verde
        0xFFF2C94C to 0xFFC08A12, // amarillo
        0xFF9B51E0 to 0xFF5B2A8C, // morado
        0xFFF2994A to 0xFFB45A17, // naranja
        0xFF56CCF2 to 0xFF2179A0, // celeste
        0xFFEB5794 to 0xFF9B2459, // rosa
        0xFF6FCF97 to 0xFF2F7F57, // menta
        0xFF828282 to 0xFF3D3D3D, // gris
    )

    fun safe(index: Int): Int = index.mod(colors.size)
}
