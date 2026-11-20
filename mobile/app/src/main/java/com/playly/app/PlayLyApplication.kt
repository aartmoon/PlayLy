package com.playly.app

import android.app.Application
import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.playly.app.ui.PlayLyViewModel
import dagger.BindsInstance
import dagger.Component
import javax.inject.Inject
import javax.inject.Provider
import javax.inject.Singleton

class PlayLyApplication : Application() {
    val component: AppComponent by lazy { DaggerAppComponent.factory().create(applicationContext) }
}

@Singleton
@Component
interface AppComponent {
    fun viewModelFactory(): PlayLyViewModelFactory

    @Component.Factory
    interface Factory {
        fun create(@BindsInstance context: Context): AppComponent
    }
}

class PlayLyViewModelFactory @Inject constructor(private val model: Provider<PlayLyViewModel>) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        require(modelClass == PlayLyViewModel::class.java)
        @Suppress("UNCHECKED_CAST")
        return model.get() as T
    }
}
