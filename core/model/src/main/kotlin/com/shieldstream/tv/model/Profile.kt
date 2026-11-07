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
    val colors: List<Pair<Long, Long>> = listOf(
        0xFF587D64 to 0xFF1E3B32, // bosque
        0xFF4F8896 to 0xFF173E4E, // mar
        0xFF537A9E to 0xFF21344F, // océano
        0xFF7C8F67 to 0xFF354D3C, // musgo
        0xFF4B9087 to 0xFF1C4C4B, // laguna
        0xFF637E8C to 0xFF293F4C, // fiordo
        0xFF749B87 to 0xFF304F43, // eucalipto
        0xFF4A7462 to 0xFF193C32, // pino
        0xFF7A8D9B to 0xFF364652, // lluvia
        0xFF4C6076 to 0xFF1D2836, // medianoche
    )

    fun safe(index: Int): Int = index.mod(colors.size)
}
