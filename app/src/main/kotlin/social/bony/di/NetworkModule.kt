package social.bony.di

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import social.bony.nostr.relay.RelayConnection
import social.bony.nostr.relay.RelayPool
import social.bony.settings.AppSettings
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object NetworkModule {

    @Provides
    @Singleton
    fun provideApplicationScope(): CoroutineScope =
        CoroutineScope(SupervisorJob() + Dispatchers.IO)

    @Provides
    @Singleton
    fun provideRelayPool(
        scope: CoroutineScope,
        appSettings: AppSettings,
    ): RelayPool {
        val pool = RelayPool(
            scope = scope,
            connectionFactory = { url ->
                RelayConnection(url, RelayConnection.buildClient(appSettings.torEnabled.value))
            },
        )
        // Observe tor toggle and reconnect relays with the appropriate transport.
        // Initial emission applies the setting before any relay has connected.
        scope.launch {
            appSettings.torEnabled.collect { useTor ->
                pool.updateTransport(RelayConnection.buildClient(useTor))
            }
        }
        return pool
    }
}
