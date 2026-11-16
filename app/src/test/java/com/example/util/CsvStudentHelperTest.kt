package com.example.util

import com.example.data.model.AssessmentGrade
import com.example.data.model.AttendanceRecord
import com.example.data.model.School
import com.example.data.model.SchoolClass
import com.example.data.model.Student
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CsvStudentHelperTest {

    @Test
    fun exportAndParseStudents_roundTripsWithCommasAndExtendedFields() {
        val students = listOf(
            Student(
                id = "stu_1",
                firstName = "Amina",
                lastName = "Hassan",
                matricule = "DJ-2026-001",
                dateOfBirth = "2014-05-12",
                gender = "F",
                className = "6ème A",
                academicYear = "2026-2027",
                legalGuardianName = "Hassan Ali",
                legalGuardianPhone = "+25377112233",
                legalGuardianRelation = "Père",
                academicHistory = "École Balbala 1, CM2 (Admise)"
            ),
            Student(
                id = "stu_2",
                firstName = "Omar",
                lastName = "Farah",
                matricule = "DJ-2026-002",
                dateOfBirth = "2013-11-02",
                gender = "M",
                className = "6ème B",
                academicYear = "2026-2027",
                legalGuardianName = "Kadija Omar",
                legalGuardianPhone = "+25377445566",
                legalGuardianRelation = "Mère",
                academicHistory = "École PK12"
            )
        )

        val csv = CsvStudentHelper.exportStudentsToCsv(students)
        val parsed = CsvStudentHelper.parseStudentsFromCsv(csv)

        assertEquals(2, parsed.size)
        assertEquals("DJ-2026-001", parsed[0].matricule)
        assertEquals("Amina", parsed[0].firstName)
        assertEquals("Hassan", parsed[0].lastName)
        assertEquals("F", parsed[0].gender)
        assertEquals("6ème A", parsed[0].className)
        assertEquals("Hassan Ali", parsed[0].legalGuardianName)
        assertEquals("École Balbala 1, CM2 (Admise)", parsed[0].academicHistory)
        assertEquals("DJ-2026-002", parsed[1].matricule)
        assertEquals("M", parsed[1].gender)
    }

    @Test
    fun parseStudentsFromCsv_supportsSemicolonDelimiterAndUtf8Bom() {
        val rawCsv = "\uFEFFmatricule;nom;prenom;sexe;classe;responsable_nom;responsable_tel\n" +
            "MAT-901;Robleh;Fadumo;Femme;3ème C;Idriss Robleh;+25377001122\n" +
            ";MissingMatricule;Test;M;3ème C;Tutor;+25377000000\n"

        val parsed = CsvStudentHelper.parseStudentsFromCsv(rawCsv)

        assertEquals(1, parsed.size)
        assertEquals("MAT-901", parsed[0].matricule)
        assertEquals("Fadumo", parsed[0].firstName)
        assertEquals("F", parsed[0].gender)
        assertEquals("3ème C", parsed[0].className)
        assertEquals("2026-2027", parsed[0].academicYear)
    }

    @Test
    fun exportNationalReportCsv_aggregatesSchoolCountsAveragesAndAttendance() {
        val schools = listOf(
            School(
                id = "sch_1",
                code = "LED-01",
                name = "Lycée d'État de Djibouti",
                schoolType = "lycee",
                regionName = "Djibouti-Ville",
                directorName = "M. Ali"
            )
        )
        val classes = listOf(
            SchoolClass(id = "c1", schoolId = "sch_1", name = "Tle S1"),
            SchoolClass(id = "c2", schoolId = "sch_1", name = "Tle S2")
        )
        val students = listOf(
            Student(id = "s1", schoolId = "sch_1", firstName = "Ali", lastName = "Omar", matricule = "1")
        )
        val grades = listOf(
            AssessmentGrade(id = "g1", schoolId = "sch_1", score = 15.0, maxScore = 20.0, coefficient = 2.0),
            AssessmentGrade(id = "g2", schoolId = "sch_1", score = 12.0, maxScore = 20.0, coefficient = 1.0)
        )
        val attendance = listOf(
            AttendanceRecord(id = "a1", schoolId = "sch_1", status = "absent"),
            AttendanceRecord(id = "a2", schoolId = "sch_1", status = "retard")
        )

        val reportCsv = CsvStudentHelper.exportNationalReportCsv(schools, classes, students, grades, attendance)

        assertTrue(reportCsv.contains("code_etablissement,nom_etablissement,type,region,directeur,classes,eleves,moyenne_generale,absences,retards"))
        assertTrue(reportCsv.contains("LED-01,Lycée d'État de Djibouti,lycee,Djibouti-Ville,M. Ali,2,1,14.00,1,1"))
    }
}
