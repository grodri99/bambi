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
    val subtitleAppearance: SubtitleAppearance = SubtitleAppearance(),
    val experimentalSubtitleSync: Boolean = true,
    val matchContentFrameRate: Boolean = true,
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
    val colors: List<Triple<Long, Long, Long>> = listOf(
        Triple(0xFFFF6B6B, 0xFFE11D48, 0xFF881337), // rojo
        Triple(0xFF60A5FA, 0xFF2563EB, 0xFF1E3A8A), // azul
        Triple(0xFF6EE7B7, 0xFF059669, 0xFF065F46), // verde
        Triple(0xFFFBBF24, 0xFFD97706, 0xFF78350F), // amarillo
        Triple(0xFFC4B5FD, 0xFF8B5CF6, 0xFF5B21B6), // morado
        Triple(0xFFFDBA74, 0xFFF97316, 0xFFC2410C), // naranja
        Triple(0xFF67E8F9, 0xFF0891B2, 0xFF155E75), // celeste
        Triple(0xFFF9A8D4, 0xFFEC4899, 0xFF9D174D), // rosa
        Triple(0xFF99F6E4, 0xFF14B8A6, 0xFF0F766E), // menta
        Triple(0xFFCBD5E1, 0xFF64748B, 0xFF1E293B), // gris
    )

    fun safe(index: Int): Int = index.mod(colors.size)
}
