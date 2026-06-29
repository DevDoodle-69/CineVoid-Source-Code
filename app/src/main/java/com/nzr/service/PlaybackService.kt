package com.nzr.service

import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.trackselection.DefaultTrackSelector
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService

class PlaybackService : MediaSessionService() {
    private var mediaSession: MediaSession? = null

    override fun onCreate() {
        super.onCreate()
        
        val context = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
            createAttributionContext("playback")
        } else {
            this
        }
        
        val selector = DefaultTrackSelector(context)
        selector.setParameters(
            selector.buildUponParameters().setPreferredAudioLanguage("en")
        )
        
        val audioAttributes = AudioAttributes.Builder()
            .setUsage(C.USAGE_MEDIA)
            .setContentType(C.AUDIO_CONTENT_TYPE_MOVIE)
            .build()
            
        val loadControl = DefaultLoadControl.Builder()
            .setBufferDurationsMs(32000, 64000, 2500, 5000)
            .build()
            
        val player = ExoPlayer.Builder(context)
            .setTrackSelector(selector)
            .setLoadControl(loadControl)
            .setAudioAttributes(audioAttributes, true)
            .setHandleAudioBecomingNoisy(true)
            .build()
            
        mediaSession = MediaSession.Builder(context, player).build()
    }

    override fun onTaskRemoved(rootIntent: android.content.Intent?) {
        val player = mediaSession?.player
        if (player != null) {
            if (!player.playWhenReady || player.mediaItemCount == 0 || player.playbackState == androidx.media3.common.Player.STATE_ENDED) {
                stopSelf()
            }
        }
        super.onTaskRemoved(rootIntent)
    }

    override fun onDestroy() {
        mediaSession?.player?.release()
        mediaSession?.release()
        mediaSession = null
        super.onDestroy()
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? = mediaSession
}
