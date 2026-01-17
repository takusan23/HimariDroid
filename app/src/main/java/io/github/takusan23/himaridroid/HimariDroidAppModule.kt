package io.github.takusan23.himaridroid

import io.github.takusan23.himaridroid.processor.MediaTool
import io.github.takusan23.himaridroid.ui.screen.viewmodel.HomeScreenViewModel
import org.koin.core.module.dsl.singleOf
import org.koin.core.module.dsl.viewModelOf
import org.koin.dsl.module

/**
 * koin を使って DI を行う
 * DI したいクラスをここで定義し、ViewModel 作成時にこれらのインスタンスが DI されるようにする
 */
val himariDroidAppModule = module {
    singleOf(::MediaTool)

    viewModelOf(::HomeScreenViewModel)
}