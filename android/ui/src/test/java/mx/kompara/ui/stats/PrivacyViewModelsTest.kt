package mx.kompara.ui.stats

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.*
import androidx.lifecycle.*
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.work.*
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.*
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.test.*
import kotlinx.serialization.json.Json
import mx.kompara.billing.*
import mx.kompara.data.accounting.*
import mx.kompara.data.db.KomparaDatabase
import mx.kompara.data.db.entity.*
import mx.kompara.data.model.City
import mx.kompara.data.service.*
import mx.kompara.data.settings.*
import mx.kompara.sync.aggregate.*
import mx.kompara.sync.api.ApiClient
import mx.kompara.sync.auth.*
import mx.kompara.sync.privacy.UploadPrivacy
import mx.kompara.sync.telemetry.*
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.Shadows.shadowOf
import java.io.File
import java.time.*

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class PrivacyViewModelsTest {
    private class Memory : DataStore<Preferences> {
        override val data = MutableStateFlow(emptyPreferences())
        override suspend fun updateData(transform: suspend (Preferences) -> Preferences) = transform(data.value).also { data.value = it }
    }
    private val context get() = ApplicationProvider.getApplicationContext<Context>()
    private lateinit var db: KomparaDatabase
    private lateinit var settings: SettingsRepository
    private lateinit var auth: AuthRepository
    private lateinit var privacy: UploadPrivacy
    private lateinit var reader: ReaderReadinessRepository
    private lateinit var billing: EntitlementRepository
    private lateinit var eraser: LocalHistoryEraser
    private lateinit var http: HttpClient
    private val store = ViewModelStore()
    private val jobs = mutableListOf<Job>()
    private val vmJobs = mutableListOf<Job>()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
    private var deleteFailure = false
    private var profileFailure = false
    private var deletes = 0
    private var patches = 0
    private var city = "cdmx"
    private var name = "Ana"

    @Before fun before() {
        Dispatchers.setMain(Dispatchers.Unconfined)
        androidx.work.testing.WorkManagerTestInitHelper.initializeTestWorkManager(context, Configuration.Builder().setExecutor { it.run() }.setTaskExecutor { it.run() }.build())
        db = Room.inMemoryDatabaseBuilder(context, KomparaDatabase::class.java).build()
        settings = SettingsRepository(Memory())
        privacy = UploadPrivacy(settings, AggregateSyncScheduler(context), TelemetryScheduler(context), db.localDataDao())
        val json = Json { ignoreUnknownKeys = true }
        val authPrefs = Memory()
        http = HttpClient(MockEngine { request ->
            val path = request.url.encodedPath
            if (request.method == HttpMethod.Delete) {
                deletes++
                assertFalse(settings.isShareAggregatesEnabled())
                assertFalse(settings.isTelemetryEnabled())
                respond(if (deleteFailure) "{}" else "{\"ok\":true}", if (deleteFailure) HttpStatusCode.InternalServerError else HttpStatusCode.OK,
                    headersOf(HttpHeaders.ContentType, "application/json"))
            } else if (request.method == HttpMethod.Patch) {
                patches++
                respond("""{"driver":{"id":"d","phone":"+525512345678","name":"$name","city":"$city","tier":"free"}}""",
                    if (profileFailure) HttpStatusCode.InternalServerError else HttpStatusCode.OK, headersOf(HttpHeaders.ContentType, "application/json"))
            } else respond("{\"ok\":true}", HttpStatusCode.OK, headersOf(HttpHeaders.ContentType, "application/json"))
        }) { install(ContentNegotiation) { json(json) } }
        auth = AuthRepository(authPrefs, ApiClient(http, "https://test.invalid", { authPrefs.data.value[stringPreferencesKey("session_token")] }, { "test" }),
            json, devBypassEnabled = true, uploads = privacy)
        reader = ReaderReadinessRepository(object : ReaderMemoryStore {
            override fun load() = ReaderMemory()
            override fun save(memory: ReaderMemory) {}
        }, java.time.Clock.fixed(Instant.parse("2026-10-19T20:40:00Z"), ZoneId.of("America/Mexico_City")))
        reader.observeEnvironment(true, false); reader.bound()
        reader.supportedApps(setOf(ReaderReadinessReducer.UBER))
        billing = EntitlementRepository(FakeBillingClient(), SubscriptionBackend.NONE, object : EntitlementCache {
            override suspend fun read() = Entitlement.Free
            override suspend fun write(entitlement: Entitlement) {}
        }, BillingLogger.NOOP)
        eraser = LocalHistoryEraser(db.localDataDao(), settings, reader, privacy, context)
    }
    @After fun after() = runBlocking {
        jobs.forEach { it.cancelAndJoin() }; store.clear(); vmJobs.forEach { it.join() }; scope.cancel()
        WorkManager.getInstance(context).cancelAllWork().result.get()
        androidx.work.testing.WorkManagerTestInitHelper.closeWorkDatabase()
        db.close(); http.close(); Dispatchers.resetMain()
    }
    private fun <T> observe(vm: ViewModel, flow: StateFlow<T>) {
        store.put("vm${vmJobs.size}", vm); vmJobs += vm.viewModelScope.coroutineContext[Job]!!
        jobs += scope.launch { flow.collect() }
    }
    private suspend fun <T> StateFlow<T>.await(predicate: (T) -> Boolean): T = withTimeout(10000) { first(predicate) }
    private suspend fun seed(): Long {
        auth.verifyOtp("+525512345678", "000000")
        settings.setOnboardingCompleted(true)
        settings.saveLimits(PlatformThreshold.DEFAULT.copy(minPerKmMxn = 9.5, redPerKmMxn = 6.5), PreferredMetric.IPK)
        CostProfileRepository(db.costProfileDao()).saveRunningCosts(RunningCostInputs.TYPICAL, 1L, 1L)
        reader.readOffer(ReaderReadinessReducer.UBER, "session", Instant.parse("2026-10-19T20:40:00Z").toEpochMilli(), reader.captureToken())
        assertNotNull(reader.state.value.apps[ReaderReadinessReducer.UBER]?.lastReadAt)
        return db.offerDao().insert(OfferEntity(seenAt = 1, platform = "UBER", fareMxn = 100.0, distanceKm = 10.0, durationMin = 20.0))
    }
    @Test fun `delete reports real failure then success keeps local history costs limits and reader`() = runBlocking {
        val id = seed(); val before = settings.settings.first()
        settings.setShareAggregates(true)
        val vm = TusDatosViewModel(auth, settings, privacy, eraser, billing, SavedStateHandle()); observe(vm, vm.state)
        vm.state.await { it.loaded && it.signedIn }
        deleteFailure = true; vm.deleteAccount()
        val failed = vm.state.await { it.error != null }
        assertEquals("No pudimos eliminarla. Inténtalo otra vez.", failed.error); assertNull(failed.result)
        assertTrue(auth.sessionState.first() is SessionState.Authenticated); assertNotNull(db.offerDao().findById(id))
        deleteFailure = false; vm.deleteAccount(); vm.state.await { it.result == "Cuenta eliminada" }
        assertEquals(2, deletes); assertEquals(SessionState.Anonymous, auth.sessionState.first())
        assertNotNull(db.offerDao().findById(id)); assertEquals(before.threshold, settings.settings.first().threshold)
        assertFalse(reader.state.value.paused); assertFalse(settings.settings.first().shareAggregates)
        assertNotNull(reader.state.value.apps[ReaderReadinessReducer.UBER]?.lastReadAt)
        assertTrue(db.costProfileDao().observeVersions().first().isNotEmpty())
    }
    @Test fun `local erase removes history exports and queues while retaining account costs and limits`() = runBlocking {
        val id = seed(); val token = auth.currentToken(); val rule = settings.settings.first().threshold
        db.tripDao().insert(TripEntity(offerId = id, startedAt = 1, platform = "UBER", grossMxn = 100.0, distanceKm = 10.0, durationMin = 20.0))
        val export = File(context.cacheDir, "share_cards/test.png").apply { parentFile!!.mkdirs(); writeText("private") }
        val oldGeneration = reader.captureToken()
        eraser.erase()
        assertNull(db.offerDao().findById(id)); assertEquals(0, db.openHelper.readableDatabase.query("SELECT count(*) FROM trips").use { it.moveToFirst(); it.getInt(0) })
        assertEquals(token, auth.currentToken()); assertEquals(rule, settings.settings.first().threshold)
        assertFalse(export.exists()); assertTrue(reader.state.value.paused); assertFalse(reader.canRead(token = oldGeneration))
        assertNull(reader.state.value.apps[ReaderReadinessReducer.UBER]?.lastReadAt)
        assertTrue(db.costProfileDao().observeVersions().first().isNotEmpty()); assertEquals(0, deletes)
    }
    @Test fun `local history transaction rolls back all rows when any delete fails`() = runBlocking {
        val id = seed()
        db.tripDao().insert(TripEntity(offerId = id, startedAt = 1, platform = "UBER", grossMxn = 100.0, distanceKm = 10.0, durationMin = 20.0))
        db.openHelper.writableDatabase.execSQL("CREATE TRIGGER stop_erase BEFORE DELETE ON trips BEGIN SELECT RAISE(ABORT, 'disk failure'); END")
        assertTrue(runCatching { db.localDataDao().eraseHistory() }.isFailure)
        assertNotNull(db.offerDao().findById(id))
        assertEquals(1, db.openHelper.readableDatabase.query("SELECT count(*) FROM trips").use { it.moveToFirst(); it.getInt(0) })
    }

    @Test fun `unsupported certified contribution cannot be enabled but existing consent can be revoked`() = runBlocking {
        val vm = TusDatosViewModel(auth, settings, privacy, eraser, billing, SavedStateHandle()); observe(vm, vm.state)
        assertFalse(vm.state.await { it.loaded }.contributionAvailable)
        privacy.contribution(true)
        assertFalse(settings.isShareAggregatesEnabled())
        settings.setShareAggregates(true)
        vm.state.await { it.contribution }
        vm.contribution(false)
        vm.state.await { !it.contribution }
        assertFalse(settings.isShareAggregatesEnabled())
    }

    @Test fun `revocation cancels periodic and immediate aggregate work and deletion cancels telemetry too`() = runBlocking {
        val manager = WorkManager.getInstance(context)
        val names = listOf(AggregateSyncWorker.ONE_OFF_WORK_NAME, AggregateSyncWorker.PERIODIC_WORK_NAME,
            TelemetryUploadWorker.ONE_OFF_WORK_NAME, TelemetryUploadWorker.PERIODIC_WORK_NAME)
        names.forEach { name -> manager.enqueueUniqueWork(name, ExistingWorkPolicy.REPLACE,
            OneTimeWorkRequest.Builder(AggregateSyncWorker::class.java).setInitialDelay(1, java.util.concurrent.TimeUnit.DAYS).build()).result.get() }
        privacy.contribution(true); privacy.contribution(false)
        names.take(2).forEach { assertEquals(WorkInfo.State.CANCELLED, manager.getWorkInfosForUniqueWork(it).get().single().state) }
        privacy.revoke(true)
        names.drop(2).forEach { assertEquals(WorkInfo.State.CANCELLED, manager.getWorkInfosForUniqueWork(it).get().single().state) }
        assertFalse(settings.isShareAggregatesEnabled()); assertFalse(settings.isTelemetryEnabled())
    }
    @Test fun `city confirms independently and failed request never changes limits or local city`() = runBlocking {
        seed(); val rule = settings.settings.first().threshold
        val saved = SavedStateHandle()
        val vm = AccountViewModel(auth, settings, saved); observe(vm, vm.state)
        vm.state.await { it.loaded }; vm.open("city"); vm.chooseCity(City.MONTERREY)
        vm.state.await { it.pendingCity == City.MONTERREY }
        assertEquals(City.CDMX, settings.currentCity()); assertEquals(0, patches)
        profileFailure = true; vm.confirmCity(); vm.state.await { it.error != null }
        assertEquals(City.CDMX, settings.currentCity()); assertEquals(rule, settings.settings.first().threshold)
        profileFailure = false; city = "monterrey"; vm.chooseCity(City.MONTERREY)
        vm.state.await { it.pendingCity != null }; vm.confirmCity(); vm.state.await { it.city == City.MONTERREY && !it.busy }
        assertEquals(rule, settings.settings.first().threshold); assertNull(vm.state.value.suggestedGoal)
        assertEquals("elegida por ti", vm.state.value.citySource)
        val recreated = AccountViewModel(auth, settings, SavedStateHandle()); observe(recreated, recreated.state)
        assertEquals(City.MONTERREY, recreated.state.await { it.loaded }.city)
    }
    @Test fun `logout keeps history and reader available and profile draft restores`() = runBlocking {
        val id = seed()
        val saved = SavedStateHandle(mapOf("profileSheet" to "name", "profileName" to "Alex"))
        val vm = AccountViewModel(auth, settings, saved); observe(vm, vm.state)
        val loaded = vm.state.await { it.loaded }
        assertEquals("Alex", loaded.nameDraft); assertEquals("name", loaded.sheet)
        vm.logout(); vm.state.await { it.message == "Sesión cerrada" }
        assertNotNull(db.offerDao().findById(id)); assertFalse(reader.state.value.paused)
        assertEquals(mx.kompara.ui.onboarding.RootRoute.MAIN, mx.kompara.ui.onboarding.RootRouter.route(true, false))
    }
    @Test fun `notification OS return never rewrites saved preferences`() = runBlocking {
        settings.setDailySummaryEnabled(false); settings.setReaderAlertsEnabled(false)
        val manager = context.getSystemService(android.app.NotificationManager::class.java)
        shadowOf(manager).setNotificationsEnabled(false)
        val vm = AvisosViewModel(settings, context); observe(vm, vm.state); vm.refreshSystem()
        vm.state.await { it.loaded }
        vm.set(NoticePreference.DAY, true); assertFalse(settings.settings.first().dailySummaryEnabled)
        shadowOf(manager).setNotificationsEnabled(true); vm.refreshSystem(); vm.state.await { it.allowed }
        assertFalse(vm.state.value.settings.dailySummaryEnabled)
        vm.set(NoticePreference.DAY, true); vm.state.await { it.settings.dailySummaryEnabled }
        assertFalse(settings.settings.first().readerAlertsEnabled)
    }
    @Test fun `channel block is visible in Avisos and cannot be bypassed by a saved switch`() = runBlocking {
        val manager = context.getSystemService(android.app.NotificationManager::class.java)
        manager.createNotificationChannel(android.app.NotificationChannel(mx.kompara.ui.notifications.NoticeChannels.SUMMARIES,
            "Resúmenes", android.app.NotificationManager.IMPORTANCE_NONE))
        val vm = AvisosViewModel(settings, context); observe(vm, vm.state); vm.refreshSystem()
        val state = vm.state.await { it.loaded && NoticePreference.DAY in it.blocked }
        assertTrue(state.allowed)
        assertFalse(NoticePreference.READER in state.blocked)
        assertTrue(state.settings.dailySummaryEnabled)
        vm.set(NoticePreference.DAY, false)
        assertTrue(settings.settings.first().dailySummaryEnabled)
    }
    @Test fun `free and promotional access cannot imply a Play subscription in deletion copy`() {
        assertFalse(deletionBody(false).contains("Google Play"))
        assertTrue(deletionBody(true).contains("borrar la cuenta no la cancela"))
    }
}
