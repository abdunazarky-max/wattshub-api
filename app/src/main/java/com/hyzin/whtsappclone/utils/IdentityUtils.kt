package com.hyzin.whtsappclone.utils

import kotlin.random.Random

object IdentityUtils {
    private val adjectives = listOf(
        "Neon", "Cyber", "Silent", "Vibrant", "Shadow", "Alpha", "Zen", "Hyper", "Solar", "Lunar",
        "Cosmic", "Azure", "Golden", "Crimson", "Swift", "Mystic", "Electric", "Storm", "Phoenix", "Nova",
        "Turbo", "Laser", "Phantom", "Cobalt", "Sleek", "Stealth", "Infinite", "Gravity", "Plasma", "Aether"
    )

    private val nouns = listOf(
        "Wolf", "Falcon", "Eagle", "Cobra", "Dragon", "Tiger", "Titan", "Ghost", "Knight", "Racer",
        "Rider", "Pilot", "Sentry", "Vanguard", "Edge", "Blade", "Striker", "Pulse", "Spark", "Orbit",
        "Horizon", "Vault", "Helix", "Cipher", "Apex", "Zenith", "Flux", "Core", "Vector", "Echo"
    )

    fun generateSecretIdentity(): String {
        val adjective = adjectives.random()
        val noun = nouns.random()
        val number = Random.nextInt(1000, 9999)
        return "$adjective-$noun-$number"
    }
}
