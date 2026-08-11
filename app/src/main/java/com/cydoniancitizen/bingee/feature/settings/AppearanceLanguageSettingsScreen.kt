package com.cydoniancitizen.bingee.feature.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.cydoniancitizen.bingee.R
import com.cydoniancitizen.bingee.core.designsystem.theme.BingeeDimensions
import com.cydoniancitizen.bingee.data.settings.AppLanguage
import com.cydoniancitizen.bingee.data.settings.AppTheme

@Composable
internal fun AppearanceLanguageSettingsScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: AppearanceLanguageViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    AppearanceLanguageSettingsContent(
        theme = state.theme,
        language = state.language,
        onSetTheme = viewModel::setTheme,
        onSetLanguage = viewModel::setLanguage,
        onBack = onBack,
        modifier = modifier
    )
}

@Composable
internal fun AppearanceLanguageSettingsContent(
    theme: AppTheme,
    language: AppLanguage,
    onSetTheme: (AppTheme) -> Unit,
    onSetLanguage: (AppLanguage) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    var expandedDropdown by remember { mutableStateOf<SettingsDropdownKind?>(null) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(BingeeDimensions.screenPadding),
        verticalArrangement = Arrangement.spacedBy(BingeeDimensions.contentSpacing)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = stringResource(R.string.detail_back)
                )
            }
            Text(
                text = stringResource(R.string.settings_appearance_title),
                modifier = Modifier.weight(1f).semantics { heading() },
                style = MaterialTheme.typography.headlineMedium
            )
        }

        AppearanceSection(
            theme = theme,
            onSetTheme = onSetTheme,
            expanded = expandedDropdown == SettingsDropdownKind.THEME,
            onExpandedChange = { isExpanded ->
                expandedDropdown = if (isExpanded) SettingsDropdownKind.THEME else null
            }
        )

        HorizontalDivider()

        LanguageSection(
            language = language,
            onSetLanguage = onSetLanguage,
            expanded = expandedDropdown == SettingsDropdownKind.LANGUAGE,
            onExpandedChange = { isExpanded ->
                expandedDropdown = if (isExpanded) SettingsDropdownKind.LANGUAGE else null
            }
        )
    }
}

private enum class SettingsDropdownKind { THEME, LANGUAGE }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AppearanceSection(
    theme: AppTheme,
    onSetTheme: (AppTheme) -> Unit,
    expanded: Boolean,
    onExpandedChange: (Boolean) -> Unit
) {
    val themeOptions = listOf(
        AppTheme.SYSTEM_DEFAULT to stringResource(R.string.settings_theme_system),
        AppTheme.LIGHT to stringResource(R.string.settings_theme_light),
        AppTheme.DARK to stringResource(R.string.settings_theme_dark)
    )
    val currentThemeLabel = stringResource(
        when (theme) {
            AppTheme.SYSTEM_DEFAULT -> R.string.settings_theme_system
            AppTheme.LIGHT -> R.string.settings_theme_light
            AppTheme.DARK -> R.string.settings_theme_dark
        }
    )

    Column(verticalArrangement = Arrangement.spacedBy(BingeeDimensions.elementSpacing)) {
        Text(stringResource(R.string.settings_theme_title), style = MaterialTheme.typography.titleMedium)
        ExposedDropdownMenuBox(
            expanded = expanded,
            onExpandedChange = onExpandedChange,
            modifier = Modifier.fillMaxWidth()
        ) {
            OutlinedTextField(
                value = currentThemeLabel,
                onValueChange = {},
                readOnly = true,
                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
                colors = ExposedDropdownMenuDefaults.outlinedTextFieldColors(),
                modifier = Modifier
                    .menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable)
                    .fillMaxWidth()
            )
            ExposedDropdownMenu(
                expanded = expanded,
                onDismissRequest = { onExpandedChange(false) }
            ) {
                themeOptions.forEach { (optionTheme, label) ->
                    DropdownMenuItem(
                        text = { Text(label) },
                        onClick = {
                            onSetTheme(optionTheme)
                            onExpandedChange(false)
                        },
                        contentPadding = ExposedDropdownMenuDefaults.ItemContentPadding
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun LanguageSection(
    language: AppLanguage,
    onSetLanguage: (AppLanguage) -> Unit,
    expanded: Boolean,
    onExpandedChange: (Boolean) -> Unit
) {
    val languageOptions = listOf(
        AppLanguage.ENGLISH to stringResource(R.string.settings_language_en),
        AppLanguage.ITALIAN to stringResource(R.string.settings_language_it)
    )
    val currentLanguageLabel = stringResource(
        when (language) {
            AppLanguage.ENGLISH -> R.string.settings_language_en
            AppLanguage.ITALIAN -> R.string.settings_language_it
        }
    )

    Column(verticalArrangement = Arrangement.spacedBy(BingeeDimensions.elementSpacing)) {
        Text(
            text = stringResource(R.string.settings_language_title),
            style = MaterialTheme.typography.titleMedium
        )
        ExposedDropdownMenuBox(
            expanded = expanded,
            onExpandedChange = onExpandedChange,
            modifier = Modifier.fillMaxWidth()
        ) {
            OutlinedTextField(
                value = currentLanguageLabel,
                onValueChange = {},
                readOnly = true,
                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
                colors = ExposedDropdownMenuDefaults.outlinedTextFieldColors(),
                modifier = Modifier
                    .menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable)
                    .fillMaxWidth()
            )
            ExposedDropdownMenu(
                expanded = expanded,
                onDismissRequest = { onExpandedChange(false) }
            ) {
                languageOptions.forEach { (optionLang, label) ->
                    DropdownMenuItem(
                        text = { Text(label) },
                        onClick = {
                            onSetLanguage(optionLang)
                            onExpandedChange(false)
                        },
                        contentPadding = ExposedDropdownMenuDefaults.ItemContentPadding
                    )
                }
            }
        }
    }
}
