package io.github.arnavdugad.flick.feedback

import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager

/** Short, crisp haptic confirmations. Never throws: haptics are a nicety, not a requirement. */
class Haptics(context: Context) {

    private enum class Kind(val fallbackMs: Long) { CONFIRM(18), TICK(8) }

    private val vibrator: Vibrator? =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            context.getSystemService(VibratorManager::class.java)?.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            context.getSystemService(Vibrator::class.java)
        }

    /** A gesture was recognised and its action dispatched. */
    fun confirm() = play(Kind.CONFIRM)

    /** Something was recognised but nothing ran (e.g. practice mode). */
    fun tick() = play(Kind.TICK)

    private fun play(kind: Kind) {
        val v = vibrator ?: return
        if (!v.hasVibrator()) return
        runCatching {
            val effect = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                VibrationEffect.createPredefined(
                    when (kind) {
                        Kind.CONFIRM -> VibrationEffect.EFFECT_CLICK
                        Kind.TICK -> VibrationEffect.EFFECT_TICK
                    },
                )
            } else {
                VibrationEffect.createOneShot(kind.fallbackMs, VibrationEffect.DEFAULT_AMPLITUDE)
            }
            v.vibrate(effect)
        }
    }
}
