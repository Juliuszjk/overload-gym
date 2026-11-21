package com.apollo.overload

import android.app.Application
import com.apollo.overload.data.GymDb
import com.apollo.overload.data.Repository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async

class OverloadApp : Application() {
    val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    lateinit var repo: Repository
        private set
    /** Completes when the exercise DB and starting plan are in place. */
    lateinit var ready: Deferred<Unit>
        private set

    override fun onCreate() {
        super.onCreate()
        repo = Repository(this, GymDb.create(this))
        ready = appScope.async { repo.init() }
    }
}
