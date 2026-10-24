package app.cheshers.feature.game.board

import android.media.AudioAttributes
import android.media.SoundPool
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import app.cheshers.feature.game.GameSound
import app.cheshers.feature.game.R

class GameSoundPlayer(private val soundPool: SoundPool, private val sounds: Map<GameSound, Int>) {
    fun play(sound: GameSound, enabled: Boolean) {
        if (enabled) sounds[sound]?.let { soundPool.play(it, 0.7f, 0.7f, 1, 0, 1f) }
    }
    fun release() = soundPool.release()
}

@Composable
fun rememberGameSoundPlayer(): GameSoundPlayer {
    val context = LocalContext.current
    val player = remember {
        val pool = SoundPool.Builder().setMaxStreams(4).setAudioAttributes(
            AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_GAME).setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build(),
        ).build()
        GameSoundPlayer(pool, mapOf(
            GameSound.MOVE to pool.load(context, R.raw.move, 1),
            GameSound.CAPTURE to pool.load(context, R.raw.capture, 1),
            GameSound.CHECK to pool.load(context, R.raw.check, 1),
            GameSound.CASTLE to pool.load(context, R.raw.castle, 1),
            GameSound.END to pool.load(context, R.raw.game_end, 1),
        ))
    }
    DisposableEffect(player) { onDispose { player.release() } }
    return player
}
