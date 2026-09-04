package com.bolin.photohelper.guide

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Camera
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

sealed interface GuideNav {
    data object ModuleList : GuideNav
    data class LessonList(val moduleId: String) : GuideNav
    data class LessonDetail(val moduleId: String, val lessonId: String) : GuideNav
    data class ModuleComplete(val moduleId: String) : GuideNav
}

@Composable
fun GuideScreen(
    progress: GuideProgress,
    onDismiss: () -> Unit,
    onStartExercise: (GuidedExercise, String) -> Unit,
) {
    var nav by remember { mutableStateOf<GuideNav>(GuideNav.ModuleList) }
    var completedIds by remember { mutableStateOf(progress.completedLessonIds()) }

    fun isComplete(id: String) = id in completedIds
    fun moduleComplete(m: GuideModule) = m.lessons.count { isComplete(it.id) }
    fun overallDone() = GUIDE_MODULES.sumOf { moduleComplete(it) }
    fun overallTotal() = GUIDE_MODULES.sumOf { it.lessons.size }

    fun markDone(lesson: GuideLesson, module: GuideModule) {
        progress.markLessonComplete(lesson.id)
        completedIds = progress.completedLessonIds()
        if (module.lessons.all { isComplete(it.id) }) {
            nav = GuideNav.ModuleComplete(module.id)
        } else {
            val next = module.lessons.firstOrNull { !isComplete(it.id) }
            if (next != null) {
                nav = GuideNav.LessonDetail(module.id, next.id)
            } else {
                nav = GuideNav.LessonList(module.id)
            }
        }
    }

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background,
    ) {
        AnimatedContent(
            targetState = nav,
            transitionSpec = {
                val forward = targetState.depth() >= initialState.depth()
                if (forward) {
                    (slideInHorizontally { it / 3 } + fadeIn()) togetherWith
                        (slideOutHorizontally { -it / 3 } + fadeOut())
                } else {
                    (slideInHorizontally { -it / 3 } + fadeIn()) togetherWith
                        (slideOutHorizontally { it / 3 } + fadeOut())
                }
            },
            label = "guide_nav",
        ) { screen ->
            when (screen) {
                is GuideNav.ModuleList -> ModuleListPane(
                    overallDone = overallDone(),
                    overallTotal = overallTotal(),
                    moduleDone = { moduleComplete(it) },
                    onModuleTap = { m ->
                        if (!m.comingSoon) nav = GuideNav.LessonList(m.id)
                    },
                    onBack = onDismiss,
                )
                is GuideNav.LessonList -> {
                    val module = GUIDE_MODULES.first { it.id == screen.moduleId }
                    LessonListPane(
                        module = module,
                        isComplete = ::isComplete,
                        onLessonTap = { l -> nav = GuideNav.LessonDetail(module.id, l.id) },
                        onBack = { nav = GuideNav.ModuleList },
                    )
                }
                is GuideNav.LessonDetail -> {
                    val module = GUIDE_MODULES.first { it.id == screen.moduleId }
                    val lesson = module.lessons.first { it.id == screen.lessonId }
                    LessonDetailPane(
                        module = module,
                        lesson = lesson,
                        isDone = isComplete(lesson.id),
                        onMarkDone = { markDone(lesson, module) },
                        onTryIt = { lesson.exercise?.let { onStartExercise(it, lesson.id); onDismiss() } },
                        onBack = { nav = GuideNav.LessonList(module.id) },
                    )
                }
                is GuideNav.ModuleComplete -> {
                    val module = GUIDE_MODULES.first { it.id == screen.moduleId }
                    val nextModule = GUIDE_MODULES
                        .dropWhile { it.id != module.id }
                        .drop(1)
                        .firstOrNull { !it.comingSoon }
                    ModuleCompletePane(
                        module = module,
                        nextModule = nextModule,
                        onContinue = {
                            if (nextModule != null) {
                                nav = GuideNav.LessonList(nextModule.id)
                            } else {
                                nav = GuideNav.ModuleList
                            }
                        },
                        onBack = { nav = GuideNav.ModuleList },
                    )
                }
            }
        }
    }
}

private fun GuideNav.depth(): Int = when (this) {
    is GuideNav.ModuleList -> 0
    is GuideNav.LessonList -> 1
    is GuideNav.LessonDetail -> 2
    is GuideNav.ModuleComplete -> 2
}

// ── Module list ────────────────────────────────────────────────────

@Composable
private fun ModuleListPane(
    overallDone: Int,
    overallTotal: Int,
    moduleDone: (GuideModule) -> Int,
    onModuleTap: (GuideModule) -> Unit,
    onBack: () -> Unit,
) {
    Column(modifier = Modifier.fillMaxSize().safeDrawingPadding()) {
        GuideTopBar(title = "Photography guide", onBack = onBack)

        LazyColumn(
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            item {
                OverallProgressCard(done = overallDone, total = overallTotal)
                Spacer(Modifier.height(8.dp))
            }
            items(GUIDE_MODULES) { module ->
                val done = moduleDone(module)
                ModuleRow(
                    module = module,
                    done = done,
                    isModuleComplete = done == module.lessons.size && !module.comingSoon,
                    onClick = { onModuleTap(module) },
                )
            }
            item { Spacer(Modifier.height(16.dp)) }
        }
    }
}

@Composable
private fun OverallProgressCard(done: Int, total: Int) {
    val fraction = if (total == 0) 0f else done.toFloat() / total
    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant,
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            ProgressRing(fraction = fraction, size = 44)
            Column {
                Text(
                    "$done of $total lessons complete",
                    style = MaterialTheme.typography.titleSmall,
                )
                if (done < total) {
                    Text(
                        "Keep going — you're making great progress",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                } else {
                    Text(
                        "Congratulations — you finished the course!",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.tertiary,
                    )
                }
            }
        }
    }
}

@Composable
private fun ModuleRow(
    module: GuideModule,
    done: Int,
    isModuleComplete: Boolean,
    onClick: () -> Unit,
) {
    val alpha = if (module.comingSoon) 0.5f else 1f
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = !module.comingSoon, onClick = onClick),
        color = MaterialTheme.colorScheme.background,
    ) {
        Row(
            modifier = Modifier.padding(vertical = 12.dp, horizontal = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Surface(
                color = when {
                    module.comingSoon -> MaterialTheme.colorScheme.surfaceVariant
                    isModuleComplete -> MaterialTheme.colorScheme.tertiaryContainer
                    done > 0 -> MaterialTheme.colorScheme.primaryContainer
                    else -> MaterialTheme.colorScheme.surfaceVariant
                },
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier.size(44.dp),
            ) {
                Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                    Icon(
                        imageVector = if (module.comingSoon) Icons.Rounded.Lock else module.icon,
                        contentDescription = null,
                        tint = when {
                            module.comingSoon -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                            isModuleComplete -> MaterialTheme.colorScheme.onTertiaryContainer
                            done > 0 -> MaterialTheme.colorScheme.onPrimaryContainer
                            else -> MaterialTheme.colorScheme.onSurfaceVariant
                        },
                        modifier = Modifier.size(22.dp),
                    )
                }
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    module.title,
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = alpha),
                )
                Text(
                    if (module.comingSoon) "Coming soon"
                    else "$done of ${module.lessons.size} lessons",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = alpha),
                )
                if (!module.comingSoon) {
                    Spacer(Modifier.height(6.dp))
                    LinearProgressIndicator(
                        progress = { if (module.lessons.isEmpty()) 0f else done.toFloat() / module.lessons.size },
                        modifier = Modifier.fillMaxWidth().height(3.dp).clip(RoundedCornerShape(2.dp)),
                        color = if (isModuleComplete) MaterialTheme.colorScheme.tertiary
                                else MaterialTheme.colorScheme.primary,
                        trackColor = MaterialTheme.colorScheme.outlineVariant,
                        strokeCap = StrokeCap.Round,
                    )
                }
            }
            if (isModuleComplete) {
                Icon(
                    Icons.Rounded.Check,
                    contentDescription = "Complete",
                    tint = MaterialTheme.colorScheme.tertiary,
                    modifier = Modifier.size(22.dp),
                )
            } else if (!module.comingSoon) {
                Icon(
                    Icons.Rounded.ChevronRight,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(22.dp),
                )
            }
        }
    }
}

// ── Lesson list ────────────────────────────────────────────────────

@Composable
private fun LessonListPane(
    module: GuideModule,
    isComplete: (String) -> Boolean,
    onLessonTap: (GuideLesson) -> Unit,
    onBack: () -> Unit,
) {
    Column(modifier = Modifier.fillMaxSize().safeDrawingPadding()) {
        GuideTopBar(title = module.title, onBack = onBack)

        LazyColumn(
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            itemsIndexed(module.lessons) { index, lesson ->
                val done = isComplete(lesson.id)
                val firstIncompleteIndex = module.lessons.indexOfFirst { !isComplete(it.id) }
                val isCurrent = index == firstIncompleteIndex

                LessonRow(
                    index = index + 1,
                    lesson = lesson,
                    done = done,
                    isCurrent = isCurrent,
                    onClick = { onLessonTap(lesson) },
                )
            }
            item { Spacer(Modifier.height(16.dp)) }
        }
    }
}

@Composable
private fun LessonRow(
    index: Int,
    lesson: GuideLesson,
    done: Boolean,
    isCurrent: Boolean,
    onClick: () -> Unit,
) {
    Surface(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
        color = MaterialTheme.colorScheme.background,
    ) {
        Row(
            modifier = Modifier.padding(vertical = 12.dp, horizontal = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Surface(
                color = when {
                    done -> MaterialTheme.colorScheme.tertiaryContainer
                    isCurrent -> MaterialTheme.colorScheme.primaryContainer
                    else -> MaterialTheme.colorScheme.surfaceVariant
                },
                shape = CircleShape,
                modifier = Modifier.size(32.dp),
            ) {
                Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                    if (done) {
                        Icon(
                            Icons.Rounded.Check,
                            contentDescription = "Done",
                            tint = MaterialTheme.colorScheme.onTertiaryContainer,
                            modifier = Modifier.size(16.dp),
                        )
                    } else {
                        Text(
                            "$index",
                            style = MaterialTheme.typography.labelMedium,
                            color = if (isCurrent) MaterialTheme.colorScheme.onPrimaryContainer
                                    else MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
            Text(
                lesson.title,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.weight(1f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            LessonTagBadge(hasAction = lesson.exercise != null)
        }
    }
}

@Composable
private fun LessonTagBadge(hasAction: Boolean) {
    val label = if (hasAction) "Try it" else "Read"
    Surface(
        color = if (hasAction) MaterialTheme.colorScheme.primaryContainer
                else MaterialTheme.colorScheme.surfaceVariant,
        shape = RoundedCornerShape(6.dp),
    ) {
        Text(
            label,
            style = MaterialTheme.typography.labelSmall,
            color = if (hasAction) MaterialTheme.colorScheme.onPrimaryContainer
                    else MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
        )
    }
}

// ── Lesson detail ──────────────────────────────────────────────────

@Composable
private fun LessonDetailPane(
    module: GuideModule,
    lesson: GuideLesson,
    isDone: Boolean,
    onMarkDone: () -> Unit,
    onTryIt: () -> Unit,
    onBack: () -> Unit,
) {
    Column(modifier = Modifier.fillMaxSize().safeDrawingPadding()) {
        GuideTopBar(title = "Lesson ${lesson.id}", onBack = onBack)

        LazyColumn(
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            item {
                Text(
                    "${module.title} — lesson ${lesson.id}",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    lesson.title,
                    style = MaterialTheme.typography.headlineMedium,
                    modifier = Modifier.semantics { heading() },
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    lesson.description,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(8.dp))
            }
            items(lesson.tips) { tip ->
                TipCard(tip)
            }
            item {
                Spacer(Modifier.height(8.dp))
                if (lesson.exercise != null) {
                    Button(
                        onClick = onTryIt,
                        modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
                        shape = RoundedCornerShape(12.dp),
                    ) {
                        Icon(Icons.Rounded.Camera, contentDescription = null, modifier = Modifier.size(20.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("Try it now — back to camera")
                    }
                    Spacer(Modifier.height(8.dp))
                }
                if (!isDone) {
                    OutlinedButton(
                        onClick = onMarkDone,
                        modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
                        shape = RoundedCornerShape(12.dp),
                    ) {
                        Icon(Icons.Rounded.Check, contentDescription = null, modifier = Modifier.size(20.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("Mark as done")
                    }
                } else {
                    Surface(
                        color = MaterialTheme.colorScheme.tertiaryContainer,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Row(
                            modifier = Modifier.padding(14.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center,
                        ) {
                            Icon(
                                Icons.Rounded.Check,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onTertiaryContainer,
                                modifier = Modifier.size(20.dp),
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(
                                "Lesson complete",
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.onTertiaryContainer,
                            )
                        }
                    }
                }
                Spacer(Modifier.height(24.dp))
            }
        }
    }
}

@Composable
private fun TipCard(tip: Tip) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant,
        shape = RoundedCornerShape(12.dp),
        tonalElevation = if (tip.isAppAssist) 2.dp else 0.dp,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                if (tip.isAppAssist) {
                    Icon(
                        Icons.Rounded.AutoAwesome,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(16.dp),
                    )
                }
                Text(
                    tip.title,
                    style = MaterialTheme.typography.labelLarge,
                    color = if (tip.isAppAssist) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.onSurface,
                )
            }
            Spacer(Modifier.height(4.dp))
            Text(
                tip.body,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

// ── Module complete ────────────────────────────────────────────────

@Composable
private fun ModuleCompletePane(
    module: GuideModule,
    nextModule: GuideModule?,
    onContinue: () -> Unit,
    onBack: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .safeDrawingPadding()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Surface(
            color = MaterialTheme.colorScheme.tertiaryContainer,
            shape = CircleShape,
            modifier = Modifier.size(64.dp),
        ) {
            Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                Icon(
                    Icons.Rounded.Check,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onTertiaryContainer,
                    modifier = Modifier.size(32.dp),
                )
            }
        }
        Spacer(Modifier.height(16.dp))
        Text(
            "Module complete",
            style = MaterialTheme.typography.headlineMedium,
            modifier = Modifier.semantics { heading() },
        )
        Spacer(Modifier.height(6.dp))
        Text(
            "You finished all ${module.lessons.size} lessons in ${module.title}.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        if (nextModule != null) {
            Spacer(Modifier.height(24.dp))
            Surface(
                color = MaterialTheme.colorScheme.surfaceVariant,
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Text(
                        "Up next",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(6.dp))
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Surface(
                            color = MaterialTheme.colorScheme.primaryContainer,
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.size(36.dp),
                        ) {
                            Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                                Icon(
                                    nextModule.icon,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                    modifier = Modifier.size(18.dp),
                                )
                            }
                        }
                        Column {
                            Text(nextModule.title, style = MaterialTheme.typography.titleSmall)
                            Text(
                                "${nextModule.lessons.size} lessons",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
        }

        Spacer(Modifier.height(24.dp))
        Button(
            onClick = onContinue,
            modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
            shape = RoundedCornerShape(12.dp),
        ) {
            Text(if (nextModule != null) "Continue to next module" else "Back to guide")
        }
        Spacer(Modifier.height(8.dp))
        OutlinedButton(
            onClick = onBack,
            modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
            shape = RoundedCornerShape(12.dp),
        ) {
            Text("Back to all modules")
        }
    }
}

// ── Shared components ──────────────────────────────────────────────

@Composable
private fun GuideTopBar(title: String, onBack: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 4.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = onBack) {
            Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back")
        }
        Text(
            title,
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.weight(1f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun ProgressRing(fraction: Float, size: Int) {
    val color = if (fraction >= 1f) MaterialTheme.colorScheme.tertiary
                else MaterialTheme.colorScheme.primary
    val trackColor = MaterialTheme.colorScheme.outlineVariant
    val pct = (fraction * 100).toInt()

    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier.size(size.dp),
    ) {
        androidx.compose.foundation.Canvas(modifier = Modifier.fillMaxSize()) {
            val stroke = 4.dp.toPx()
            val arcSize = this.size.copy(
                width = this.size.width - stroke,
                height = this.size.height - stroke,
            )
            val topLeft = androidx.compose.ui.geometry.Offset(stroke / 2, stroke / 2)
            drawArc(
                color = trackColor,
                startAngle = -90f,
                sweepAngle = 360f,
                useCenter = false,
                topLeft = topLeft,
                size = arcSize,
                style = androidx.compose.ui.graphics.drawscope.Stroke(width = stroke, cap = StrokeCap.Round),
            )
            drawArc(
                color = color,
                startAngle = -90f,
                sweepAngle = 360f * fraction,
                useCenter = false,
                topLeft = topLeft,
                size = arcSize,
                style = androidx.compose.ui.graphics.drawscope.Stroke(width = stroke, cap = StrokeCap.Round),
            )
        }
        Text(
            "${pct}%",
            style = MaterialTheme.typography.labelSmall,
            color = color,
        )
    }
}
