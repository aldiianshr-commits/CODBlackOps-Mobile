package com.codblackops.mobile.storage

import android.content.Context
import android.database.Cursor
import android.net.Uri
import android.provider.DocumentsContract
import android.provider.DocumentsContract.Document
import com.codblackops.mobile.core.disc.ByteSource
import java.io.FileInputStream
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.nio.ByteBuffer
import java.nio.channels.FileChannel

/** Minimal Storage Access Framework helper over a persisted document tree. */
class SafTree(private val ctx: Context, val treeUri: Uri) {

    data class Doc(
        val uri: Uri,
        val documentId: String,
        val name: String,
        val mime: String,
        val size: Long,
        val lastModified: Long,
    ) {
        val isDirectory: Boolean get() = mime == Document.MIME_TYPE_DIR
    }

    private val resolver get() = ctx.contentResolver

    val rootId: String get() = DocumentsContract.getTreeDocumentId(treeUri)

    fun docUri(documentId: String): Uri = DocumentsContract.buildDocumentUriUsingTree(treeUri, documentId)

    fun list(parentId: String = rootId): List<Doc> {
        val childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(treeUri, parentId)
        val out = ArrayList<Doc>()
        var c: Cursor? = null
        try {
            c = resolver.query(
                childrenUri,
                arrayOf(
                    Document.COLUMN_DOCUMENT_ID,
                    Document.COLUMN_DISPLAY_NAME,
                    Document.COLUMN_MIME_TYPE,
                    Document.COLUMN_SIZE,
                    Document.COLUMN_LAST_MODIFIED,
                ),
                null, null, null,
            )
            if (c != null) {
                while (c.moveToNext()) {
                    val id = c.getString(0) ?: continue
                    out.add(
                        Doc(
                            uri = docUri(id),
                            documentId = id,
                            name = c.getString(1) ?: "",
                            mime = c.getString(2) ?: "",
                            size = if (c.isNull(3)) -1 else c.getLong(3),
                            lastModified = if (c.isNull(4)) 0 else c.getLong(4),
                        ),
                    )
                }
            }
        } catch (_: Exception) {
            // provider errors -> empty listing
        } finally {
            c?.close()
        }
        return out
    }

    fun findChild(parentId: String, name: String): Doc? = list(parentId).firstOrNull { it.name == name }

    fun ensureDir(parentId: String, name: String): Doc? {
        findChild(parentId, name)?.let { if (it.isDirectory) return it }
        return try {
            val uri = DocumentsContract.createDocument(resolver, docUri(parentId), Document.MIME_TYPE_DIR, name)
                ?: return null
            val id = DocumentsContract.getDocumentId(uri)
            Doc(uri, id, name, Document.MIME_TYPE_DIR, 0, System.currentTimeMillis())
        } catch (_: Exception) {
            null
        }
    }

    /** Creates (or replaces) a file and returns an output stream to it. */
    fun createFile(parentId: String, name: String, mime: String): OutputStream? {
        findChild(parentId, name)?.let { delete(it) }
        return try {
            val uri = DocumentsContract.createDocument(resolver, docUri(parentId), mime, name) ?: return null
            resolver.openOutputStream(uri, "w")
        } catch (_: Exception) {
            null
        }
    }

    fun openInput(doc: Doc): InputStream? = try {
        resolver.openInputStream(doc.uri)
    } catch (_: Exception) {
        null
    }

    fun delete(doc: Doc): Boolean = try {
        DocumentsContract.deleteDocument(resolver, doc.uri)
    } catch (_: Exception) {
        false
    }

    /** Recursively lists files (not directories) up to [maxDepth] levels below [parentId]. */
    fun walkFiles(parentId: String = rootId, maxDepth: Int = 3, skipDirs: Set<String> = emptySet()): List<Doc> {
        val out = ArrayList<Doc>()
        fun walk(id: String, depth: Int) {
            for (d in list(id)) {
                if (d.isDirectory) {
                    if (depth < maxDepth && d.name !in skipDirs) walk(d.documentId, depth + 1)
                } else {
                    out.add(d)
                }
            }
        }
        walk(parentId, 0)
        return out
    }

    /** Random-access reader for a document, used for disc header parsing. */
    fun openByteSource(doc: Doc): ByteSource? {
        val pfd = try {
            resolver.openFileDescriptor(doc.uri, "r")
        } catch (_: Exception) {
            null
        } ?: return null
        val stream = FileInputStream(pfd.fileDescriptor)
        val channel = stream.channel
        return object : ByteSource {
            override fun size(): Long = try {
                channel.size()
            } catch (_: IOException) {
                doc.size
            }

            override fun read(offset: Long, buffer: ByteArray, bufferOffset: Int, len: Int): Int =
                readAt(channel, offset, buffer, bufferOffset, len)

            override fun close() {
                try {
                    stream.close()
                } finally {
                    pfd.close()
                }
            }
        }
    }

    companion object {
        fun readAt(channel: FileChannel, offset: Long, buffer: ByteArray, bufferOffset: Int, len: Int): Int =
            channel.read(ByteBuffer.wrap(buffer, bufferOffset, len), offset)
    }
}
