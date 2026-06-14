package com.oasismall.oasisai.ui.screens.phonesync

import android.content.Context
import android.os.Build
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.oasismall.oasisai.domain.ImageMatcher
import com.oasismall.oasisai.domain.phonesync.PhoneSyncApplyService
import com.oasismall.oasisai.domain.phonesync.PhoneSyncCatalog
import com.oasismall.oasisai.domain.phonesync.PhoneSyncCatalogService
import com.oasismall.oasisai.domain.phonesync.PhoneSyncClient
import com.oasismall.oasisai.domain.phonesync.PhoneSyncConfig
import com.oasismall.oasisai.domain.phonesync.PhoneSyncDeltaBuilder
import com.oasismall.oasisai.domain.phonesync.PhoneSyncProtocol
import com.oasismall.oasisai.domain.phonesync.PhoneSyncServer
import com.oasismall.oasisai.data.repository.OasisRepository
import com.oasismall.oasisai.util.NetworkUtils
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

enum class PhoneSyncRole { MASTER, SLAVE }

data class PhoneSyncUiState(
    val role: PhoneSyncRole = PhoneSyncRole.SLAVE,
    val pin: String = "2468",
    val masterHost: String = "",
    val masterRunning: Boolean = false,
    val masterIp: String? = null,
    val statusMessage: String? = null,
    val progressLabel: String? = null,
    val masterCatalogPreview: PhoneSyncCatalog? = null,
    val outboundCount: Int = 0,
    val lastResult: String? = null,
)

class PhoneSyncViewModel(
    private val repository: OasisRepository,
    private val imageMatcher: ImageMatcher,
) : ViewModel() {
    private val catalogService = PhoneSyncCatalogService(repository)
    private val deltaBuilder = PhoneSyncDeltaBuilder(repository)
    private val applyService = PhoneSyncApplyService(repository, imageMatcher)
    private val client = PhoneSyncClient()

    private var server: PhoneSyncServer? = null

    private val _ui = MutableStateFlow(PhoneSyncUiState())
    val ui: StateFlow<PhoneSyncUiState> = _ui.asStateFlow()

    fun loadSettings(context: Context) {
        _ui.value = _ui.value.copy(
            pin = PhoneSyncConfig.loadPin(context),
            masterHost = PhoneSyncConfig.loadMasterHost(context),
            masterIp = NetworkUtils.getLanIpv4(),
        )
    }

    fun setRole(role: PhoneSyncRole) {
        if (role == PhoneSyncRole.SLAVE && _ui.value.masterRunning) stopMaster()
        _ui.value = _ui.value.copy(role = role)
    }

    fun setPin(pin: String) {
        _ui.value = _ui.value.copy(pin = pin)
    }

    fun setMasterHost(host: String) {
        _ui.value = _ui.value.copy(masterHost = host)
    }

    fun saveSettings(context: Context) {
        PhoneSyncConfig.savePin(context, _ui.value.pin)
        PhoneSyncConfig.saveMasterHost(context, _ui.value.masterHost)
        _ui.value = _ui.value.copy(statusMessage = "Settings saved.")
    }

    fun startMaster() {
        if (server != null) return
        val pin = _ui.value.pin
        val deviceName = Build.MODEL
        val srv = PhoneSyncServer(
            port = PhoneSyncProtocol.PORT,
            pin = pin,
            catalogProvider = { catalogService.buildCatalog(deviceName) },
            onPush = { manifest, files -> applyService.applyPush(manifest, files) },
        )
        runCatching {
            srv.start(SOCKET_READ_TIMEOUT, false)
            server = srv
            _ui.value = _ui.value.copy(
                masterRunning = true,
                masterIp = NetworkUtils.getLanIpv4(),
                statusMessage = "Master receiver running on port ${PhoneSyncProtocol.PORT}",
                lastResult = null,
            )
        }.onFailure { e ->
            _ui.value = _ui.value.copy(statusMessage = "Could not start server: ${e.message}")
        }
    }

    fun stopMaster() {
        server?.stop()
        server = null
        _ui.value = _ui.value.copy(
            masterRunning = false,
            statusMessage = "Master receiver stopped.",
        )
    }

    fun refreshMasterIp() {
        _ui.value = _ui.value.copy(masterIp = NetworkUtils.getLanIpv4())
    }

    fun slavePullCatalog(context: Context) {
        val host = _ui.value.masterHost.trim()
        val pin = _ui.value.pin
        if (host.isBlank()) {
            _ui.value = _ui.value.copy(statusMessage = "Enter master phone IP.")
            return
        }
        viewModelScope.launch {
            _ui.value = _ui.value.copy(progressLabel = "Connecting to master…", lastResult = null)
            client.ping(host, PhoneSyncProtocol.PORT, pin).onFailure { e ->
                _ui.value = _ui.value.copy(
                    progressLabel = null,
                    statusMessage = e.message,
                )
                return@launch
            }
            client.fetchCatalog(host, PhoneSyncProtocol.PORT, pin)
                .onSuccess { catalog ->
                    val outbound = deltaBuilder.buildOutbound(catalog)
                    PhoneSyncConfig.saveMasterHost(context, host)
                    _ui.value = _ui.value.copy(
                        progressLabel = null,
                        masterCatalogPreview = catalog,
                        outboundCount = outbound.size,
                        statusMessage =
                            "Master has ${catalog.articleCount} articles, ${catalog.imageCount} PNG(s). " +
                            "You can send ${outbound.size} new item(s).",
                    )
                }
                .onFailure { e ->
                    _ui.value = _ui.value.copy(
                        progressLabel = null,
                        statusMessage = e.message,
                    )
                }
        }
    }

    fun slaveSendNewWork(context: Context) {
        val host = _ui.value.masterHost.trim()
        val pin = _ui.value.pin
        val catalog = _ui.value.masterCatalogPreview
        if (host.isBlank() || catalog == null) {
            _ui.value = _ui.value.copy(statusMessage = "Pull master catalog first.")
            return
        }
        viewModelScope.launch {
            _ui.value = _ui.value.copy(progressLabel = "Preparing outbound PNGs…")
            val outbound = deltaBuilder.buildOutbound(catalog)
            if (outbound.isEmpty()) {
                _ui.value = _ui.value.copy(
                    progressLabel = null,
                    statusMessage = "Nothing new — master already has your PNGs.",
                )
                return@launch
            }
            _ui.value = _ui.value.copy(progressLabel = "Sending ${outbound.size} PNG(s) to master…")
            client.pushOutbound(
                host = host,
                port = PhoneSyncProtocol.PORT,
                pin = pin,
                deviceName = Build.MODEL,
                items = outbound,
            ).onSuccess { result ->
                _ui.value = _ui.value.copy(
                    progressLabel = null,
                    lastResult =
                        "Sent ${result.imagesApplied} PNG(s), ${result.alternatesLinked} alternate barcode(s). " +
                        if (result.skipped > 0) "${result.skipped} skipped." else "",
                    statusMessage = "Sync complete.",
                    outboundCount = 0,
                )
            }.onFailure { e ->
                _ui.value = _ui.value.copy(
                    progressLabel = null,
                    statusMessage = e.message,
                )
            }
        }
    }

    override fun onCleared() {
        stopMaster()
        super.onCleared()
    }

    companion object {
        private const val SOCKET_READ_TIMEOUT = 30_000
    }
}
