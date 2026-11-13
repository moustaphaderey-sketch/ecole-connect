package com.example.util

import com.example.data.model.AssessmentGrade
import com.example.data.model.Subject
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

object DateUtils {
    private val isoFormat = SimpleDateFormat("yyyy-MM-dd", Locale.US)
    private val frShortFormat = SimpleDateFormat("dd/MM/yyyy", Locale.FRANCE)
    private val frLongFormat = SimpleDateFormat("EEEE d MMMM yyyy", Locale.FRANCE)

    fun todayIso(): String = isoFormat.format(Date())

    fun daysAgoIso(days: Int): String {
        val cal = Calendar.getInstance()
        cal.add(Calendar.DAY_OF_YEAR, -days)
        return isoFormat.format(cal.time)
    }

    fun formatFrenchShort(isoDate: String): String {
        return try {
            val parsed = isoFormat.parse(isoDate) ?: return isoDate
            frShortFormat.format(parsed)
        } catch (_: Exception) {
            isoDate
        }
    }

    fun formatFrenchLong(isoDate: String): String {
        return try {
            val parsed = isoFormat.parse(isoDate) ?: return isoDate
            frLongFormat.format(parsed).replaceFirstChar { it.uppercase(Locale.FRANCE) }
        } catch (_: Exception) {
            isoDate
        }
    }
}

data class SubjectAverageSummary(
    val subjectName: String,
    val subjectId: String,
    val coefficient: Double,
    val averageOn20: Double,
    val gradesCount: Int
)

object GradeCalculator {
    fun computeSubjectAverages(
        grades: List<AssessmentGrade>,
        subjects: List<Subject>,
        period: String? = null
    ): List<SubjectAverageSummary> {
        val filtered = if (period.isNullOrBlank()) grades else grades.filter { it.period == period }
        if (filtered.isEmpty()) return emptyList()
        val subjectMap = subjects.associateBy { it.id }
        return filtered.groupBy { it.subjectId }.map { (subjectId, list) ->
            val first = list.first()
            val subj = subjectMap[subjectId]
            var weightedSum = 0.0
            var coeffSum = 0.0
            list.forEach { g ->
                val normalized20 = if (g.maxScore > 0) (g.score / g.maxScore) * 20.0 else g.score
                val w = if (g.coefficient > 0) g.coefficient else 1.0
                weightedSum += normalized20 * w
                coeffSum += w
            }
            val avg = if (coeffSum > 0) weightedSum / coeffSum else 0.0
            SubjectAverageSummary(
                subjectName = subj?.name ?: first.subjectName.ifBlank { "Matière" },
                subjectId = subjectId,
                coefficient = subj?.coefficient ?: first.coefficient,
                averageOn20 = avg,
                gradesCount = list.size
            )
        }.sortedBy { it.subjectName }
    }

    fun computeOverallAverage(subjectAverages: List<SubjectAverageSummary>): Double? {
        if (subjectAverages.isEmpty()) return null
        var weightedSum = 0.0
        var coeffSum = 0.0
        subjectAverages.forEach { s ->
            val w = if (s.coefficient > 0) s.coefficient else 1.0
            weightedSum += s.averageOn20 * w
            coeffSum += w
        }
        return if (coeffSum > 0) weightedSum / coeffSum else null
    }
}
