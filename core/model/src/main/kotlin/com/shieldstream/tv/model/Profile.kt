package com.shieldstream.tv.model

data class Profile(
    val id: String,
    val name: String,
    val avatar: Int,
    val traktUser: String? = null,
    val pinHash: String? = null,
    val kids: Boolean = false,
    val spanishFirst: Boolean = kids || !Lang.isEnglish,
    val spanishOnlySources: Boolean = kids,
    val hiddenCategories: Set<String> = Category.DEFAULT_HIDDEN,
    val subtitlePreference: SubtitlePreference = SubtitlePreference.OFF,
    val experimentalSubtitleSync: Boolean = true,
) {
    val initial: String get() = name.trim().firstOrNull()?.uppercase() ?: "?"

    val locked: Boolean get() = pinHash != null

    companion object {
        const val MAX = 5

        const val DEFAULT_ID = "default"
    }
}

object ProfilePin {
    const val LENGTH = 4

    fun hash(profileId: String, pin: String): String {
        val bytes = java.security.MessageDigest.getInstance("SHA-256").digest("bambi-pin:$profileId:$pin".toByteArray())
        return bytes.joinToString("") { "%02x".format(it) }
    }

    fun matches(profile: Profile, pin: String): Boolean = profile.pinHash == null || profile.pinHash == hash(profile.id, pin)
}

object ProfileManagement {
    fun pinProfile(profiles: List<Profile>): Profile? =
        profiles.firstOrNull { !it.kids && it.locked } ?: profiles.firstOrNull { it.locked }
}

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
