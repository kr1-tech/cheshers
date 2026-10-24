package app.cheshers.ui

import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import java.io.File

fun sharePgn(context: Context, pgn: String) {
    val directory = File(context.cacheDir, "pgn").apply { mkdirs() }
    val file = File(directory, "cheshers-${System.currentTimeMillis()}.pgn")
    file.writeText(pgn)
    val uri = FileProvider.getUriForFile(context, "${context.packageName}.files", file)
    val intent = Intent(Intent.ACTION_SEND).apply {
        type = "application/x-chess-pgn"
        putExtra(Intent.EXTRA_STREAM, uri)
        putExtra(Intent.EXTRA_SUBJECT, "Cheshers game PGN")
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    context.startActivity(Intent.createChooser(intent, "Export PGN"))
}
