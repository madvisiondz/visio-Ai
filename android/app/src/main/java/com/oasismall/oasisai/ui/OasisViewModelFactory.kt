package com.oasismall.oasisai.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.oasismall.oasisai.OasisApp
import com.oasismall.oasisai.data.model.CartType
import com.oasismall.oasisai.ui.screens.article.ArticleDetailViewModel
import com.oasismall.oasisai.ui.screens.backgroundremoval.BackgroundRemovalViewModel
import com.oasismall.oasisai.ui.screens.batchtxt.BatchTxtViewModel
import com.oasismall.oasisai.ui.screens.camerabatch.CameraBatchImportViewModel
import com.oasismall.oasisai.ui.screens.camerabatch.CameraBatchShootViewModel
import com.oasismall.oasisai.ui.screens.checkshoot.CheckShootViewModel
import com.oasismall.oasisai.ui.screens.catalog.CatalogViewModel
import com.oasismall.oasisai.ui.screens.cart.CartViewModel
import com.oasismall.oasisai.ui.screens.home.HomeViewModel
import com.oasismall.oasisai.ui.screens.history.PrintHistoryViewModel
import com.oasismall.oasisai.ui.screens.history.WorkHistoryViewModel
import com.oasismall.oasisai.ui.screens.report.ReportViewModel
import com.oasismall.oasisai.ui.screens.images.ImageManagerViewModel
import com.oasismall.oasisai.ui.screens.importing.ImportViewModel
import com.oasismall.oasisai.ui.screens.preselection.PreselectionViewModel
import com.oasismall.oasisai.ui.screens.print.PrintViewModel
import com.oasismall.oasisai.ui.screens.promo.PromoViewModel
import com.oasismall.oasisai.ui.screens.design.DesignViewModel
import com.oasismall.oasisai.ui.screens.parayhome.ParayHomeViewModel
import com.oasismall.oasisai.ui.screens.parayimport.ParayImportViewModel
import com.oasismall.oasisai.ui.screens.phonesync.PhoneSyncViewModel
import com.oasismall.oasisai.ui.screens.scanner.ScannerViewModel
import com.oasismall.oasisai.ui.screens.settings.ImportantRayonsViewModel
import com.oasismall.oasisai.ui.screens.settings.SettingsViewModel
import com.oasismall.oasisai.ui.screens.visiopro.VisioProHomeViewModel
import com.oasismall.oasisai.ui.screens.visiopro.VisioProViewModel
import com.oasismall.oasisai.domain.visiopro.designer.VisioProPresetDesignKey
import com.oasismall.oasisai.domain.visiopro.VisioProCategory
import com.oasismall.oasisai.ui.screens.visiopro.designer.VisioProDesignerHubViewModel
import com.oasismall.oasisai.ui.screens.visiopro.designer.VisioProDesignerViewModel
import com.oasismall.oasisai.ui.screens.visiopro.settings.VisioProListEditorViewModel
import com.oasismall.oasisai.ui.screens.visiopro.settings.VisioProSettingsViewModel

class OasisViewModelFactory(
    private val app: OasisApp,
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        return when {
            modelClass.isAssignableFrom(HomeViewModel::class.java) ->
                HomeViewModel(app.repository) as T
            modelClass.isAssignableFrom(SettingsViewModel::class.java) ->
                SettingsViewModel(
                    app.repository,
                    app.importService,
                    app.imageMatcher,
                    app.readyPngLoader,
                    app.productImagesExporter,
                ) as T
            modelClass.isAssignableFrom(ImportantRayonsViewModel::class.java) ->
                ImportantRayonsViewModel(app.repository) as T
            modelClass.isAssignableFrom(ParayImportViewModel::class.java) ->
                ParayImportViewModel(app.parayImportManager) as T
            modelClass.isAssignableFrom(ParayHomeViewModel::class.java) ->
                ParayHomeViewModel(app.paray) as T
            modelClass.isAssignableFrom(ImportViewModel::class.java) ->
                ImportViewModel(app.repository, app.importService) as T
            modelClass.isAssignableFrom(CatalogViewModel::class.java) ->
                CatalogViewModel(app.repository) as T
            modelClass.isAssignableFrom(ArticleDetailViewModel::class.java) ->
                ArticleDetailViewModel(app.repository) as T
            modelClass.isAssignableFrom(ScannerViewModel::class.java) ->
                ScannerViewModel(app.repository) as T
            modelClass.isAssignableFrom(PreselectionViewModel::class.java) ->
                PreselectionViewModel(app.repository) as T
            modelClass.isAssignableFrom(PrintViewModel::class.java) ->
                PrintViewModel(app.repository, app.printGenerator) as T
            modelClass.isAssignableFrom(PrintHistoryViewModel::class.java) ->
                PrintHistoryViewModel(app.repository) as T
            modelClass.isAssignableFrom(WorkHistoryViewModel::class.java) ->
                WorkHistoryViewModel(app.repository) as T
            modelClass.isAssignableFrom(ReportViewModel::class.java) ->
                ReportViewModel(app.repository) as T
            modelClass.isAssignableFrom(PromoViewModel::class.java) ->
                PromoViewModel(app.repository, app.promoService) as T
            modelClass.isAssignableFrom(ImageManagerViewModel::class.java) ->
                ImageManagerViewModel(app.repository) as T
            modelClass.isAssignableFrom(BatchTxtViewModel::class.java) ->
                BatchTxtViewModel(app.repository, app.batchCameraQueueStore) as T
            modelClass.isAssignableFrom(CameraBatchShootViewModel::class.java) ->
                CameraBatchShootViewModel(
                    app.repository,
                    app.cameraBatchStore,
                    app.batchCameraQueueStore,
                ) as T
            modelClass.isAssignableFrom(CameraBatchImportViewModel::class.java) ->
                CameraBatchImportViewModel(app.cameraBatchStore) as T
            modelClass.isAssignableFrom(CheckShootViewModel::class.java) ->
                CheckShootViewModel(
                    app.applicationContext,
                    app.repository,
                    app.imageMatcher,
                    app.backgroundRemovalService,
                    app.paray,
                    app.bulkCaptureStore,
                ) as T
            modelClass.isAssignableFrom(PhoneSyncViewModel::class.java) ->
                PhoneSyncViewModel(app.repository, app.imageMatcher) as T
            modelClass.isAssignableFrom(DesignViewModel::class.java) ->
                DesignViewModel(app.repository, app.paray) as T
            modelClass.isAssignableFrom(VisioProViewModel::class.java) ->
                VisioProViewModel(
                    app.repository,
                    app.visioProCatalogService,
                    app.visioProDesignStore,
                    app.visioProStore,
                    app.visioProPhotoStore,
                    app.visioProPriceResolver,
                    app.visioProExporter,
                    app.visioProRenderFacade,
                ) as T
            modelClass.isAssignableFrom(VisioProSettingsViewModel::class.java) ->
                VisioProSettingsViewModel(app.visioProCatalogService) as T
            modelClass.isAssignableFrom(VisioProHomeViewModel::class.java) ->
                VisioProHomeViewModel(app.visioProCatalogService) as T
            modelClass.isAssignableFrom(VisioProDesignerHubViewModel::class.java) ->
                VisioProDesignerHubViewModel(app.visioProDesignStore) as T
            else -> throw IllegalArgumentException("Unknown ViewModel: ${modelClass.name}")
        }
    }

    fun visioProDesignerFactory(presetKey: VisioProPresetDesignKey): ViewModelProvider.Factory =
        object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                return VisioProDesignerViewModel(
                    presetKey,
                    app.visioProDesignStore,
                    app.visioProRenderFacade,
                ) as T
            }
        }

    fun visioProListEditorFactory(category: VisioProCategory): ViewModelProvider.Factory =
        object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                return VisioProListEditorViewModel(category, app.visioProCatalogService) as T
            }
        }

    fun cartViewModelFactory(cartType: CartType): ViewModelProvider.Factory =
        object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                return CartViewModel(cartType, app.repository) as T
            }
        }

    fun backgroundRemovalViewModelFactory(articleId: Long): ViewModelProvider.Factory =
        object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                return BackgroundRemovalViewModel(
                    articleId = articleId,
                    appContext = app,
                    repository = app.repository,
                    imageMatcher = app.imageMatcher,
                    bgService = app.backgroundRemovalService,
                ) as T
            }
        }
}
