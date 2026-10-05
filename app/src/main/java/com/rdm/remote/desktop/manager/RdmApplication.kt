package com.rdm.remote.desktop.manager

import android.app.Application
import com.rdm.remote.desktop.manager.data.database.AppDatabase
import com.rdm.remote.desktop.manager.data.repository.RdmRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class RdmApplication : Application() {

    lateinit var database: AppDatabase
        private set

    lateinit var repository: RdmRepository
        private set

    override fun onCreate() {
        super.onCreate()
        database = AppDatabase.getInstance(this)
        repository = RdmRepository(database)

        // Preload sample data if database is empty on first launch
        CoroutineScope(Dispatchers.IO).launch {
            repository.checkAndInitSampleData()
        }
    }
}
