package com.example.util

import android.content.Context
import android.content.Intent
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import androidx.core.content.FileProvider
import androidx.core.graphics.withSave
import com.example.R
import com.example.data.model.AssessmentGrade
import com.example.data.model.ReportCard
import com.example.data.model.Subject
import java.io.File
import java.io.FileOutputStream
import java.util.Locale

object PdfBulletinGenerator {

    fun generateAndOpenPdf(
        context: Context,
        reportCard: ReportCard,
        studentGrades: List<AssessmentGrade>,
        subjects: List<Subject>
    ): Result<File> = runCatching {
        val pdfDocument = PdfDocument()
        val pageInfo = PdfDocument.PageInfo.Builder(595, 842, 1).create()
        val page = pdfDocument.startPage(pageInfo)
        val canvas = page.canvas

        val headerBg = Paint().apply { color = Color.rgb(11, 74, 125) }
        val accentBar = Paint().apply { color = Color.rgb(13, 148, 136) }
        val lightBox = Paint().apply { color = Color.rgb(240, 245, 250) }
        val borderPaint = Paint().apply {
            color = Color.rgb(200, 214, 229)
            style = Paint.Style.STROKE
            strokeWidth = 1f
        }
        val titleWhite = Paint().apply {
            color = Color.WHITE
            textSize = 18f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            isAntiAlias = true
        }
        val subtitleWhite = Paint().apply {
            color = Color.rgb(204, 251, 241)
            textSize = 11f
            isAntiAlias = true
        }
        val sectionTitle = Paint().apply {
            color = Color.rgb(11, 74, 125)
            textSize = 13f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            isAntiAlias = true
        }
        val bodyBold = Paint().apply {
            color = Color.rgb(15, 33, 55)
            textSize = 11f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            isAntiAlias = true
        }
        val bodyText = Paint().apply {
            color = Color.rgb(40, 55, 75)
            textSize = 10.5f
            isAntiAlias = true
        }

        // Header banner
        canvas.drawRect(0f, 0f, 595f, 96f, headerBg)
        canvas.drawRect(0f, 96f, 595f, 102f, accentBar)
        canvas.drawText("ÉCOLE CONNECTÉE — BULLETIN SCOLAIRE", 36f, 42f, titleWhite)
        canvas.drawText(
            "${reportCard.schoolName.ifBlank { "Établissement scolaire" }}  •  ${reportCard.period}",
            36f,
            66f,
            subtitleWhite
        )
        runCatching {
            BitmapFactory.decodeResource(context.resources, R.drawable.img_ecole_embleme)
        }.getOrNull()?.let { logo ->
            val cx = 532f
            val cy = 48f
            val radius = 32f
            val whiteCircle = Paint().apply {
                color = Color.WHITE
                isAntiAlias = true
            }
            canvas.withSave {
                drawCircle(cx, cy, radius + 2f, whiteCircle)
                val clipPath = Path().apply {
                    addCircle(cx, cy, radius, Path.Direction.CW)
                }
                clipPath(clipPath)
                val destRect = RectF(cx - radius, cy - radius, cx + radius, cy + radius)
                drawBitmap(logo, null, destRect, Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG))
            }
            logo.recycle()
        }

        // Student info box
        canvas.drawRect(36f, 120f, 559f, 192f, lightBox)
        canvas.drawRect(36f, 120f, 559f, 192f, borderPaint)
        canvas.drawText("Élève : ${reportCard.studentName}", 48f, 144f, bodyBold)
        canvas.drawText("Classe : ${reportCard.className}", 48f, 164f, bodyText)
        canvas.drawText(
            "Absences : ${reportCard.absencesCount}   |   Retards : ${reportCard.tardiesCount}",
            48f,
            182f,
            bodyText
        )
        canvas.drawText(
            String.format(Locale.FRANCE, "Moyenne générale : %.2f / 20", reportCard.overallAverage),
            350f,
            144f,
            sectionTitle
        )
        canvas.drawText(
            String.format(Locale.FRANCE, "Moyenne classe : %.2f / 20", reportCard.classAverage),
            350f,
            164f,
            bodyText
        )
        if (reportCard.rankLabel.isNotBlank()) {
            canvas.drawText("Rang : ${reportCard.rankLabel}", 350f, 182f, bodyText)
        }

        // Table header
        var y = 224f
        canvas.drawText("DÉTAIL DES RÉSULTATS PAR MATIÈRE", 36f, y, sectionTitle)
        y += 14f
        canvas.drawRect(36f, y, 559f, y + 24f, headerBg)
        val tableHeaderPaint = Paint(bodyBold).apply { color = Color.WHITE }
        canvas.drawText("Matière", 44f, y + 16f, tableHeaderPaint)
        canvas.drawText("Coeff.", 270f, y + 16f, tableHeaderPaint)
        canvas.drawText("Éval.", 340f, y + 16f, tableHeaderPaint)
        canvas.drawText("Moyenne /20", 420f, y + 16f, tableHeaderPaint)
        y += 24f

        val summaries = GradeCalculator.computeSubjectAverages(studentGrades, subjects, reportCard.period)
        if (summaries.isEmpty()) {
            canvas.drawRect(36f, y, 559f, y + 28f, borderPaint)
            canvas.drawText("Aucune note détaillée enregistrée pour cette période.", 44f, y + 18f, bodyText)
            y += 28f
        } else {
            summaries.take(14).forEachIndexed { idx, item ->
                if (idx % 2 == 1) {
                    canvas.drawRect(36f, y, 559f, y + 24f, lightBox)
                }
                canvas.drawRect(36f, y, 559f, y + 24f, borderPaint)
                canvas.drawText(item.subjectName.take(30), 44f, y + 16f, bodyText)
                canvas.drawText(String.format(Locale.FRANCE, "%.1f", item.coefficient), 275f, y + 16f, bodyText)
                canvas.drawText("${item.gradesCount}", 350f, y + 16f, bodyText)
                canvas.drawText(String.format(Locale.FRANCE, "%.2f", item.averageOn20), 432f, y + 16f, bodyBold)
                y += 24f
            }
        }

        // Council decision & observation
        y += 24f
        canvas.drawText("APPRÉCIATION ET DÉCISION DU CONSEIL", 36f, y, sectionTitle)
        y += 12f
        canvas.drawRect(36f, y, 559f, y + 84f, lightBox)
        canvas.drawRect(36f, y, 559f, y + 84f, borderPaint)
        canvas.drawText(
            "Décision : ${reportCard.councilDecision.ifBlank { "Non renseignée" }}",
            48f,
            y + 22f,
            bodyBold
        )
        val obs = reportCard.directorObservation.ifBlank { "Aucune observation complémentaire." }
        val line1 = obs.take(78)
        val line2 = if (obs.length > 78) obs.drop(78).take(78) else ""
        canvas.drawText("Observation : $line1", 48f, y + 44f, bodyText)
        if (line2.isNotBlank()) {
            canvas.drawText(line2, 48f, y + 62f, bodyText)
        }

        canvas.drawText(
            "Document généré par la plateforme nationale ÉCOLE CONNECTÉE — ${DateUtils.formatFrenchShort(DateUtils.todayIso())}",
            36f,
            808f,
            Paint(bodyText).apply { color = Color.GRAY; textSize = 9f }
        )

        pdfDocument.finishPage(page)

        val dir = File(context.cacheDir, "bulletins").apply { mkdirs() }
        val safeName = reportCard.studentName.replace(Regex("[^a-zA-Z0-9_-]"), "_")
        val file = File(dir, "bulletin_${safeName}_${reportCard.period.replace(" ", "_")}.pdf")
        FileOutputStream(file).use { out ->
            pdfDocument.writeTo(out)
        }
        pdfDocument.close()

        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        val viewIntent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "application/pdf")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        val shareIntent = Intent(Intent.ACTION_SEND).apply {
            type = "application/pdf"
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        try {
            context.startActivity(viewIntent)
        } catch (_: Exception) {
            val chooser = Intent.createChooser(shareIntent, "Ouvrir ou partager le bulletin PDF").apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(chooser)
        }
        file
    }
}
