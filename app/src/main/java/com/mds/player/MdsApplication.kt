package com.mds.player

import android.app.Application
import com.mds.player.data.MdsRepository
import com.mds.player.playback.PlaybackManager

class MdsApplication : Application() {

    lateinit var repository: MdsRepository
        private set

    lateinit var playback: PlaybackManager
        private set

    override fun onCreate() {
        super.onCreate()
        repository = MdsRepository(this)
        playback = PlaybackManager(this)
    }
}
