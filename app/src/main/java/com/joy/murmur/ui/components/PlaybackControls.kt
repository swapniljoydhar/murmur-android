package com.joy.murmur.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.OutlinedIconButton
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.joy.murmur.R
import com.joy.murmur.domain.audiobooks.Audiobook
import com.joy.murmur.domain.documents.ReaderDocument
import com.joy.murmur.playback.PlaybackContentType
import com.joy.murmur.playback.PlaybackSnapshot
import com.joy.murmur.playback.playbackPositionAtFraction
import com.joy.murmur.playback.playbackPositionFraction

object MurmurIcons {
    val Play: ImageVector by lazy {
        vectorIcon("Play") {
            moveTo(8f, 5f)
            verticalLineTo(19f)
            lineTo(19f, 12f)
            close()
        }
    }

    val Pause: ImageVector by lazy {
        vectorIcon("Pause") {
            moveTo(6f, 19f)
            horizontalLineTo(10f)
            verticalLineTo(5f)
            horizontalLineTo(6f)
            close()
            moveTo(14f, 5f)
            verticalLineTo(19f)
            horizontalLineTo(18f)
            verticalLineTo(5f)
            close()
        }
    }

    val SkipPrevious: ImageVector by lazy {
        vectorIcon("SkipPrevious") {
            moveTo(6f, 6f)
            horizontalLineTo(8f)
            verticalLineTo(18f)
            horizontalLineTo(6f)
            close()
            moveTo(9.5f, 12f)
            lineTo(18f, 18f)
            verticalLineTo(6f)
            close()
        }
    }

    val SkipNext: ImageVector by lazy {
        vectorIcon("SkipNext") {
            moveTo(6f, 18f)
            lineTo(14.5f, 12f)
            lineTo(6f, 6f)
            verticalLineTo(18f)
            close()
            moveTo(16f, 6f)
            verticalLineTo(18f)
            horizontalLineTo(18f)
            verticalLineTo(6f)
            close()
        }
    }

    val MusicNote: ImageVector by lazy {
        vectorIcon("MusicNote") {
            moveTo(12f, 3f)
            verticalLineTo(13.55f)
            curveTo(11.41f, 13.21f, 10.73f, 13f, 10f, 13f)
            curveTo(7.79f, 13f, 6f, 14.79f, 6f, 17f)
            reflectiveCurveTo(7.79f, 21f, 10f, 21f)
            reflectiveCurveTo(14f, 19.21f, 14f, 17f)
            verticalLineTo(7f)
            horizontalLineTo(18f)
            verticalLineTo(3f)
            horizontalLineTo(12f)
            close()
        }
    }

    val Audiobook: ImageVector by lazy {
        vectorIcon("Audiobook") {
            moveTo(18f, 2f)
            horizontalLineTo(6f)
            curveTo(4.9f, 2f, 4f, 2.9f, 4f, 4f)
            verticalLineTo(20f)
            curveTo(4f, 21.1f, 4.9f, 22f, 6f, 22f)
            horizontalLineTo(18f)
            curveTo(19.1f, 22f, 20f, 21.1f, 20f, 20f)
            verticalLineTo(4f)
            curveTo(20f, 2.9f, 19.1f, 2f, 18f, 2f)
            close()
            moveTo(6f, 4f)
            horizontalLineTo(11f)
            verticalLineTo(12f)
            lineTo(8.5f, 10.5f)
            lineTo(6f, 12f)
            verticalLineTo(4f)
            close()
        }
    }

    val Document: ImageVector by lazy {
        vectorIcon("Document") {
            moveTo(14f, 2f)
            horizontalLineTo(6f)
            curveTo(4.9f, 2f, 4f, 2.9f, 4f, 4f)
            verticalLineTo(20f)
            curveTo(4f, 21.1f, 4.9f, 22f, 6f, 22f)
            horizontalLineTo(18f)
            curveTo(19.1f, 22f, 20f, 21.1f, 20f, 20f)
            verticalLineTo(8f)
            lineTo(14f, 2f)
            close()
            moveTo(16f, 18f)
            horizontalLineTo(8f)
            verticalLineTo(16f)
            horizontalLineTo(16f)
            verticalLineTo(18f)
            close()
            moveTo(16f, 14f)
            horizontalLineTo(8f)
            verticalLineTo(12f)
            horizontalLineTo(16f)
            verticalLineTo(14f)
            close()
            moveTo(13f, 9f)
            verticalLineTo(3.5f)
            lineTo(18.5f, 9f)
            horizontalLineTo(13f)
            close()
        }
    }

    val FolderOpen: ImageVector by lazy {
        vectorIcon("FolderOpen") {
            moveTo(20f, 6f)
            horizontalLineTo(12f)
            lineTo(10f, 4f)
            horizontalLineTo(4f)
            curveTo(2.9f, 4f, 2.01f, 4.9f, 2.01f, 6f)
            lineTo(2f, 18f)
            curveTo(2f, 19.1f, 2.9f, 20f, 4f, 20f)
            horizontalLineTo(20f)
            curveTo(21.1f, 20f, 22f, 19.1f, 22f, 18f)
            verticalLineTo(8f)
            curveTo(22f, 6.9f, 21.1f, 6f, 20f, 6f)
            close()
            moveTo(20f, 18f)
            horizontalLineTo(4f)
            verticalLineTo(8f)
            horizontalLineTo(20f)
            verticalLineTo(18f)
            close()
        }
    }

    val ShieldCheck: ImageVector by lazy {
        vectorIcon("ShieldCheck") {
            moveTo(12f, 1f)
            lineTo(3f, 5f)
            verticalLineTo(11f)
            curveTo(3f, 16.55f, 6.84f, 21.74f, 12f, 23f)
            curveTo(17.16f, 21.74f, 21f, 16.55f, 21f, 11f)
            verticalLineTo(5f)
            lineTo(12f, 1f)
            close()
            moveTo(10f, 17f)
            lineTo(6f, 13f)
            lineTo(7.41f, 11.59f)
            lineTo(10f, 14.17f)
            lineTo(16.59f, 7.58f)
            lineTo(18f, 9f)
            lineTo(10f, 17f)
            close()
        }
    }

    private fun vectorIcon(
        name: String,
        builder: androidx.compose.ui.graphics.vector.PathBuilder.() -> Unit,
    ): ImageVector = ImageVector.Builder(
        name = name,
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 24f,
        viewportHeight = 24f,
    ).path(
        fill = SolidColor(Color.Black),
        pathFillType = PathFillType.NonZero,
        pathBuilder = builder,
    ).build()
}

fun formatMediaClock(milliseconds: Long): String {
    val totalSeconds = milliseconds.coerceAtLeast(0L) / 1_000L
    val hours = totalSeconds / 3_600L
    val minutes = (totalSeconds / 60L) % 60L
    val seconds = totalSeconds % 60L
    return if (hours > 0L) "%d:%02d:%02d".format(hours, minutes, seconds) else "%d:%02d".format(minutes, seconds)
}

@Composable
fun PlaybackSeekBar(
    positionMs: Long,
    durationMs: Long,
    onSeekTo: (Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    val hasKnownDuration = durationMs > 0L
    val liveFraction = playbackPositionFraction(positionMs, durationMs)
    var isDragging by remember { mutableStateOf(false) }
    var dragFraction by remember { mutableFloatStateOf(liveFraction) }

    LaunchedEffect(liveFraction, isDragging) {
        if (!isDragging) {
            dragFraction = liveFraction
        }
    }

    val displayedFraction = if (isDragging) dragFraction else liveFraction
    val displayedPositionMs = if (isDragging && hasKnownDuration) {
        playbackPositionAtFraction(dragFraction, durationMs)
    } else {
        positionMs.coerceAtLeast(0L)
    }

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Slider(
            value = displayedFraction,
            onValueChange = { fraction ->
                isDragging = true
                dragFraction = fraction.coerceIn(0f, 1f)
            },
            onValueChangeFinished = {
                val targetPositionMs = playbackPositionAtFraction(dragFraction, durationMs)
                isDragging = false
                if (hasKnownDuration) {
                    onSeekTo(targetPositionMs)
                }
            },
            enabled = hasKnownDuration,
            valueRange = 0f..1f,
            modifier = Modifier
                .fillMaxWidth()
                .testTag("playback_seek_bar"),
            colors = SliderDefaults.colors(
                thumbColor = MaterialTheme.colorScheme.primary,
                activeTrackColor = MaterialTheme.colorScheme.primary,
                inactiveTrackColor = MaterialTheme.colorScheme.surfaceVariant,
            ),
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                text = formatMediaClock(displayedPositionMs),
                style = MaterialTheme.typography.labelSmall,
                color = if (isDragging) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = if (hasKnownDuration) formatMediaClock(durationMs) else "--:--",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
fun PlayerTransportControls(
    isPlaying: Boolean,
    onPrevious: () -> Unit,
    onTogglePlayPause: () -> Unit,
    onNext: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(20.dp, Alignment.CenterHorizontally),
    ) {
        OutlinedIconButton(
            onClick = onPrevious,
            modifier = Modifier
                .size(52.dp)
                .testTag("player_prev_button"),
        ) {
            Icon(
                imageVector = MurmurIcons.SkipPrevious,
                contentDescription = "Previous",
            )
        }
        FilledIconButton(
            onClick = onTogglePlayPause,
            modifier = Modifier
                .size(64.dp)
                .testTag("player_play_pause_button"),
        ) {
            Icon(
                imageVector = if (isPlaying) MurmurIcons.Pause else MurmurIcons.Play,
                contentDescription = if (isPlaying) "Pause" else "Play",
                modifier = Modifier.size(30.dp),
            )
        }
        OutlinedIconButton(
            onClick = onNext,
            modifier = Modifier
                .size(52.dp)
                .testTag("player_next_button"),
        ) {
            Icon(
                imageVector = MurmurIcons.SkipNext,
                contentDescription = "Next",
            )
        }
    }
}

@Composable
fun MurmurMiniPlayerBar(
    snapshot: PlaybackSnapshot,
    onOpen: () -> Unit,
    onPrevious: () -> Unit,
    onToggle: () -> Unit,
    onNext: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val current = snapshot.current ?: return
    val progress = playbackPositionFraction(snapshot.positionMs, snapshot.durationMs)
    val contentIcon = when (current.contentType) {
        PlaybackContentType.MUSIC -> MurmurIcons.MusicNote
        PlaybackContentType.AUDIOBOOK -> MurmurIcons.Audiobook
        PlaybackContentType.READ_ALOUD -> MurmurIcons.Document
    }

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .testTag("mini_player_bar"),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        tonalElevation = 4.dp,
        shadowElevation = 6.dp,
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            LinearProgressIndicator(
                progress = { progress },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(3.dp),
                color = MaterialTheme.colorScheme.primary,
                trackColor = MaterialTheme.colorScheme.surfaceVariant,
            )
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(onClick = onOpen)
                    .padding(start = 14.dp, end = 8.dp, top = 8.dp, bottom = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .background(MaterialTheme.colorScheme.primaryContainer, RoundedCornerShape(12.dp)),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = contentIcon,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.size(20.dp),
                    )
                }
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    Text(
                        text = current.title,
                        style = MaterialTheme.typography.titleSmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = buildString {
                            append(current.subtitle ?: current.contentType.displayName)
                            if (snapshot.durationMs > 0L) {
                                append(" · ")
                                append(formatMediaClock(snapshot.positionMs))
                            }
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                IconButton(
                    onClick = onPrevious,
                    modifier = Modifier
                        .size(48.dp)
                        .testTag("mini_player_prev_button"),
                ) {
                    Icon(
                        imageVector = MurmurIcons.SkipPrevious,
                        contentDescription = "Previous",
                    )
                }
                FilledTonalIconButton(
                    onClick = onToggle,
                    modifier = Modifier
                        .size(48.dp)
                        .testTag("mini_player_play_pause_button"),
                ) {
                    Icon(
                        imageVector = if (snapshot.isPlaying) MurmurIcons.Pause else MurmurIcons.Play,
                        contentDescription = if (snapshot.isPlaying) "Pause" else "Play",
                    )
                }
                IconButton(
                    onClick = onNext,
                    modifier = Modifier
                        .size(48.dp)
                        .testTag("mini_player_next_button"),
                ) {
                    Icon(
                        imageVector = MurmurIcons.SkipNext,
                        contentDescription = "Next",
                    )
                }
            }
        }
    }
}

@Composable
fun EditorialHomeScreen(
    playback: PlaybackSnapshot,
    musicCount: Int,
    audiobooks: List<Audiobook>,
    documents: List<ReaderDocument>,
    onOpenMusic: () -> Unit,
    onOpenAudiobooks: () -> Unit,
    onOpenDocuments: () -> Unit,
    onOpenNowPlaying: () -> Unit,
    onTogglePlayback: () -> Unit,
    onResumeAudiobook: (Audiobook) -> Unit,
    onResumeDocument: (ReaderDocument) -> Unit,
    onOpenPrivacy: () -> Unit,
) {
    val resumeAudiobook = remember(audiobooks) {
        audiobooks.firstOrNull { it.isInProgress }
    }
    val resumeDocument = remember(documents) {
        documents.firstOrNull { it.positionChars > 0L }
    }

    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.TopCenter,
    ) {
        Column(
            modifier = Modifier
                .widthIn(max = 640.dp)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 22.dp, vertical = 20.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Surface(
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.primaryContainer,
                ) {
                    Text(
                        text = "OFFLINE SANCTUARY · ZERO CLOUD",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 5.dp),
                    )
                }
                Text(
                    text = "A quiet place for your local media.",
                    style = MaterialTheme.typography.headlineMedium,
                    color = MaterialTheme.colorScheme.onBackground,
                )
                Text(
                    text = "Choose local audio, or open a document to read and listen in one place.",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            val active = playback.current
            when {
                active != null -> {
                    Card(
                        onClick = onOpenNowPlaying,
                        shape = RoundedCornerShape(24.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.primaryContainer,
                            contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("hero_now_playing_card"),
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(20.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            Text(
                                text = "NOW PLAYING · ${active.contentType.displayName.uppercase()}",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.primary,
                            )
                            Text(
                                text = active.title,
                                style = MaterialTheme.typography.headlineSmall,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                            )
                            active.subtitle?.let { subtitle ->
                                Text(
                                    text = subtitle,
                                    style = MaterialTheme.typography.bodyMedium,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                            LinearProgressIndicator(
                                progress = { playbackPositionFraction(playback.positionMs, playback.durationMs) },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(6.dp)
                                    .clip(CircleShape),
                                color = MaterialTheme.colorScheme.primary,
                                trackColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.55f),
                            )
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween,
                            ) {
                                Text(
                                    text = if (playback.durationMs > 0L) {
                                        "${formatMediaClock(playback.positionMs)} / ${formatMediaClock(playback.durationMs)}"
                                    } else {
                                        formatMediaClock(playback.positionMs)
                                    },
                                    style = MaterialTheme.typography.labelMedium,
                                )
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    OutlinedButton(
                                        onClick = onOpenNowPlaying,
                                        modifier = Modifier.testTag("hero_open_player_button"),
                                    ) {
                                        Text("Open player")
                                    }
                                    Button(
                                        onClick = onTogglePlayback,
                                        modifier = Modifier.testTag("hero_play_pause_button"),
                                    ) {
                                        Text(if (playback.isPlaying) "Pause" else "Resume")
                                    }
                                }
                            }
                        }
                    }
                }
                resumeAudiobook != null -> {
                    Card(
                        onClick = { onResumeAudiobook(resumeAudiobook) },
                        shape = RoundedCornerShape(24.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.tertiaryContainer,
                            contentColor = MaterialTheme.colorScheme.onTertiaryContainer,
                        ),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(20.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            Text(
                                text = "PICK UP LISTENING",
                                style = MaterialTheme.typography.labelMedium,
                            )
                            Text(
                                text = resumeAudiobook.title,
                                style = MaterialTheme.typography.headlineSmall,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                            )
                            val pct = ((resumeAudiobook.progressFraction ?: 0f) * 100).toInt()
                            Text(
                                text = "${resumeAudiobook.author ?: "Audiobook"} · $pct% complete",
                                style = MaterialTheme.typography.bodyMedium,
                            )
                            Button(
                                onClick = { onResumeAudiobook(resumeAudiobook) },
                                modifier = Modifier.testTag("hero_resume_audiobook_button"),
                            ) {
                                Text("Resume audiobook")
                            }
                        }
                    }
                }
                resumeDocument != null -> {
                    Card(
                        onClick = { onResumeDocument(resumeDocument) },
                        shape = RoundedCornerShape(24.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.secondaryContainer,
                            contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                        ),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(20.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            Text(
                                text = "CONTINUE READING",
                                style = MaterialTheme.typography.labelMedium,
                            )
                            Text(
                                text = resumeDocument.title,
                                style = MaterialTheme.typography.headlineSmall,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                            )
                            Text(
                                text = "Saved at position ${resumeDocument.positionChars + 1}",
                                style = MaterialTheme.typography.bodyMedium,
                            )
                            Button(
                                onClick = { onResumeDocument(resumeDocument) },
                                modifier = Modifier.testTag("hero_resume_document_button"),
                            ) {
                                Text("Open reader")
                            }
                        }
                    }
                }
                else -> {
                    Card(
                        shape = RoundedCornerShape(24.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Column(modifier = Modifier.fillMaxWidth()) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(156.dp),
                            ) {
                                Image(
                                    painter = painterResource(id = R.drawable.img_sanctuary_banner_1793349915171),
                                    contentDescription = "Warm offline reading room illustration",
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier.fillMaxSize(),
                                )
                                Box(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .background(
                                            Brush.verticalGradient(
                                                colors = listOf(
                                                    Color.Transparent,
                                                    MaterialTheme.colorScheme.surfaceContainer.copy(alpha = 0.85f),
                                                ),
                                            ),
                                        ),
                                )
                            }
                            Column(
                                modifier = Modifier.padding(horizontal = 18.dp, vertical = 14.dp),
                                verticalArrangement = Arrangement.spacedBy(10.dp),
                            ) {
                                Text(
                                    text = "Your private desk for albums, chapters, and pages",
                                    style = MaterialTheme.typography.titleMedium,
                                )
                                Text(
                                    text = "Import folders or individual files directly from device storage—no accounts, cloud sync, or background telemetry.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                    FilledTonalButton(
                                        onClick = onOpenMusic,
                                        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp),
                                    ) {
                                        Icon(MurmurIcons.FolderOpen, contentDescription = null, modifier = Modifier.size(18.dp))
                                        Spacer(Modifier.width(6.dp))
                                        Text("Browse audio")
                                    }
                                    OutlinedButton(
                                        onClick = onOpenDocuments,
                                        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp),
                                    ) {
                                        Icon(MurmurIcons.Document, contentDescription = null, modifier = Modifier.size(18.dp))
                                        Spacer(Modifier.width(6.dp))
                                        Text("Open document")
                                    }
                                }
                            }
                        }
                    }
                }
            }

            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                EditorialDestinationCard(
                    title = "Music",
                    description = "Browse music on this device",
                    badge = musicCount.takeIf { it > 0 }?.let { "$it tracks" },
                    icon = MurmurIcons.MusicNote,
                    testTag = "destination-music",
                    onClick = onOpenMusic,
                )
                EditorialDestinationCard(
                    title = "Audiobooks",
                    description = "Keep your place in every book",
                    badge = audiobooks.size.takeIf { it > 0 }?.let { "$it books" },
                    icon = MurmurIcons.Audiobook,
                    testTag = "destination-audiobooks",
                    onClick = onOpenAudiobooks,
                )
                EditorialDestinationCard(
                    title = "Read & Listen",
                    description = "Read documents and listen aloud in one place",
                    badge = documents.size.takeIf { it > 0 }?.let { "$it docs" },
                    icon = MurmurIcons.Document,
                    testTag = "destination-documents",
                    onClick = onOpenDocuments,
                )
            }

            OutlinedCard(
                onClick = onOpenPrivacy,
                shape = RoundedCornerShape(18.dp),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                colors = CardDefaults.outlinedCardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("open_privacy_notice_button"),
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Icon(
                        imageVector = MurmurIcons.ShieldCheck,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(22.dp),
                    )
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "100% Offline · Zero Data Collected",
                            style = MaterialTheme.typography.titleSmall,
                        )
                        Text(
                            text = "Privacy, local storage, TTS cache & legal notice",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Text(
                        text = "View",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
            }
        }
    }
}

@Composable
private fun EditorialDestinationCard(
    title: String,
    description: String,
    badge: String?,
    icon: ImageVector,
    testTag: String,
    onClick: () -> Unit,
) {
    OutlinedCard(
        onClick = onClick,
        shape = RoundedCornerShape(20.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        colors = CardDefaults.outlinedCardColors(containerColor = MaterialTheme.colorScheme.surface),
        modifier = Modifier
            .fillMaxWidth()
            .testTag(testTag),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 18.dp, vertical = 18.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Box(
                modifier = Modifier
                    .size(46.dp)
                    .background(MaterialTheme.colorScheme.primaryContainer, RoundedCornerShape(14.dp)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier.size(22.dp),
                )
            }
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(3.dp),
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                    if (badge != null) {
                        Surface(
                            shape = CircleShape,
                            color = MaterialTheme.colorScheme.secondaryContainer,
                        ) {
                            Text(
                                text = badge,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSecondaryContainer,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                            )
                        }
                    }
                }
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Text(
                text = "Open",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
            )
        }
    }
}

@Composable
fun PrivacyLegalScreen(
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 18.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text(
            text = "Privacy & Legal Notice",
            style = MaterialTheme.typography.headlineSmall,
        )
        Text(
            text = "Murmur is an offline-only personal reader and audio player. Your library, listening positions, bookmarks, and preferences never leave this device unless you explicitly export a backup file.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        PrivacySectionCard(
            title = "1. Zero Network & Zero Telemetry",
            body = "Murmur does not declare android.permission.INTERNET and contains no analytics SDKs, crash reporters, advertising identifiers, session replay tools, or cloud accounts. When Strict Offline Speech is enabled (default), Murmur also blocks Android system Text-to-Speech voices that report requiring a network connection.",
        )
        PrivacySectionCard(
            title = "2. Personal Data, Children's Privacy & Global Laws",
            body = "Because Murmur collects, transmits, and shares zero personal information or device identifiers, it performs no profiling under GDPR/CCPA, no third-party wiretapping or tracking under CIPA, no commercial messaging under CAN-SPAM, no paid subscriptions or recurring billing under California ARL, and collects zero data from children under COPPA.",
        )
        PrivacySectionCard(
            title = "3. On-Device Storage & Unencrypted Speech Cache",
            body = "Murmur stores playlists, reading positions, and bookmarks in an app-private Room database protected by Android's application sandbox and OS file-based encryption. Synthesized Read Aloud audio segments are cached as unencrypted WAV files inside app-private cache storage (cacheDir/read-aloud) and can be cleared anytime in Settings → Storage. Exported backup JSON files are saved unencrypted to the folder you select via Android's system file picker.",
        )
        PrivacySectionCard(
            title = "4. Platform Capabilities & Permissions",
            body = "Optional audio library discovery uses Android's READ_MEDIA_AUDIO (or READ_EXTERNAL_STORAGE on Android 12 and earlier) solely for local indexing. You can also import folders or individual files without granting broad media permissions. PDF visual page rendering works on all supported Android versions, while PDF text extraction, search, and Read Aloud require Android 15 (API 35) or newer.",
        )
        PrivacySectionCard(
            title = "5. User-Supplied Media & Copyright",
            body = "Murmur ships with zero third-party music, audiobooks, or books. It plays and reads only local files chosen by you on your device and does not host, index, or distribute external content.",
        )
    }
}

@Composable
private fun PrivacySectionCard(
    title: String,
    body: String,
) {
    OutlinedCard(
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary,
            )
            Text(
                text = body,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
