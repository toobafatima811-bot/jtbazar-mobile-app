package com.example.localization

import android.content.Context
import android.content.res.Configuration
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.Locale

enum class AppLanguage(
  val code: String,
  val englishName: String,
  val nativeName: String
) {
  ENGLISH(code = "en", englishName = "English", nativeName = "English"),
  URDU(code = "ur", englishName = "Urdu", nativeName = "اردو");

  companion object {
    fun fromCode(raw: String?): AppLanguage {
      return when (raw?.trim()?.lowercase()) {
        "ur", "urdu", "اردو" -> URDU
        else -> ENGLISH
      }
    }
  }
}

object AppLanguageManager {
  private const val PREFS_NAME = "jtbazar_language_prefs"
  private const val KEY_LANGUAGE = "selected_language"

  private val _currentLanguage = MutableStateFlow(AppLanguage.ENGLISH)
  val currentLanguage: StateFlow<AppLanguage> = _currentLanguage.asStateFlow()

  val isUrdu: Boolean
    get() = _currentLanguage.value == AppLanguage.URDU

  fun init(context: Context) {
    val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    val saved = prefs.getString(KEY_LANGUAGE, AppLanguage.ENGLISH.code)
    _currentLanguage.value = AppLanguage.fromCode(saved)
  }

  fun setLanguage(language: AppLanguage, context: Context? = null) {
    _currentLanguage.value = language
    if (context != null) {
      val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
      prefs.edit().putString(KEY_LANGUAGE, language.code).apply()
    }
  }

  fun setLanguageForTest(language: AppLanguage) {
    _currentLanguage.value = language
  }

  fun tr(english: String, urdu: String): String {
    return if (_currentLanguage.value == AppLanguage.URDU) urdu else english
  }

  fun localeFor(language: AppLanguage = _currentLanguage.value): Locale {
    return if (language == AppLanguage.URDU) Locale("ur", "PK") else Locale.ENGLISH
  }

  fun localizedContext(
    base: Context,
    language: AppLanguage = _currentLanguage.value
  ): Context {
    val locale = localeFor(language)
    val config = Configuration(base.resources.configuration)
    config.setLocale(locale)
    config.setLayoutDirection(locale)
    return base.createConfigurationContext(config)
  }
}

val LocalAppLanguage = compositionLocalOf { AppLanguage.ENGLISH }

@Composable
fun appText(english: String, urdu: String): String {
  return if (LocalAppLanguage.current == AppLanguage.URDU) urdu else english
}

@Composable
fun isAppUrdu(): Boolean = LocalAppLanguage.current == AppLanguage.URDU

@Composable
fun JTBazarLocalizedContainer(
  languageOverride: AppLanguage? = null,
  content: @Composable () -> Unit
) {
  val observedLanguage by AppLanguageManager.currentLanguage.collectAsState()
  val activeLanguage = languageOverride ?: observedLanguage
  val currentConfiguration = LocalConfiguration.current
  val localizedConfiguration = remember(currentConfiguration, activeLanguage) {
    val locale = AppLanguageManager.localeFor(activeLanguage)
    Configuration(currentConfiguration).apply {
      setLocale(locale)
      setLayoutDirection(locale)
    }
  }
  val layoutDirection = if (activeLanguage == AppLanguage.URDU) {
    LayoutDirection.Rtl
  } else {
    LayoutDirection.Ltr
  }

  // Keep LocalContext untouched so ComponentActivity (ActivityResultRegistryOwner,
  // OnBackPressedDispatcherOwner, etc.) remains reachable everywhere below.
  CompositionLocalProvider(
    LocalAppLanguage provides activeLanguage,
    LocalLayoutDirection provides layoutDirection,
    LocalConfiguration provides localizedConfiguration
  ) {
    content()
  }
}
