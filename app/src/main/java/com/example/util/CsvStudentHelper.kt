package com.example.util

import com.example.data.model.AssessmentGrade
import com.example.data.model.AttendanceRecord
import com.example.data.model.School
import com.example.data.model.SchoolClass
import com.example.data.model.Student

data class CsvStudentRow(
    val matricule: String,
    val firstName: String,
    val lastName: String,
    val dateOfBirth: String,
    val gender: String,
    val className: String,
    val academicYear: String,
    val legalGuardianName: String,
    val legalGuardianPhone: String,
    val legalGuardianRelation: String,
    val academicHistory: String
)

object CsvStudentHelper {
    private val headerLine = listOf(
        "matricule",
        "nom",
        "prenom",
        "date_naissance",
        "sexe",
        "classe",
        "annee_scolaire",
        "responsable_nom",
        "responsable_tel",
        "responsable_lien",
        "historique"
    ).joinToString(",")

    fun exportStudentsToCsv(students: List<Student>): String {
        val sb = StringBuilder()
        sb.appendLine(headerLine)
        students.forEach { s ->
            sb.appendLine(
                listOf(
                    s.matricule,
                    s.lastName,
                    s.firstName,
                    s.dateOfBirth,
                    s.gender,
                    s.className,
                    s.academicYear,
                    s.legalGuardianName,
                    s.legalGuardianPhone,
                    s.legalGuardianRelation,
                    s.academicHistory
                ).joinToString(",") { escapeCsv(it) }
            )
        }
        return sb.toString()
    }

    fun parseStudentsFromCsv(csvContent: String): List<CsvStudentRow> {
        val cleaned = csvContent.removePrefix("\uFEFF")
        val lines = cleaned.lines().filter { it.isNotBlank() }
        if (lines.isEmpty()) return emptyList()

        val firstFields = splitCsvLine(lines.first()).map { it.trim().lowercase() }
        val hasHeader = firstFields.any {
            it in setOf("matricule", "nom", "prenom", "lastname", "firstname", "classe")
        }

        val colMap: Map<String, Int> = if (hasHeader) {
            firstFields.mapIndexed { idx, name -> name to idx }.toMap()
        } else {
            mapOf(
                "matricule" to 0,
                "nom" to 1,
                "prenom" to 2,
                "date_naissance" to 3,
                "sexe" to 4,
                "classe" to 5,
                "annee_scolaire" to 6,
                "responsable_nom" to 7,
                "responsable_tel" to 8,
                "responsable_lien" to 9,
                "historique" to 10
            )
        }

        val dataLines = if (hasHeader) lines.drop(1) else lines
        return dataLines.mapNotNull { line ->
            val fields = splitCsvLine(line)
            fun col(vararg keys: String, fallbackIdx: Int): String {
                for (k in keys) {
                    val idx = colMap[k]
                    if (idx != null && idx < fields.size) return fields[idx].trim()
                }
                return if (!hasHeader && fallbackIdx < fields.size) fields[fallbackIdx].trim() else ""
            }

            val matricule = col("matricule", fallbackIdx = 0)
            val lastName = col("nom", "lastname", "last_name", fallbackIdx = 1)
            val firstName = col("prenom", "firstname", "first_name", fallbackIdx = 2)
            if (matricule.isBlank() || lastName.isBlank() || firstName.isBlank()) return@mapNotNull null

            val rawGender = col("sexe", "gender", fallbackIdx = 4).uppercase()
            val gender = if (rawGender.startsWith("F")) "F" else "M"

            CsvStudentRow(
                matricule = matricule,
                firstName = firstName,
                lastName = lastName,
                dateOfBirth = col("date_naissance", "dateofbirth", "dob", fallbackIdx = 3),
                gender = gender,
                className = col("classe", "class", "classname", fallbackIdx = 5),
                academicYear = col("annee_scolaire", "academicyear", fallbackIdx = 6).ifBlank { "2026-2027" },
                legalGuardianName = col("responsable_nom", "tuteur", "guardian_name", fallbackIdx = 7),
                legalGuardianPhone = col("responsable_tel", "telephone_responsable", "guardian_phone", fallbackIdx = 8),
                legalGuardianRelation = col("responsable_lien", "lien_parente", "guardian_relation", fallbackIdx = 9),
                academicHistory = col("historique", "academic_history", fallbackIdx = 10)
            )
        }
    }

    fun exportNationalReportCsv(
        schools: List<School>,
        classes: List<SchoolClass>,
        students: List<Student>,
        grades: List<AssessmentGrade>,
        attendance: List<AttendanceRecord>
    ): String {
        val sb = StringBuilder()
        sb.appendLine(
            listOf(
                "code_etablissement",
                "nom_etablissement",
                "type",
                "region",
                "directeur",
                "classes",
                "eleves",
                "moyenne_generale",
                "absences",
                "retards"
            ).joinToString(",")
        )
        schools.sortedBy { it.name }.forEach { school ->
            val schoolClasses = classes.count { it.schoolId == school.id }
            val schoolStudents = students.count { it.schoolId == school.id }
            val schoolGrades = grades.filter { it.schoolId == school.id && it.maxScore > 0 }
            val avg = if (schoolGrades.isEmpty()) {
                ""
            } else {
                val totalWeighted = schoolGrades.sumOf { (it.score / it.maxScore) * 20.0 * it.coefficient }
                val totalCoeff = schoolGrades.sumOf { it.coefficient }
                if (totalCoeff > 0) String.format(java.util.Locale.US, "%.2f", totalWeighted / totalCoeff) else ""
            }
            val absences = attendance.count { it.schoolId == school.id && it.status == "absent" }
            val tardies = attendance.count { it.schoolId == school.id && it.status == "retard" }

            sb.appendLine(
                listOf(
                    school.code,
                    school.name,
                    school.schoolType,
                    school.regionName,
                    school.directorName,
                    schoolClasses.toString(),
                    schoolStudents.toString(),
                    avg,
                    absences.toString(),
                    tardies.toString()
                ).joinToString(",") { escapeCsv(it) }
            )
        }
        return sb.toString()
    }

    internal fun splitCsvLine(line: String): List<String> {
        val result = mutableListOf<String>()
        val current = StringBuilder()
        var inQuotes = false
        var i = 0
        val delimiter = if (!line.contains(',') && line.contains(';')) ';' else ','
        while (i < line.length) {
            val ch = line[i]
            when {
                ch == '"' -> {
                    if (inQuotes && i + 1 < line.length && line[i + 1] == '"') {
                        current.append('"')
                        i++
                    } else {
                        inQuotes = !inQuotes
                    }
                }
                ch == delimiter && !inQuotes -> {
                    result.add(current.toString())
                    current.clear()
                }
                else -> current.append(ch)
            }
            i++
        }
        result.add(current.toString())
        return result
    }

    internal fun escapeCsv(value: String): String {
        val clean = value.replace("\r", " ").replace("\n", " ")
        return if (clean.contains(',') || clean.contains('"') || clean.contains(';')) {
            "\"${clean.replace("\"", "\"\"")}\""
        } else {
            clean
        }
    }
}
