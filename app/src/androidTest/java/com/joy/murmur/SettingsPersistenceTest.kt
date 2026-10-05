package com.joy.murmur

import android.content.Context
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.joy.murmur.data.settings.DataStoreSettingsRepository
import com.joy.murmur.domain.settings.AppearanceMode
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.util.UUID

@RunWith(AndroidJUnit4::class)
class SettingsPersistenceTest {
    @Test
    fun appearanceModeSurvivesRepositoryRecreation() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val file = File(context.cacheDir, "settings-${UUID.randomUUID()}.preferences_pb")
        val firstScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        val firstStore = PreferenceDataStoreFactory.create(
            scope = firstScope,
            produceFile = { file },
        )

        DataStoreSettingsRepository(firstStore).setAppearanceMode(AppearanceMode.DARK)
        firstScope.cancel()

        val secondScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        try {
            val secondStore = PreferenceDataStoreFactory.create(
                scope = secondScope,
                produceFile = { file },
            )
            assertEquals(
                AppearanceMode.DARK,
                DataStoreSettingsRepository(secondStore).appearanceMode.first(),
            )
        } finally {
            secondScope.cancel()
            file.delete()
        }
    }
}
