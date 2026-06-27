package com.oasismall.oasisai

import android.app.Application
import com.oasismall.oasisai.data.db.OasisDatabase
import com.oasismall.oasisai.data.repository.OasisRepository
import com.oasismall.oasisai.domain.ImageMatcher
import com.oasismall.oasisai.domain.backgroundremoval.BackgroundRemovalService
import com.oasismall.oasisai.domain.flavors.SubBarcodeRegistry
import com.oasismall.oasisai.domain.flavors.SubBarcodeFlavorService
import com.oasismall.oasisai.domain.ImportService
import com.oasismall.oasisai.domain.settings.BackupSecurityStore
import com.oasismall.oasisai.domain.settings.ImportantRayonsStore
import com.oasismall.oasisai.domain.paray.ParayAgent
import com.oasismall.oasisai.domain.paray.ParayHome
import com.oasismall.oasisai.domain.paray.ParayImportManager
import com.oasismall.oasisai.domain.PrintGenerator
import com.oasismall.oasisai.domain.PromoService
import com.oasismall.oasisai.domain.ReadyPngLoader
import com.oasismall.oasisai.domain.bulk.BulkCaptureStore
import com.oasismall.oasisai.domain.visio.BatchCameraQueueStore
import com.oasismall.oasisai.domain.visio.CameraBatchStore
import com.oasismall.oasisai.domain.visio.ProductImagesExporter
import com.oasismall.oasisai.domain.visiopro.VisioProExporter
import com.oasismall.oasisai.domain.visiopro.VisioProAilRenderer
import com.oasismall.oasisai.domain.visiopro.VisioProPhotoStore
import com.oasismall.oasisai.domain.visiopro.VisioProPriceResolver
import com.oasismall.oasisai.domain.visiopro.VisioProRenderFacade
import com.oasismall.oasisai.domain.visiopro.VisioProCatalogConfigStore
import com.oasismall.oasisai.domain.visiopro.designer.VisioProDesignStore
import com.oasismall.oasisai.domain.visiopro.VisioProCatalogService
import com.oasismall.oasisai.domain.visiopro.VisioProStore
import com.oasismall.oasisai.domain.visiopro.VisioProTemplateAssets
import com.oasismall.oasisai.util.LocalCrashReporter
import com.oasismall.oasisai.util.OasisLog
import com.oasismall.oasisai.BuildConfig
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import timber.log.Timber

@HiltAndroidApp
class OasisApp : Application() {
    val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    @Inject lateinit var database: OasisDatabase
    @Inject lateinit var backgroundTaskManager: com.oasismall.oasisai.domain.background.OasisBackgroundTaskManager
    @Inject lateinit var importantRayonsStore: ImportantRayonsStore
    @Inject lateinit var subBarcodeRegistry: SubBarcodeRegistry
    @Inject lateinit var repository: OasisRepository
    @Inject lateinit var backupSecurityStore: BackupSecurityStore
    val imageMatcher: ImageMatcher by lazy { ImageMatcher(this, repository) }
    val subBarcodeFlavorService: SubBarcodeFlavorService by lazy {
        SubBarcodeFlavorService(this, repository, imageMatcher)
    }
    val readyPngLoader: ReadyPngLoader by lazy { ReadyPngLoader(imageMatcher) }
    val importService: ImportService by lazy {
        ImportService(repository, imageMatcher, subBarcodeFlavorService) { trigger, context ->
            appScope.launch { parayObserver.onTrigger(trigger, context) }
            if (trigger == com.oasismall.oasisai.domain.paray.ParayObserverTrigger.CSV_IMPORT_COMPLETED) {
                parayWorkflowTracker.recordFeature(
                    com.oasismall.oasisai.domain.paray.ParayWorkflowFeature.CSV_IMPORT,
                )
            }
        }
    }
    val printGenerator: PrintGenerator by lazy { PrintGenerator(this, repository) }
    val promoService: PromoService by lazy { PromoService(repository) }
    val backgroundRemovalService: BackgroundRemovalService by lazy { BackgroundRemovalService(this) }
    val parayHome: ParayHome by lazy { ParayHome(this) }
    val parayActivityMonitor: com.oasismall.oasisai.domain.paray.ParayActivityMonitor by lazy {
        com.oasismall.oasisai.domain.paray.ParayActivityMonitor(appScope)
    }
    val parayRecognitionObserver: com.oasismall.oasisai.domain.paray.ParayRecognitionObserver by lazy {
        com.oasismall.oasisai.domain.paray.ParayRecognitionObserver(
            parayHome,
            activityMonitor = parayActivityMonitor,
        )
    }
    val parayRecognitionTracker: com.oasismall.oasisai.domain.paray.ParayRecognitionTracker by lazy {
        com.oasismall.oasisai.domain.paray.ParayRecognitionTracker(parayRecognitionObserver, appScope)
    }
    val paray: ParayAgent by lazy {
        ParayAgent(this, parayHome) { parayRecognitionTracker }
    }
    val parayKnowledgeObserver: com.oasismall.oasisai.domain.paray.ParayKnowledgeObserver by lazy {
        com.oasismall.oasisai.domain.paray.ParayKnowledgeObserver(
            parayHome,
            repository,
            com.oasismall.oasisai.domain.paray.ParayLearnStore(parayHome),
        )
    }
    val parayWorkflowObserver: com.oasismall.oasisai.domain.paray.ParayWorkflowObserver by lazy {
        com.oasismall.oasisai.domain.paray.ParayWorkflowObserver(parayHome)
    }
    val parayWorkflowTracker: com.oasismall.oasisai.domain.paray.ParayWorkflowTracker by lazy {
        com.oasismall.oasisai.domain.paray.ParayWorkflowTracker(parayWorkflowObserver, appScope)
    }
    val parayObserver: com.oasismall.oasisai.domain.paray.ParayObserver by lazy {
        com.oasismall.oasisai.domain.paray.ParayObserver(
            parayHome,
            repository,
            knowledgeObserver = parayKnowledgeObserver,
        )
    }
    val parayImportManager: ParayImportManager by lazy { ParayImportManager(this) }
    val parayFusionStore: com.oasismall.oasisai.domain.paray.ParayFusionStore by lazy {
        com.oasismall.oasisai.domain.paray.ParayFusionStore(parayHome)
    }
    val parayKnowledgeFusionEngine: com.oasismall.oasisai.domain.paray.ParayKnowledgeFusionEngine by lazy {
        com.oasismall.oasisai.domain.paray.ParayKnowledgeFusionEngine(
            parayHome,
            parayFusionStore,
            com.oasismall.oasisai.domain.paray.ParayKnowledgePackageValidator(),
            cacheDir,
        )
    }
    val galleryPngAssignService: com.oasismall.oasisai.domain.GalleryPngAssignService by lazy {
        com.oasismall.oasisai.domain.GalleryPngAssignService(this, repository, imageMatcher)
    }
    val bulkCaptureStore: BulkCaptureStore by lazy {
        BulkCaptureStore(this, database.bulkCaptureDao())
    }
    val batchCameraQueueStore: BatchCameraQueueStore by lazy {
        BatchCameraQueueStore(database.batchCameraQueueDao())
    }
    val cameraBatchStore: CameraBatchStore by lazy {
        CameraBatchStore(this, database.cameraBatchDao(), repository, imageMatcher)
    }
    val productImagesExporter: ProductImagesExporter by lazy {
        ProductImagesExporter(this, imageMatcher)
    }
    val visioProStore: VisioProStore by lazy { VisioProStore(this) }
    val visioProCatalogConfigStore: VisioProCatalogConfigStore by lazy { VisioProCatalogConfigStore(this) }
    val visioProDesignStore: VisioProDesignStore by lazy {
        VisioProDesignStore(this, visioProTemplateAssets)
    }
    val visioProCatalogService: VisioProCatalogService by lazy {
        VisioProCatalogService(repository, visioProCatalogConfigStore)
    }
    val visioProPhotoStore: VisioProPhotoStore by lazy { VisioProPhotoStore(this) }
    val visioProExporter: VisioProExporter by lazy { VisioProExporter(this) }
    val visioProTemplateAssets: VisioProTemplateAssets by lazy { VisioProTemplateAssets(this) }
    val visioProAilRenderer: VisioProAilRenderer by lazy { VisioProAilRenderer(visioProTemplateAssets) }
    val visioProRenderFacade: VisioProRenderFacade by lazy {
        VisioProRenderFacade(visioProAilRenderer, visioProTemplateAssets)
    }
    val visioProPriceResolver: VisioProPriceResolver by lazy {
        VisioProPriceResolver(repository)
    }
    val gestiumCatalogPurge: com.oasismall.oasisai.domain.transfer.GestiumCatalogPurge by lazy {
        com.oasismall.oasisai.domain.transfer.GestiumCatalogPurge(
            repository, imageMatcher, visioProCatalogConfigStore, subBarcodeFlavorService,
        )
    }
    val deviceBackupExporter: com.oasismall.oasisai.domain.transfer.DeviceBackupExporter by lazy {
        com.oasismall.oasisai.domain.transfer.DeviceBackupExporter(
            this, repository, visioProCatalogConfigStore, backupSecurityStore,
        )
    }
    val deviceBackupImporter: com.oasismall.oasisai.domain.transfer.DeviceBackupImporter by lazy {
        com.oasismall.oasisai.domain.transfer.DeviceBackupImporter(
            this, repository, imageMatcher, importantRayonsStore, visioProCatalogConfigStore, backupSecurityStore,
        )
    }
    val visioProBundleExporter: com.oasismall.oasisai.domain.transfer.VisioProBundleExporter by lazy {
        com.oasismall.oasisai.domain.transfer.VisioProBundleExporter(
            this, visioProCatalogService, visioProCatalogConfigStore, visioProStore,
        )
    }
    val visioProBundleImporter: com.oasismall.oasisai.domain.transfer.VisioProBundleImporter by lazy {
        com.oasismall.oasisai.domain.transfer.VisioProBundleImporter(
            this, repository, imageMatcher, visioProCatalogConfigStore, visioProStore,
        )
    }
    val visioProPrintImageLinker: com.oasismall.oasisai.domain.visiopro.VisioProPrintImageLinker by lazy {
        com.oasismall.oasisai.domain.visiopro.VisioProPrintImageLinker(
            this, repository, visioProCatalogService, visioProPhotoStore, imageMatcher,
        )
    }

    override fun onCreate() {
        super.onCreate()
        LocalCrashReporter.install(this)
        if (BuildConfig.DEBUG) {
            Timber.plant(Timber.DebugTree())
        } else {
            Timber.plant(OasisLog.ReleaseTree(this))
        }
        appScope.launch {
            com.oasismall.oasisai.domain.transfer.UserExportStorage.cleanupStaleExportCache(cacheDir)
            repository.seedDefaultTemplates()
            promoService.refreshAlerts()
            parayObserver.onTrigger(com.oasismall.oasisai.domain.paray.ParayObserverTrigger.APP_STARTUP)
        }
    }
}

fun Application.oasisContainer(): OasisApp = this as OasisApp
