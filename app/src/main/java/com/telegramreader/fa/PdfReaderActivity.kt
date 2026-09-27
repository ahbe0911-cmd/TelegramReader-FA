package com.telegramreader.fa

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.ext.SdkExtensions
import android.view.Gravity
import android.view.View
import android.widget.Button
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.FileProvider
import androidx.core.view.setPadding
import androidx.fragment.app.commitNow
import androidx.lifecycle.lifecycleScope
import androidx.pdf.viewer.fragment.PdfViewerFragment
import com.github.barteksc.pdfviewer.PDFView
import com.telegramreader.fa.data.TelegramRepository
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class PdfReaderActivity : AppCompatActivity() {
    private lateinit var repository: TelegramRepository
    private lateinit var container: FrameLayout
    private lateinit var progress: ProgressBar
    private lateinit var status: TextView
    private var sourceUrl: String = ""
    private var documentTitle: String = "document.pdf"

    private val savePdf = registerForActivityResult(
        ActivityResultContracts.CreateDocument("application/pdf"),
    ) { uri: Uri? ->
        if (uri == null) return@registerForActivityResult
        lifecycleScope.launch {
            setStatus("در حال ذخیره PDF…", true)
            runCatching {
                withContext(Dispatchers.IO) {
                    repository.downloadFileToUri(
                        url = sourceUrl,
                        title = documentTitle,
                        destination = uri,
                    )
                }
            }.onSuccess {
                Toast.makeText(
                    this@PdfReaderActivity,
                    "PDF ذخیره شد",
                    Toast.LENGTH_SHORT,
                ).show()
            }.onFailure {
                Toast.makeText(
                    this@PdfReaderActivity,
                    it.message ?: "ذخیره PDF ناموفق بود",
                    Toast.LENGTH_LONG,
                ).show()
            }
            setStatus("", false)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        repository = TelegramRepository(applicationContext)
        sourceUrl = intent.getStringExtra(EXTRA_URL).orEmpty()
        documentTitle = intent.getStringExtra(EXTRA_TITLE)
            ?.takeIf { it.isNotBlank() }
            ?: "document.pdf"

        window.decorView.layoutDirection = View.LAYOUT_DIRECTION_RTL
        setContentView(buildContentView())

        if (sourceUrl.isBlank()) {
            showFatalError("آدرس PDF موجود نیست.")
            return
        }

        loadPdf()
    }

    private fun buildContentView(): View {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutDirection = View.LAYOUT_DIRECTION_RTL
            setBackgroundColor(0xFFF6F7F9.toInt())
        }

        val toolbar = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(10))
            setBackgroundColor(0xFFFFFFFF.toInt())
        }

        val back = Button(this).apply {
            text = "بازگشت"
            isAllCaps = false
            setOnClickListener { finish() }
        }

        val title = TextView(this).apply {
            text = documentTitle
            textSize = 16f
            gravity = Gravity.CENTER_VERTICAL or Gravity.RIGHT
            maxLines = 2
            setTextColor(0xFF172033.toInt())
        }

        val download = Button(this).apply {
            text = "دانلود"
            isAllCaps = false
            setOnClickListener {
                val fileName = documentTitle.let {
                    if (it.lowercase().endsWith(".pdf")) it else "$it.pdf"
                }
                savePdf.launch(fileName)
            }
        }

        toolbar.addView(
            back,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT,
            ),
        )
        toolbar.addView(
            title,
            LinearLayout.LayoutParams(
                0,
                LinearLayout.LayoutParams.WRAP_CONTENT,
                1f,
            ).apply {
                marginStart = dp(8)
                marginEnd = dp(8)
            },
        )
        toolbar.addView(
            download,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT,
            ),
        )

        val body = FrameLayout(this)
        container = FrameLayout(this).apply {
            id = View.generateViewId()
            setBackgroundColor(0xFFEFF1F5.toInt())
        }

        progress = ProgressBar(this).apply {
            isIndeterminate = true
        }

        status = TextView(this).apply {
            text = "در حال دریافت PDF…"
            textSize = 14f
            gravity = Gravity.CENTER
            setTextColor(0xFF5B6474.toInt())
        }

        body.addView(
            container,
            FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT,
            ),
        )
        body.addView(
            progress,
            FrameLayout.LayoutParams(
                dp(42),
                dp(42),
                Gravity.CENTER,
            ),
        )
        body.addView(
            status,
            FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.WRAP_CONTENT,
                Gravity.CENTER,
            ).apply {
                topMargin = dp(86)
                leftMargin = dp(24)
                rightMargin = dp(24)
            },
        )

        root.addView(
            toolbar,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT,
            ),
        )
        root.addView(
            body,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                0,
                1f,
            ),
        )

        return root
    }

    private fun loadPdf() {
        setStatus("در حال دریافت و آماده‌سازی PDF…", true)

        lifecycleScope.launch {
            val fileResult = runCatching {
                withContext(Dispatchers.IO) {
                    repository.downloadPdf(sourceUrl, documentTitle)
                }
            }

            fileResult.onSuccess { file ->
                setStatus("", false)
                if (supportsAndroidXViewer()) {
                    showWithAndroidX(file)
                } else {
                    showWithLegacyPdfium(file)
                }
            }.onFailure {
                showFatalError(
                    it.message ?: "PDF دریافت نشد.",
                )
            }
        }
    }

    private fun supportsAndroidXViewer(): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return false
        return runCatching {
            SdkExtensions.getExtensionVersion(Build.VERSION_CODES.S) >= 13
        }.getOrDefault(false)
    }

    private fun showWithAndroidX(file: File) {
        runCatching {
            val uri = FileProvider.getUriForFile(
                this,
                "$packageName.fileprovider",
                file,
            )

            val fragment = PdfViewerFragment()
            supportFragmentManager.commitNow {
                replace(container.id, fragment, PDF_FRAGMENT_TAG)
            }
            fragment.documentUri = uri
        }.onFailure {
            showWithLegacyPdfium(file)
        }
    }

    private fun showWithLegacyPdfium(file: File) {
        supportFragmentManager.findFragmentByTag(PDF_FRAGMENT_TAG)?.let { fragment ->
            supportFragmentManager.commitNow {
                remove(fragment)
            }
        }

        container.removeAllViews()

        val pdfView = PDFView(this, null)
        container.addView(
            pdfView,
            FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT,
            ),
        )

        pdfView.fromFile(file)
            .defaultPage(0)
            .enableSwipe(true)
            .swipeHorizontal(false)
            .enableDoubletap(true)
            .spacing(dp(6))
            .onError { error ->
                showFatalError(
                    error.message ?: "نمایش PDF روی این دستگاه ممکن نشد.",
                )
            }
            .load()
    }

    private fun showFatalError(message: String) {
        container.removeAllViews()
        setStatus(message, false)

        val retry = Button(this).apply {
            text = "تلاش دوباره"
            isAllCaps = false
            setOnClickListener { loadPdf() }
        }

        container.addView(
            retry,
            FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.WRAP_CONTENT,
                FrameLayout.LayoutParams.WRAP_CONTENT,
                Gravity.CENTER,
            ).apply {
                topMargin = dp(120)
            },
        )
    }

    private fun setStatus(message: String, loading: Boolean) {
        status.text = message
        status.visibility = if (message.isBlank()) View.GONE else View.VISIBLE
        progress.visibility = if (loading) View.VISIBLE else View.GONE
    }

    private fun dp(value: Int): Int =
        (value * resources.displayMetrics.density).toInt()

    companion object {
        const val EXTRA_URL = "pdf_url"
        const val EXTRA_TITLE = "pdf_title"
        private const val PDF_FRAGMENT_TAG = "internal_pdf_viewer"

        fun intent(
            context: android.content.Context,
            url: String,
            title: String,
        ): Intent = Intent(context, PdfReaderActivity::class.java).apply {
            putExtra(EXTRA_URL, url)
            putExtra(EXTRA_TITLE, title)
        }
    }
}
