package com.apollo.overload.widget

import android.content.Context
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.doublePreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.glance.GlanceId
import androidx.glance.action.ActionParameters
import androidx.glance.appwidget.action.ActionCallback
import androidx.glance.appwidget.state.getAppWidgetState
import androidx.glance.appwidget.state.updateAppWidgetState
import androidx.glance.appwidget.updateAll
import androidx.glance.state.PreferencesGlanceStateDefinition
import androidx.room.withTransaction
import com.apollo.overload.OverloadApp
import com.apollo.overload.data.logSet
import com.apollo.overload.data.nextDay
import com.apollo.overload.data.startExercise
import com.apollo.overload.data.startWorkout
import com.apollo.overload.domain.Progression
import com.apollo.overload.domain.Session
import kotlinx.coroutines.delay

/** Widget instance state (Glance preferences): the − / + draft of one set, and the short "Logged" flash. */
internal object WidgetPrefs {
    val draftKey = stringPreferencesKey("draft")
    val draftKg = doublePreferencesKey("draftKg")
    val draftReps = intPreferencesKey("draftReps")
    val flash = booleanPreferencesKey("flash")

    fun draft(p: Preferences): Draft? {
        val parts = p[draftKey]?.split(':')?.mapNotNull { it.toLongOrNull() }?.takeIf { it.size == 3 } ?: return null
        return Draft(parts[0], parts[1], parts[2].toInt(), p[draftKg], p[draftReps])
    }
}

internal object WidgetParams {
    val workout = ActionParameters.Key<Long>("workout")
    val planEx = ActionParameters.Key<Long>("planEx")
    val setIndex = ActionParameters.Key<Int>("setIndex")
    val kg = ActionParameters.Key<Double>("kg")
    val reps = ActionParameters.Key<Int>("reps")
    val stepKg = ActionParameters.Key<Double>("stepKg")
    val stepReps = ActionParameters.Key<Int>("stepReps")
}

private fun Context.app() = applicationContext as OverloadApp

/**
 * − / + on kg (by the plan row's load step) or reps (by 1). kg / reps carry the shown values, the base when this set
 * has no draft yet; quick taps add up on the stored draft.
 */
class StepAction : ActionCallback {
    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) {
        val key = Draft.key(parameters[WidgetParams.workout] ?: return, parameters[WidgetParams.planEx] ?: return, parameters[WidgetParams.setIndex] ?: return)
        val dKg = parameters[WidgetParams.stepKg] ?: 0.0
        val dReps = parameters[WidgetParams.stepReps] ?: 0
        updateAppWidgetState(context, glanceId) { p ->
            if (p[WidgetPrefs.draftKey] != key) {
                p[WidgetPrefs.draftKey] = key
                p.remove(WidgetPrefs.draftKg)
                p.remove(WidgetPrefs.draftReps)
            }
            // Same bounds as the session screen's dials.
            if (dKg != 0.0) p[WidgetPrefs.draftKg] =
                Progression.round2(((p[WidgetPrefs.draftKg] ?: parameters[WidgetParams.kg] ?: 0.0) + dKg).coerceAtLeast(0.0))
            if (dReps != 0) p[WidgetPrefs.draftReps] = ((p[WidgetPrefs.draftReps] ?: parameters[WidgetParams.reps] ?: 1) + dReps).coerceAtLeast(1)
        }
        OverloadWidget().update(context, glanceId)
    }
}

/** Logs the shown kg × reps as the set the widget showed; a stale tap (the set is already logged) does nothing. */
class LogAction : ActionCallback {
    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) {
        val workoutId = parameters[WidgetParams.workout] ?: return
        val planExId = parameters[WidgetParams.planEx] ?: return
        val setIndex = parameters[WidgetParams.setIndex] ?: return
        // The draft as it is now: the action's values come from the last render and can miss a quick − / + tap.
        val draft = WidgetPrefs.draft(getAppWidgetState(context, PreferencesGlanceStateDefinition, glanceId))
            ?.takeIf { it.key == Draft.key(workoutId, planExId, setIndex) }
        val kg = draft?.kg ?: parameters[WidgetParams.kg] ?: return
        val reps = draft?.reps ?: parameters[WidgetParams.reps] ?: return
        val app = context.app()
        app.ready.await()
        val repo = app.repo
        val dao = repo.dao
        val logged = repo.db.withTransaction {
            val w = dao.openWorkoutNow()?.takeIf { it.id == workoutId } ?: return@withTransaction false
            val types = dao.allExercises().associate { it.id to it.type }
            val s = Session(w.todayPlan(dao.dayExercisesNow(w.dayId)), w.order, dao.setsNow(w.id), types, w.setCounts)
            val pe = s.planRow(planExId) ?: return@withTransaction false
            if (s.active != planExId || s.today(planExId).size != setIndex) return@withTransaction false
            repo.logSet(w.id, pe, setIndex, kg, reps, finishes = setIndex + 1 >= s.target(planExId))
            true
        }
        if (!logged) { OverloadWidget().updateAll(context); return }
        updateAppWidgetState(context, glanceId) { p ->
            p.remove(WidgetPrefs.draftKey); p.remove(WidgetPrefs.draftKg); p.remove(WidgetPrefs.draftReps)
            p[WidgetPrefs.flash] = true
        }
        OverloadWidget().updateAll(context)
        delay(FLASH_MS)
        updateAppWidgetState(context, glanceId) { it.remove(WidgetPrefs.flash) }
        OverloadWidget().update(context, glanceId)
    }

    private companion object {
        const val FLASH_MS = 650L
    }
}

/** Idle Start: starts the next day in the rotation, exactly like the app's Start. */
class StartWorkoutAction : ActionCallback {
    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) {
        val app = context.app()
        app.ready.await()
        app.repo.nextDay()?.let { app.repo.startWorkout(it) }
        OverloadWidget().updateAll(context)
    }
}

/** Next up → Start: starts the next exercise like the app's Start button there (leaves "Next up"). */
class StartExerciseAction : ActionCallback {
    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) {
        val app = context.app()
        app.ready.await()
        app.repo.startExercise(parameters[WidgetParams.workout] ?: return, parameters[WidgetParams.planEx] ?: return)
        OverloadWidget().updateAll(context)
    }
}
