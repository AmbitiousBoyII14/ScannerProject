package com.example.live

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.DocumentsContract as Docs
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.Executors

/** Only the user-selected local storage tree, never all-files permission. One ordered writer. */
internal object DebugFile {
    private val io=Executors.newSingleThreadExecutor()
    @Volatile var status="Not saving · select a folder"
        private set
    @Volatile private var app:Context?=null
    private var target:Uri?=null // accessed on io only
    private fun prefs(c:Context)=c.getSharedPreferences("debug_storage",Context.MODE_PRIVATE)
    fun init(c:Context):Boolean {
        app=c.applicationContext;val saved=prefs(c).getString("tree",null)?:return false
        val uri=Uri.parse(saved);if(c.contentResolver.persistedUriPermissions.none{it.uri==uri&&it.isWritePermission&&it.isReadPermission})return false
        configure(uri);return true
    }
    fun configure(tree:Uri){val ctx=app?:return;io.execute {
        try{
            require(tree.authority=="com.android.externalstorage.documents")
            val r=ctx.contentResolver;val id=Docs.getTreeDocumentId(tree)
            fun child(parent:Uri,name:String,mime:String):Uri{
                val pid=Docs.getDocumentId(parent);val kids=Docs.buildChildDocumentsUriUsingTree(tree,pid)
                r.query(kids,arrayOf(Docs.Document.COLUMN_DOCUMENT_ID,Docs.Document.COLUMN_DISPLAY_NAME,Docs.Document.COLUMN_MIME_TYPE),null,null,null)?.use{c->while(c.moveToNext()){if(c.getString(1)==name){require(c.getString(2)==mime || mime!="vnd.android.document/directory"&&c.getString(2)!="vnd.android.document/directory");return Docs.buildDocumentUriUsingTree(tree,c.getString(0))}}}
                return requireNotNull(Docs.createDocument(r,parent,mime,name))
            }
            val parent=Docs.buildDocumentUriUsingTree(tree,id)
            val folder=if(id.substringAfter(':').substringAfterLast('/')=="Scanner")parent else child(parent,"Scanner",Docs.Document.MIME_TYPE_DIR)
            val file=child(folder,"debug.txt","text/plain");val before=prefs(ctx).getString("tree",null);target=file;append(ctx,file,"LOG  File logging enabled")
            prefs(ctx).edit().putString("tree",tree.toString()).apply();status="Saving · "+Docs.getDocumentId(file).substringAfter(':');
            if(before!=null&&before!=tree.toString())runCatching{r.releasePersistableUriPermission(Uri.parse(before),Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION)}
        }catch(e:Exception){target=null;status="Not saving · choose a writable folder on this device";prefs(ctx).edit().remove("tree").apply()}
    }}
    private fun append(c:Context,file:Uri,line:String){val date=SimpleDateFormat("yyyy-MM-dd HH:mm:ss",Locale.US).format(Date());requireNotNull(c.contentResolver.openOutputStream(file,"wa")).use{it.write("$date  $line\n".toByteArray(Charsets.UTF_8))}}
    fun event(line:String){val ctx=app?:return;io.execute{val dest=target?:return@execute;try{append(ctx,dest,line)}catch(e:Exception){target=null;status="Write failed · reselect folder or check free space";prefs(ctx).edit().remove("tree").apply()}}}
}
