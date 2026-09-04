package com.bolin.photohelper.guide

import android.content.Context

class GuideProgress(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences(NAME, Context.MODE_PRIVATE)

    fun completedLessonIds(): Set<String> =
        prefs.getStringSet(COMPLETED_LESSONS, emptySet()).orEmpty()

    fun isLessonComplete(lessonId: String): Boolean =
        completedLessonIds().contains(lessonId)

    fun markLessonComplete(lessonId: String) {
        val updated = completedLessonIds().toMutableSet().apply { add(lessonId) }
        prefs.edit().putStringSet(COMPLETED_LESSONS, updated).apply()
    }

    fun markLessonIncomplete(lessonId: String) {
        val updated = completedLessonIds().toMutableSet().apply { remove(lessonId) }
        prefs.edit().putStringSet(COMPLETED_LESSONS, updated).apply()
    }

    fun moduleLessonsComplete(module: GuideModule): Int =
        module.lessons.count { isLessonComplete(it.id) }

    fun isModuleComplete(module: GuideModule): Boolean =
        moduleLessonsComplete(module) == module.lessons.size

    fun overallComplete(): Int =
        GUIDE_MODULES.sumOf { moduleLessonsComplete(it) }

    fun overallTotal(): Int =
        GUIDE_MODULES.sumOf { it.lessons.size }

    fun overallFraction(): Float {
        val total = overallTotal()
        return if (total == 0) 0f else overallComplete().toFloat() / total
    }

    fun nextIncompleteLesson(module: GuideModule): GuideLesson? =
        module.lessons.firstOrNull { !isLessonComplete(it.id) }

    private companion object {
        const val NAME = "guide_progress"
        const val COMPLETED_LESSONS = "completed_lessons"
    }
}
