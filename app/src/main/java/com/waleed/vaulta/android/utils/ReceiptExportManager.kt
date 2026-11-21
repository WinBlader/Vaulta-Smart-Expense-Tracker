package com.waleed.vaulta.android.utils

import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.core.content.FileProvider
import com.waleed.vaulta.android.data.model.ExpenseEntity
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object ReceiptExportManager {

    private fun getExportsDir(context: Context): File {
        val dir = File(context.cacheDir, "exports")
        if (!dir.exists()) {
            dir.mkdirs()
        }
        return dir
    }

    /**
     * Opens a PDF document URI with any installed PDF viewer app.
     */
    fun openPdf(context: Context, uri: Uri) {
        try {
            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, "application/pdf")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(Intent.createChooser(intent, "Open PDF"))
        } catch (_: Exception) {
            VaultaToast.show(context, "No PDF viewer app found on device.")
        }
    }

    /**
     * Saves a generated PDF document directly into the device's public Downloads directory
     * under Environment.DIRECTORY_DOWNLOADS/Vaulta using MediaStore on Android 10+ (API 29+).
     */
    fun savePdfToPublicDownloads(context: Context, pdfDocument: PdfDocument, baseFileName: String): Uri? {
        val fileName = if (baseFileName.endsWith(".pdf", ignoreCase = true)) baseFileName else "$baseFileName.pdf"
        var savedUri: Uri? = null

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val contentValues = ContentValues().apply {
                    put(MediaStore.MediaColumns.DISPLAY_NAME, fileName)
                    put(MediaStore.MediaColumns.MIME_TYPE, "application/pdf")
                    put(MediaStore.MediaColumns.RELATIVE_PATH, "${Environment.DIRECTORY_DOWNLOADS}/Vaulta")
                }
                val resolver = context.contentResolver
                val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, contentValues)
                if (uri != null) {
                    resolver.openOutputStream(uri)?.use { out ->
                        pdfDocument.writeTo(out)
                    }
                    savedUri = uri
                }
            } else {
                val publicDownloads = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
                val vaultaFolder = File(publicDownloads, "Vaulta")
                if (!vaultaFolder.exists()) {
                    vaultaFolder.mkdirs()
                }
                val targetFile = File(vaultaFolder, fileName)
                FileOutputStream(targetFile).use { out ->
                    pdfDocument.writeTo(out)
                }
                savedUri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", targetFile)
            }
        } catch (e: Exception) {
            e.printStackTrace()
            try {
                val fallbackFile = File(getExportsDir(context), fileName)
                FileOutputStream(fallbackFile).use { out ->
                    pdfDocument.writeTo(out)
                }
                savedUri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", fallbackFile)
            } catch (_: Exception) {}
        }

        return savedUri
    }

    /**
     * Renders a single transaction receipt into a branded Bitmap.
     */
    fun createReceiptBitmap(context: Context, item: ExpenseEntity): Bitmap {
        val width = 800
        val height = 1060
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)

        // Background
        canvas.drawColor(Color.parseColor("#0F1716"))

        val paint = Paint(Paint.ANTI_ALIAS_FLAG)

        // Header Background Banner
        paint.color = Color.parseColor("#004D40")
        canvas.drawRect(0f, 0f, width.toFloat(), 150f, paint)

        // Brand Title
        paint.color = Color.WHITE
        paint.textSize = 34f
        paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        paint.textAlign = Paint.Align.CENTER
        canvas.drawText("VAULTA", (width / 2).toFloat(), 65f, paint)

        // Brand Subtitle
        paint.color = Color.parseColor("#80CBC4")
        paint.textSize = 15f
        paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
        canvas.drawText("SMART EXPENSE TRACKER & FINANCE MANAGER", (width / 2).toFloat(), 98f, paint)

        // Card Container
        paint.color = Color.parseColor("#182523")
        val cardRect = RectF(40f, 180f, (width - 40).toFloat(), (height - 80).toFloat())
        canvas.drawRoundRect(cardRect, 24f, 24f, paint)

        // Card Border
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 2.5f
        paint.color = Color.parseColor("#263D39")
        canvas.drawRoundRect(cardRect, 24f, 24f, paint)
        paint.style = Paint.Style.FILL

        // Receipt Header Label
        paint.color = Color.parseColor("#9E9E9E")
        paint.textSize = 14f
        paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        paint.letterSpacing = 0.08f
        canvas.drawText("OFFICIAL TRANSACTION RECEIPT", (width / 2).toFloat(), 230f, paint)
        paint.letterSpacing = 0f

        // Transaction Amount
        val isIncome = item.type.equals("Income", ignoreCase = true)
        val amountSign = if (isIncome) "+" else "-"
        val formattedAmount = "$amountSign${Utils.formatCurrency(item.amount)}"
        paint.textSize = 52f
        paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        paint.color = if (isIncome) Color.parseColor("#00E676") else Color.parseColor("#FF5252")
        canvas.drawText(formattedAmount, (width / 2).toFloat(), 305f, paint)

        // Type Badge
        val badgeText = if (isIncome) "INCOME / CREDIT" else "EXPENSE / DEBIT"
        val badgeBg = if (isIncome) Color.parseColor("#1B5E20") else Color.parseColor("#B71C1C")
        paint.color = badgeBg
        val badgeRect = RectF((width / 2 - 110).toFloat(), 330f, (width / 2 + 110).toFloat(), 368f)
        canvas.drawRoundRect(badgeRect, 18f, 18f, paint)
        paint.color = Color.WHITE
        paint.textSize = 14f
        paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        canvas.drawText(badgeText, (width / 2).toFloat(), 355f, paint)

        // Divider
        paint.color = Color.parseColor("#263D39")
        paint.strokeWidth = 1.5f
        canvas.drawLine(70f, 395f, (width - 70).toFloat(), 395f, paint)

        // Itemized Rows
        var currentY = 445f
        val lineSpacing = 68f
        val leftX = 70f
        val rightX = (width - 70).toFloat()

        fun drawRow(label: String, value: String) {
            paint.textAlign = Paint.Align.LEFT
            paint.color = Color.parseColor("#A0B2AF")
            paint.textSize = 17f
            paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
            canvas.drawText(label, leftX, currentY, paint)

            paint.textAlign = Paint.Align.RIGHT
            paint.color = Color.WHITE
            paint.textSize = 18f
            paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            canvas.drawText(value, rightX, currentY, paint)

            paint.color = Color.parseColor("#20332F")
            paint.strokeWidth = 1f
            canvas.drawLine(leftX, currentY + 22f, rightX, currentY + 22f, paint)

            currentY += lineSpacing
        }

        drawRow("Title / Category", item.title)
        drawRow("Transaction Type", item.type)
        drawRow("Transaction Date", Utils.formatStringDateToMonthDayYear(item.date))
        drawRow("Reference ID", "VLT-${item.id ?: (System.currentTimeMillis() % 100000)}")
        drawRow("Payment Status", "Completed • Verified")
        drawRow("Generated On", SimpleDateFormat("MMM dd, yyyy • HH:mm", Locale.getDefault()).format(Date()))

        // Security Footnote
        currentY += 25f
        paint.textAlign = Paint.Align.CENTER
        paint.color = Color.parseColor("#80CBC4")
        paint.textSize = 13f
        paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
        canvas.drawText("🔒 Verified offline record stored in Vaulta SQLite Database", (width / 2).toFloat(), currentY, paint)

        // Footer at bottom
        paint.color = Color.parseColor("#617370")
        paint.textSize = 13f
        canvas.drawText("from winblader.org • Vaulta Smart Expense Tracker", (width / 2).toFloat(), (height - 35).toFloat(), paint)

        return bitmap
    }

    /**
     * Shares a transaction receipt as a PNG image via Android share sheet.
     */
    fun shareTransactionReceiptAsImage(context: Context, item: ExpenseEntity) {
        val bitmap = createReceiptBitmap(context, item)
        val file = File(getExportsDir(context), "vaulta_receipt_${item.id ?: System.currentTimeMillis()}.png")

        FileOutputStream(file).use { out ->
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
        }

        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "image/png"
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_SUBJECT, "Vaulta Receipt - ${item.title}")
            putExtra(Intent.EXTRA_TEXT, "Transaction Receipt for ${item.title}: ${Utils.formatCurrency(item.amount)} (${item.date})")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }

        val chooser = Intent.createChooser(intent, "Share Transaction Receipt")
        chooser.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(chooser)
    }

    /**
     * Renders a single transaction receipt into an A4 PdfDocument.
     */
    fun createReceiptPdfDocument(item: ExpenseEntity): PdfDocument {
        val pdfDocument = PdfDocument()
        val pageInfo = PdfDocument.PageInfo.Builder(595, 842, 1).create() // A4
        val page = pdfDocument.startPage(pageInfo)
        val canvas = page.canvas

        // Background
        canvas.drawColor(Color.parseColor("#0F1716"))

        val paint = Paint(Paint.ANTI_ALIAS_FLAG)

        // Top Header
        paint.color = Color.parseColor("#004D40")
        canvas.drawRect(0f, 0f, 595f, 110f, paint)

        paint.color = Color.WHITE
        paint.textSize = 28f
        paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        paint.textAlign = Paint.Align.CENTER
        canvas.drawText("VAULTA", 297.5f, 50f, paint)

        paint.color = Color.parseColor("#80CBC4")
        paint.textSize = 12f
        paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
        canvas.drawText("SMART EXPENSE TRACKER & FINANCE MANAGER", 297.5f, 75f, paint)

        // Card Container
        paint.color = Color.parseColor("#182523")
        val cardRect = RectF(35f, 140f, 560f, 740f)
        canvas.drawRoundRect(cardRect, 18f, 18f, paint)

        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 1.5f
        paint.color = Color.parseColor("#263D39")
        canvas.drawRoundRect(cardRect, 18f, 18f, paint)
        paint.style = Paint.Style.FILL

        // Title
        paint.color = Color.parseColor("#9E9E9E")
        paint.textSize = 12f
        paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        canvas.drawText("OFFICIAL TRANSACTION RECEIPT", 297.5f, 180f, paint)

        // Amount
        val isIncome = item.type.equals("Income", ignoreCase = true)
        val amountSign = if (isIncome) "+" else "-"
        val formattedAmount = "$amountSign${Utils.formatCurrency(item.amount)}"
        paint.textSize = 38f
        paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        paint.color = if (isIncome) Color.parseColor("#00E676") else Color.parseColor("#FF5252")
        canvas.drawText(formattedAmount, 297.5f, 235f, paint)

        // Badge
        val badgeText = if (isIncome) "INCOME / CREDIT" else "EXPENSE / DEBIT"
        val badgeBg = if (isIncome) Color.parseColor("#1B5E20") else Color.parseColor("#B71C1C")
        paint.color = badgeBg
        val badgeRect = RectF(297.5f - 85f, 255f, 297.5f + 85f, 285f)
        canvas.drawRoundRect(badgeRect, 14f, 14f, paint)
        paint.color = Color.WHITE
        paint.textSize = 11f
        paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        canvas.drawText(badgeText, 297.5f, 274f, paint)

        // Divider
        paint.color = Color.parseColor("#263D39")
        paint.strokeWidth = 1f
        canvas.drawLine(60f, 310f, 535f, 310f, paint)

        // Table Rows
        var currentY = 350f
        val lineSpacing = 52f
        val leftX = 65f
        val rightX = 530f

        fun drawPdfRow(label: String, value: String) {
            paint.textAlign = Paint.Align.LEFT
            paint.color = Color.parseColor("#A0B2AF")
            paint.textSize = 14f
            paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
            canvas.drawText(label, leftX, currentY, paint)

            paint.textAlign = Paint.Align.RIGHT
            paint.color = Color.WHITE
            paint.textSize = 15f
            paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            canvas.drawText(value, rightX, currentY, paint)

            paint.color = Color.parseColor("#20332F")
            paint.strokeWidth = 0.8f
            canvas.drawLine(leftX, currentY + 16f, rightX, currentY + 16f, paint)

            currentY += lineSpacing
        }

        drawPdfRow("Title / Category", item.title)
        drawPdfRow("Transaction Type", item.type)
        drawPdfRow("Transaction Date", Utils.formatStringDateToMonthDayYear(item.date))
        drawPdfRow("Reference ID", "VLT-${item.id ?: (System.currentTimeMillis() % 100000)}")
        drawPdfRow("Payment Status", "Completed • Verified")
        drawPdfRow("Generated On", SimpleDateFormat("MMM dd, yyyy • HH:mm", Locale.getDefault()).format(Date()))

        // Footer in PDF
        paint.textAlign = Paint.Align.CENTER
        paint.color = Color.parseColor("#80CBC4")
        paint.textSize = 11f
        paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
        canvas.drawText("🔒 Verified offline record stored in Vaulta SQLite Database", 297.5f, 690f, paint)

        paint.color = Color.parseColor("#617370")
        paint.textSize = 11f
        canvas.drawText("from winblader.org • Vaulta Smart Expense Tracker", 297.5f, 790f, paint)

        pdfDocument.finishPage(page)
        return pdfDocument
    }

    /**
     * Downloads a single transaction receipt PDF directly into public Downloads/Vaulta.
     */
    fun downloadTransactionReceiptPdf(context: Context, item: ExpenseEntity): Uri? {
        val pdfDocument = createReceiptPdfDocument(item)
        val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
        val fileName = "Vaulta_Receipt_${item.id ?: timestamp}.pdf"
        val uri = savePdfToPublicDownloads(context, pdfDocument, fileName)
        pdfDocument.close()

        if (uri != null) {
            VaultaToast.showWithAction(
                message = "PDF saved to Downloads/Vaulta",
                actionLabel = "OPEN",
                onAction = { openPdf(context, uri) }
            )
        } else {
            VaultaToast.show(context, "Failed to download PDF receipt.")
        }
        return uri
    }

    /**
     * Shares a single transaction receipt as a high-quality PDF.
     */
    fun shareTransactionReceiptAsPdf(context: Context, item: ExpenseEntity) {
        val pdfDocument = createReceiptPdfDocument(item)
        val file = File(getExportsDir(context), "vaulta_receipt_${item.id ?: System.currentTimeMillis()}.pdf")
        FileOutputStream(file).use { out ->
            pdfDocument.writeTo(out)
        }
        pdfDocument.close()

        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "application/pdf"
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_SUBJECT, "Vaulta Receipt PDF - ${item.title}")
            putExtra(Intent.EXTRA_TEXT, "Transaction Receipt PDF for ${item.title}: ${Utils.formatCurrency(item.amount)}")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }

        val chooser = Intent.createChooser(intent, "Share Transaction Receipt PDF")
        chooser.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(chooser)
    }

    /**
     * Renders a multi-transaction statement into a multi-page A4 PdfDocument.
     */
    fun createTransactionsStatementPdfDocument(
        transactions: List<ExpenseEntity>,
        filterType: String,
        dateRange: String,
        totalIncome: Double,
        totalExpense: Double,
        netBalance: Double
    ): PdfDocument {
        val pdfDocument = PdfDocument()
        val itemsPerPage = 22
        val totalPages = if (transactions.isEmpty()) 1 else ((transactions.size - 1) / itemsPerPage) + 1

        for (pageIndex in 0 until totalPages) {
            val pageInfo = PdfDocument.PageInfo.Builder(595, 842, pageIndex + 1).create()
            val page = pdfDocument.startPage(pageInfo)
            val canvas = page.canvas
            val paint = Paint(Paint.ANTI_ALIAS_FLAG)

            // Background
            canvas.drawColor(Color.parseColor("#0F1716"))

            // Header Banner
            paint.color = Color.parseColor("#004D40")
            canvas.drawRect(0f, 0f, 595f, 90f, paint)

            paint.color = Color.WHITE
            paint.textSize = 24f
            paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            paint.textAlign = Paint.Align.LEFT
            canvas.drawText("VAULTA — FINANCIAL STATEMENT", 35f, 45f, paint)

            paint.color = Color.parseColor("#80CBC4")
            paint.textSize = 11f
            paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
            val genDate = SimpleDateFormat("MMM dd, yyyy • HH:mm", Locale.getDefault()).format(Date())
            canvas.drawText("Period: $dateRange | Filter: $filterType | Generated: $genDate", 35f, 68f, paint)

            var startY = 110f

            // On Page 1, draw the Summary KPI Cards
            if (pageIndex == 0) {
                val boxWidth = 160f
                val boxHeight = 60f

                // Income Card
                paint.color = Color.parseColor("#182523")
                val incomeRect = RectF(35f, startY, 35f + boxWidth, startY + boxHeight)
                canvas.drawRoundRect(incomeRect, 10f, 10f, paint)
                paint.color = Color.parseColor("#A0B2AF")
                paint.textSize = 10f
                paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
                canvas.drawText("TOTAL INCOME", 45f, startY + 22f, paint)
                paint.color = Color.parseColor("#00E676")
                paint.textSize = 16f
                canvas.drawText("+${Utils.formatCurrency(totalIncome)}", 45f, startY + 48f, paint)

                // Expense Card
                val expX = 35f + boxWidth + 20f
                paint.color = Color.parseColor("#182523")
                val expRect = RectF(expX, startY, expX + boxWidth, startY + boxHeight)
                canvas.drawRoundRect(expRect, 10f, 10f, paint)
                paint.color = Color.parseColor("#A0B2AF")
                paint.textSize = 10f
                paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
                canvas.drawText("TOTAL EXPENSE", expX + 10f, startY + 22f, paint)
                paint.color = Color.parseColor("#FF5252")
                paint.textSize = 16f
                canvas.drawText("-${Utils.formatCurrency(totalExpense)}", expX + 10f, startY + 48f, paint)

                // Net Card
                val netX = expX + boxWidth + 20f
                paint.color = Color.parseColor("#182523")
                val netRect = RectF(netX, startY, netX + boxWidth, startY + boxHeight)
                canvas.drawRoundRect(netRect, 10f, 10f, paint)
                paint.color = Color.parseColor("#A0B2AF")
                paint.textSize = 10f
                paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
                canvas.drawText("NET BALANCE", netX + 10f, startY + 22f, paint)
                paint.color = if (netBalance >= 0) Color.parseColor("#80CBC4") else Color.parseColor("#FF5252")
                paint.textSize = 16f
                canvas.drawText(Utils.formatCurrency(netBalance), netX + 10f, startY + 48f, paint)

                startY += 85f
            }

            // Table Header Bar
            paint.color = Color.parseColor("#182523")
            canvas.drawRect(35f, startY, 560f, startY + 26f, paint)

            paint.color = Color.parseColor("#80CBC4")
            paint.textSize = 11f
            paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            paint.textAlign = Paint.Align.LEFT

            canvas.drawText("DATE", 45f, startY + 17f, paint)
            canvas.drawText("TITLE / CATEGORY", 135f, startY + 17f, paint)
            canvas.drawText("TYPE", 390f, startY + 17f, paint)
            paint.textAlign = Paint.Align.RIGHT
            canvas.drawText("AMOUNT", 550f, startY + 17f, paint)

            startY += 36f

            // Table Rows for this page
            val startIndex = pageIndex * itemsPerPage
            val endIndex = (startIndex + itemsPerPage).coerceAtMost(transactions.size)
            val pageItems = if (transactions.isNotEmpty()) transactions.subList(startIndex, endIndex) else emptyList()

            for (item in pageItems) {
                val isIncome = item.type.equals("Income", ignoreCase = true)

                // Row bottom separator line
                paint.color = Color.parseColor("#1C2C28")
                paint.strokeWidth = 0.8f
                canvas.drawLine(35f, startY + 14f, 560f, startY + 14f, paint)

                // Row values
                paint.textAlign = Paint.Align.LEFT
                paint.color = Color.parseColor("#B0BEC5")
                paint.textSize = 10.5f
                paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
                canvas.drawText(item.date, 45f, startY + 8f, paint)

                paint.color = Color.WHITE
                paint.textSize = 11f
                paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
                val truncatedTitle = if (item.title.length > 36) item.title.take(33) + "..." else item.title
                canvas.drawText(truncatedTitle, 135f, startY + 8f, paint)

                paint.color = if (isIncome) Color.parseColor("#00E676") else Color.parseColor("#FF8A80")
                paint.textSize = 10f
                paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
                canvas.drawText(item.type, 390f, startY + 8f, paint)

                paint.textAlign = Paint.Align.RIGHT
                paint.textSize = 11.5f
                val sign = if (isIncome) "+" else "-"
                canvas.drawText("$sign${Utils.formatCurrency(item.amount)}", 550f, startY + 8f, paint)

                startY += 26f
            }

            // Footer
            paint.textAlign = Paint.Align.LEFT
            paint.color = Color.parseColor("#617370")
            paint.textSize = 10f
            paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
            canvas.drawText("Vaulta Smart Expense Tracker • winblader.org", 35f, 815f, paint)

            paint.textAlign = Paint.Align.RIGHT
            canvas.drawText("Page ${pageIndex + 1} of $totalPages", 560f, 815f, paint)

            pdfDocument.finishPage(page)
        }
        return pdfDocument
    }

    /**
     * Downloads a multi-transaction statement PDF directly into public Downloads/Vaulta.
     */
    fun downloadTransactionsStatementPdf(
        context: Context,
        transactions: List<ExpenseEntity>,
        filterType: String,
        dateRange: String,
        totalIncome: Double,
        totalExpense: Double,
        netBalance: Double
    ): Uri? {
        val pdfDocument = createTransactionsStatementPdfDocument(
            transactions, filterType, dateRange, totalIncome, totalExpense, netBalance
        )
        val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
        val fileName = "Vaulta_Statement_$timestamp.pdf"
        val uri = savePdfToPublicDownloads(context, pdfDocument, fileName)
        pdfDocument.close()

        if (uri != null) {
            VaultaToast.showWithAction(
                message = "PDF saved to Downloads/Vaulta",
                actionLabel = "OPEN",
                onAction = { openPdf(context, uri) }
            )
        } else {
            VaultaToast.show(context, "Failed to download Statement PDF.")
        }
        return uri
    }

    /**
     * Exports a multi-transaction statement PDF for active filtered transactions via share sheet.
     */
    fun exportTransactionsStatementPdf(
        context: Context,
        transactions: List<ExpenseEntity>,
        filterType: String,
        dateRange: String,
        totalIncome: Double,
        totalExpense: Double,
        netBalance: Double
    ) {
        val pdfDocument = createTransactionsStatementPdfDocument(
            transactions, filterType, dateRange, totalIncome, totalExpense, netBalance
        )
        val file = File(getExportsDir(context), "vaulta_statement_${System.currentTimeMillis()}.pdf")
        FileOutputStream(file).use { out ->
            pdfDocument.writeTo(out)
        }
        pdfDocument.close()

        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "application/pdf"
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_SUBJECT, "Vaulta Statement ($dateRange)")
            putExtra(Intent.EXTRA_TEXT, "Financial Statement from Vaulta: $dateRange • Total Net: ${Utils.formatCurrency(netBalance)}")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }

        val chooser = Intent.createChooser(intent, "Share Financial Statement PDF")
        chooser.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(chooser)
    }
}
