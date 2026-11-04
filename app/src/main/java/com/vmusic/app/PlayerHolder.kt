package com.vmusic.app

import android.content.Context
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.audio.AudioSink
import androidx.media3.exoplayer.audio.DefaultAudioSink
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService

@UnstableApi
object PlayerHolder {
    lateinit var player: ExoPlayer
    val bass = BassProcessor()
    val eq = EqProcessor()
    @Volatile var safeMode = false

    /** Called when playback fails with a runtime error: turn custom audio processing + effects off and persist it. */
    fun enterSafeMode(ctx: Context) {
        safeMode = true; bass.off = true; eq.off = true
        ctx.getSharedPreferences("vm", 0).edit().putBoolean("safe_audio", true).apply()
        FxHolder.release()
    }

    fun init(ctx: Context) {
        if (::player.isInitialized) return
        safeMode = ctx.getSharedPreferences("vm", 0).getBoolean("safe_audio", false)
        bass.off = safeMode; eq.off = safeMode
        FxHolder.load(ctx); ShortCfg.load(ctx); Store.load(ctx)
        val rf = object : DefaultRenderersFactory(ctx) {
            override fun buildAudioSink(c: Context, f: Boolean, p: Boolean): AudioSink =
                DefaultAudioSink.Builder(c).setAudioProcessors(arrayOf(bass, eq)).build()
        }
        player = ExoPlayer.Builder(ctx, rf)
            .setAudioAttributes(AudioAttributes.Builder().setUsage(C.USAGE_MEDIA)
                .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC).build(), true)
            .setHandleAudioBecomingNoisy(true)
            .build()
        player.addListener(object : androidx.media3.common.Player.Listener {
            override fun onAudioSessionIdChanged(id: Int) { FxHolder.attach(id) }
        })
        FxHolder.attach(player.audioSessionId)
        FxHolder.apply()
    }
}

@UnstableApi
class PlaybackService : MediaSessionService() {
    private var session: MediaSession? = null
    override fun onCreate() {
        super.onCreate()
        PlayerHolder.init(applicationContext)
        session = MediaSession.Builder(this, PlayerHolder.player).build()
    }
    override fun onGetSession(c: MediaSession.ControllerInfo) = session
    override fun onDestroy() { session?.release(); super.onDestroy() }
}
