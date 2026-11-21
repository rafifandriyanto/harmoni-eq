package com.example.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.CloudDone
import androidx.compose.material.icons.rounded.Earbuds
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.Headphones
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.RestartAlt
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.model.HeadphoneProfile
import com.example.ui.AutoEqUiState
import com.example.ui.AutoEqViewModel
import com.example.ui.components.glass.GlassBottomSheet
import com.example.ui.components.glass.GlassSurface
import com.example.ui.components.glass.GlassVariant
import com.example.ui.theme.GlassTokens
import com.example.ui.theme.HarmoniEqTheme
import com.example.ui.theme.LocalHarmoniPalette
import com.example.ui.theme.LocalThemeController
import com.example.ui.theme.ThemeMode
import com.example.ui.theme.ThemeSettings

private val HEADPHONE_FORM_FILTERS = listOf("All", "Over-ear", "In-ear", "On-ear", "Earbud")

@Composable
fun AutoEqBottomSheet(
    autoEqViewModel: AutoEqViewModel,
    onDismiss: () -> Unit,
    onApplyProfile: (HeadphoneProfile, FloatArray) -> Unit,
    onDisableAutoEq: () -> Unit,
    onShowMessage: (String) -> Unit = {}
) {
    val uiState by autoEqViewModel.uiState.collectAsStateWithLifecycle()
    val searchQuery by autoEqViewModel.searchQuery.collectAsStateWithLifecycle()
    val selectedTypeFilter by autoEqViewModel.selectedTypeFilter.collectAsStateWithLifecycle()
    val cachedProfileIds by autoEqViewModel.cachedProfileIds.collectAsStateWithLifecycle()
    val activeCurveGainsDb by autoEqViewModel.activeCurveGainsDb.collectAsStateWithLifecycle()
    val applyingProfileId by autoEqViewModel.applyingProfileId.collectAsStateWithLifecycle()
    val snackbarMessage by autoEqViewModel.snackbarMessage.collectAsStateWithLifecycle()
    val haptics = LocalHapticFeedback.current
    val sheetSnackbarHost = remember { SnackbarHostState() }

    LaunchedEffect(Unit) {
        autoEqViewModel.loadProfiles()
    }

    LaunchedEffect(snackbarMessage) {
        val msg = snackbarMessage ?: return@LaunchedEffect
        onShowMessage(msg)
        sheetSnackbarHost.showSnackbar(msg)
        autoEqViewModel.consumeSnackbarMessage()
    }

    GlassBottomSheet(onDismissRequest = onDismiss) {
        Box(modifier = Modifier.fillMaxWidth()) {
            AutoEqSheetContent(
                uiState = uiState,
                searchQuery = searchQuery,
                selectedTypeFilter = selectedTypeFilter,
                cachedProfileIds = cachedProfileIds,
                activeCurveGainsDb = activeCurveGainsDb,
                applyingProfileId = applyingProfileId,
                onSearchChange = autoEqViewModel::updateSearchQuery,
                onTypeFilterChange = autoEqViewModel::selectTypeFilter,
                onRefresh = { autoEqViewModel.loadProfiles(forceRefresh = true) },
                onSelectProfile = { profile ->
                    autoEqViewModel.selectProfile(profile) { appliedProfile, gainsArray ->
                        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                        onApplyProfile(appliedProfile, gainsArray)
                        onDismiss()
                    }
                },
                onClearReset = {
                    autoEqViewModel.detachAutoEq()
                    onDisableAutoEq()
                    onDismiss()
                },
                onDismiss = onDismiss
            )
            SnackbarHost(
                hostState = sheetSnackbarHost,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(GlassTokens.Space16)
                    .testTag("autoeq_sheet_snackbar")
            )
        }
    }
}

@Composable
fun AutoEqSheetContent(
    uiState: AutoEqUiState,
    searchQuery: String,
    selectedTypeFilter: String,
    cachedProfileIds: Set<String>,
    activeCurveGainsDb: List<Float>?,
    applyingProfileId: String?,
    onSearchChange: (String) -> Unit,
    onTypeFilterChange: (String) -> Unit,
    onRefresh: () -> Unit,
    onSelectProfile: (HeadphoneProfile) -> Unit,
    onClearReset: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    val palette = LocalHarmoniPalette.current
    val reduceTransparency = LocalThemeController.current.settings.reduceTransparency
    val haptics = LocalHapticFeedback.current

    val selectedProfile = (uiState as? AutoEqUiState.Success)?.selectedProfile
    val isLoading = uiState is AutoEqUiState.Loading || uiState is AutoEqUiState.Idle

    val glassFill = when {
        reduceTransparency -> palette.surfaceElevated
        palette.isDark -> Color.White.copy(alpha = 0.09f)
        else -> Color.White.copy(alpha = 0.68f)
    }
    val glassBorderBrush = Brush.linearGradient(
        colors = listOf(
            if (palette.isDark) Color.White.copy(alpha = 0.24f) else Color.White.copy(alpha = 0.85f),
            if (palette.isDark) Color.White.copy(alpha = 0.04f) else palette.borderSubtle
        )
    )

    Column(
        modifier = modifier
            .fillMaxWidth()
            .fillMaxHeight(0.88f)
            .padding(horizontal = GlassTokens.Space16, vertical = GlassTokens.Space8),
        verticalArrangement = Arrangement.spacedBy(GlassTokens.Space16)
    ) {
        // Header: Title "Headphone Correction (AutoEQ)" + Clear/Reset button
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(GlassTokens.Space8)
        ) {
            Icon(
                imageVector = Icons.Rounded.Headphones,
                contentDescription = null,
                tint = palette.accent,
                modifier = Modifier.size(24.dp)
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Headphone Correction (AutoEQ)",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = palette.textPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = "jaakkopasanen/AutoEq database",
                    style = MaterialTheme.typography.bodySmall,
                    color = palette.textSecondary
                )
            }
            OutlinedButton(
                onClick = {
                    haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                    onClearReset()
                },
                enabled = selectedProfile != null,
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                modifier = Modifier
                    .defaultMinSize(minHeight = 48.dp)
                    .semantics { contentDescription = "Clear and reset AutoEQ profile" }
                    .testTag("autoeq_disable_button")
            ) {
                Icon(
                    imageVector = Icons.Rounded.RestartAlt,
                    contentDescription = "Clear and reset AutoEQ profile",
                    modifier = Modifier.size(18.dp)
                )
                Text(
                    text = "Clear/Reset",
                    modifier = Modifier.padding(start = 4.dp),
                    style = MaterialTheme.typography.labelMedium
                )
            }
            IconButton(
                onClick = onDismiss,
                modifier = Modifier.testTag("autoeq_close_button")
            ) {
                Icon(
                    imageVector = Icons.Rounded.Close,
                    contentDescription = "Close Headphone Correction",
                    tint = palette.textSecondary
                )
            }
        }

        // Active AutoEQ mini-curve card when a profile is selected
        if (selectedProfile != null) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(palette.accentContainer)
                    .border(1.dp, palette.accent.copy(alpha = 0.6f), RoundedCornerShape(16.dp))
                    .padding(GlassTokens.Space12)
                    .testTag("autoeq_active_banner")
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(GlassTokens.Space12)
                ) {
                    activeCurveGainsDb?.let { gains ->
                        MiniAutoEqCurve(gainsDb = gains)
                    }
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "ACTIVE CORRECTION",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = palette.accent
                        )
                        Text(
                            text = selectedProfile.name,
                            style = MaterialTheme.typography.titleSmall,
                            color = palette.textPrimary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = "${selectedProfile.type} • ${selectedProfile.sourceLabel}",
                            style = MaterialTheme.typography.bodySmall,
                            color = palette.textSecondary,
                            maxLines = 1
                        )
                    }
                }
            }
        }

        // Glassmorphism-styled Debounced Search Bar bound directly to ViewModel
        OutlinedTextField(
            value = searchQuery,
            onValueChange = onSearchChange,
            modifier = Modifier
                .fillMaxWidth()
                .semantics { contentDescription = "Search for headphone models" }
                .testTag("autoeq_search_input"),
            shape = RoundedCornerShape(16.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedContainerColor = glassFill,
                unfocusedContainerColor = glassFill,
                focusedBorderColor = palette.accent,
                unfocusedBorderColor = palette.border
            ),
            label = { Text("Search headphone model") },
            placeholder = { Text("e.g. Sony WH-1000XM5, AirPods Pro 2") },
            singleLine = true,
            leadingIcon = { Icon(Icons.Rounded.Search, contentDescription = null) },
            trailingIcon = {
                if (searchQuery.isNotEmpty()) {
                    IconButton(onClick = { onSearchChange("") }) {
                        Icon(Icons.Rounded.Close, contentDescription = "Clear search")
                    }
                }
            }
        )

        // Form-factor filter chips
        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(GlassTokens.Space8),
            modifier = Modifier.fillMaxWidth()
        ) {
            items(HEADPHONE_FORM_FILTERS) { filter ->
                FilterChip(
                    selected = selectedTypeFilter == filter,
                    onClick = { onTypeFilterChange(filter) },
                    label = { Text(filter) },
                    modifier = Modifier.testTag("autoeq_filter_${filter.lowercase().replace("-", "_")}")
                )
            }
        }

        // Content area driven by sealed AutoEqUiState
        when (uiState) {
            AutoEqUiState.Idle,
            AutoEqUiState.Loading -> {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .testTag("autoeq_loading_indicator"),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(GlassTokens.Space16)
                    ) {
                        CircularProgressIndicator(color = palette.accent)
                        Text(
                            text = "Loading AutoEQ headphone database…",
                            style = MaterialTheme.typography.bodyMedium,
                            color = palette.textSecondary
                        )
                    }
                }
            }

            is AutoEqUiState.Error -> {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    contentAlignment = Alignment.Center
                ) {
                    GlassSurface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .border(1.dp, palette.statusDanger.copy(alpha = 0.65f), RoundedCornerShape(GlassTokens.CornerRadius))
                            .testTag("autoeq_error_banner"),
                        variant = GlassVariant.CHROME,
                        cornerRadius = GlassTokens.CornerRadius
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(GlassTokens.Space12),
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(GlassTokens.Space24)
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.ErrorOutline,
                                contentDescription = "Error loading AutoEQ profiles",
                                tint = palette.statusDanger,
                                modifier = Modifier.size(32.dp)
                            )
                            Text(
                                text = uiState.message,
                                style = MaterialTheme.typography.bodyMedium,
                                color = palette.textPrimary
                            )
                            OutlinedButton(
                                onClick = onRefresh,
                                modifier = Modifier
                                    .defaultMinSize(minHeight = 48.dp)
                                    .testTag("autoeq_retry_button")
                            ) {
                                Icon(Icons.Rounded.Refresh, contentDescription = null, modifier = Modifier.size(18.dp))
                                Text("Retry", modifier = Modifier.padding(start = 6.dp))
                            }
                        }
                    }
                }
            }

            is AutoEqUiState.Success -> {
                if (uiState.profiles.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(GlassTokens.Space8)
                        ) {
                            Text(
                                text = if (searchQuery.isBlank()) {
                                    "No headphone models loaded yet."
                                } else {
                                    "No headphones found for \"$searchQuery\""
                                },
                                style = MaterialTheme.typography.titleSmall,
                                color = palette.textPrimary
                            )
                            if (searchQuery.isNotBlank()) {
                                TextButton(onClick = { onSearchChange("") }) {
                                    Text("Clear search")
                                }
                            }
                        }
                    }
                } else {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "${uiState.profiles.size} models (A–Z)",
                            style = MaterialTheme.typography.labelMedium,
                            color = palette.textSecondary
                        )
                        if (applyingProfileId != null) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(GlassTokens.Space8)
                            ) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(16.dp),
                                    strokeWidth = 2.dp,
                                    color = palette.accent
                                )
                                Text(
                                    text = "Applying curve…",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = palette.accent
                                )
                            }
                        } else if (!isLoading) {
                            IconButton(
                                onClick = onRefresh,
                                modifier = Modifier
                                    .size(36.dp)
                                    .testTag("autoeq_refresh_button")
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.Refresh,
                                    contentDescription = "Refresh AutoEQ catalog",
                                    tint = palette.textSecondary,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                    }

                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f)
                            .testTag("autoeq_profile_list"),
                        contentPadding = PaddingValues(bottom = GlassTokens.Space24),
                        verticalArrangement = Arrangement.spacedBy(GlassTokens.Space8)
                    ) {
                        items(uiState.profiles, key = { it.id }) { profile ->
                            val isSelected = uiState.selectedProfile?.id == profile.id
                            val isApplying = applyingProfileId == profile.id
                            val isCached = profile.id in cachedProfileIds

                            GlassHeadphoneProfileCard(
                                profile = profile,
                                isSelected = isSelected,
                                isApplying = isApplying,
                                isCached = isCached,
                                glassFill = glassFill,
                                glassBorderBrush = glassBorderBrush,
                                onClick = {
                                    haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                    onSelectProfile(profile)
                                }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun GlassHeadphoneProfileCard(
    profile: HeadphoneProfile,
    isSelected: Boolean,
    isApplying: Boolean,
    isCached: Boolean,
    glassFill: Color,
    glassBorderBrush: Brush,
    onClick: () -> Unit
) {
    val palette = LocalHarmoniPalette.current
    val shape = RoundedCornerShape(16.dp)
    val isInEar = profile.type.equals("In-ear", ignoreCase = true) ||
        profile.type.equals("Earbud", ignoreCase = true)

    val cardModifier = Modifier
        .fillMaxWidth()
        .defaultMinSize(minHeight = 56.dp)
        .clip(shape)
        .background(if (isSelected) palette.accentContainer else glassFill, shape)
        .then(
            if (isSelected) {
                Modifier.border(BorderStroke(1.5.dp, palette.accent), shape)
            } else {
                Modifier.border(BorderStroke(1.dp, glassBorderBrush), shape)
            }
        )
        .clickable(enabled = !isApplying, onClick = onClick)
        .testTag("autoeq_item_${profile.name.lowercase().replace(Regex("[^a-z0-9]+"), "_")}")

    Box(modifier = cardModifier) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = GlassTokens.Space16, vertical = GlassTokens.Space12),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(GlassTokens.Space12)
        ) {
            Icon(
                imageVector = if (isInEar) Icons.Rounded.Earbuds else Icons.Rounded.Headphones,
                contentDescription = null,
                tint = if (isSelected) palette.accent else palette.textSecondary,
                modifier = Modifier.size(22.dp)
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = profile.name,
                    style = MaterialTheme.typography.titleSmall,
                    color = palette.textPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(GlassTokens.Space8)
                ) {
                    Text(
                        text = profile.type,
                        style = MaterialTheme.typography.labelMedium,
                        color = if (isSelected) palette.accent else palette.textSecondary
                    )
                    Text(
                        text = "• ${profile.sourceLabel}",
                        style = MaterialTheme.typography.labelSmall,
                        color = palette.textMuted,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    if (isCached) {
                        Icon(
                            imageVector = Icons.Rounded.CloudDone,
                            contentDescription = "Cached offline",
                            tint = palette.statusSuccess,
                            modifier = Modifier.size(14.dp)
                        )
                    }
                }
            }
            when {
                isApplying -> CircularProgressIndicator(
                    modifier = Modifier.size(20.dp),
                    strokeWidth = 2.dp,
                    color = palette.accent
                )
                isSelected -> Icon(
                    imageVector = Icons.Rounded.CheckCircle,
                    contentDescription = "Selected headphone profile",
                    tint = palette.accent,
                    modifier = Modifier.size(22.dp)
                )
            }
        }
    }
}

@Composable
private fun MiniAutoEqCurve(
    gainsDb: List<Float>,
    modifier: Modifier = Modifier
) {
    val palette = LocalHarmoniPalette.current
    val density = LocalDensity.current
    val path = remember { Path() }
    val xs = remember { FloatArray(20) }
    val ys = remember { FloatArray(20) }
    val stroke = remember(density) {
        with(density) { Stroke(2.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round) }
    }

    Canvas(modifier = modifier.size(56.dp, 36.dp)) {
        val zeroY = size.height / 2f
        drawLine(palette.borderSubtle, Offset(0f, zeroY), Offset(size.width, zeroY), 1.dp.toPx())
        val count = gainsDb.size.coerceAtMost(20)
        if (count >= 2) {
            for (i in 0 until count) {
                xs[i] = size.width * i / (count - 1).coerceAtLeast(1)
                ys[i] = size.height * (15f - gainsDb[i].coerceIn(-15f, 15f)) / 30f
            }
            buildSmoothBezierCurvePath(path, null, xs, ys, count, zeroY, 0f, size.height)
            drawPath(path, palette.accent, style = stroke)
        }
    }
}

@Preview(name = "AutoEQ Glass Sheet Dark", showBackground = true, backgroundColor = 0xFF0E1013, widthDp = 390, heightDp = 760)
@Composable
private fun PreviewAutoEqSheetDark() {
    val sampleProfiles = listOf(
        HeadphoneProfile("Apple AirPods Pro 2", "In-ear", "https://cdn.jsdelivr.net/gh/jaakkopasanen/AutoEq@master/results/crinacle/in-ear/Apple%20AirPods%20Pro%202/Apple%20AirPods%20Pro%202.csv"),
        HeadphoneProfile("Sennheiser HD 800", "Over-ear", "https://cdn.jsdelivr.net/gh/jaakkopasanen/AutoEq@master/results/oratory1990/over-ear/Sennheiser%20HD%20800/Sennheiser%20HD%20800.csv"),
        HeadphoneProfile("Sony WH-1000XM5", "Over-ear", "https://cdn.jsdelivr.net/gh/jaakkopasanen/AutoEq@master/results/oratory1990/over-ear/Sony%20WH-1000XM5/Sony%20WH-1000XM5.csv")
    )
    HarmoniEqTheme(previewSettings = ThemeSettings(mode = ThemeMode.DARK, dynamicColor = false)) {
        Surface(color = LocalHarmoniPalette.current.canvas) {
            AutoEqSheetContent(
                uiState = AutoEqUiState.Success(
                    profiles = sampleProfiles,
                    selectedProfile = sampleProfiles.last()
                ),
                searchQuery = "",
                selectedTypeFilter = "All",
                cachedProfileIds = setOf(sampleProfiles.last().id),
                activeCurveGainsDb = listOf(
                    -4.5f, -4.0f, -3.5f, -3.0f, -2.5f,
                    -2.0f, -1.0f, 0.5f, 1.5f, 2.0f,
                    1.5f, 1.0f, 1.5f, 2.5f, 3.0f,
                    2.5f, 1.5f, 0.5f, 0.0f, -0.5f
                ),
                applyingProfileId = null,
                onSearchChange = {},
                onTypeFilterChange = {},
                onRefresh = {},
                onSelectProfile = {},
                onClearReset = {},
                onDismiss = {}
            )
        }
    }
}
