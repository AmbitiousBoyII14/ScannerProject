package com.example

import android.app.Activity
import android.content.Intent
import android.view.View
import android.view.ViewGroup
import android.webkit.WebView
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.live.BatchLabActivity
import com.example.live.LiveActivity
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.ArrayBlockingQueue
import java.util.concurrent.TimeUnit

@RunWith(AndroidJUnit4::class)
class BatchLabTest {
    @Test fun batchLabFromDrawer() {
        val inst=InstrumentationRegistry.getInstrumentation()
        val home=inst.startActivitySync(Intent(inst.targetContext,LiveActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        fun find(v:View):WebView? {if(v is WebView)return v;if(v is ViewGroup)for(i in 0 until v.childCount)find(v.getChildAt(i))?.let{return it};return null}
        fun js(activity:Activity,code:String):String {
            val q=ArrayBlockingQueue<String>(1)
            inst.runOnMainSync { find(activity.window.decorView)!!.evaluateJavascript(code){q.offer(it?:"null")} }
            return requireNotNull(q.poll(4,TimeUnit.SECONDS))
        }
        fun wait(activity:Activity,condition:String){repeat(120){if(js(activity,condition)=="true")return;Thread.sleep(100)};fail(condition+"; state="+js(activity,"JSON.stringify([document.title,document.readyState,typeof preview,document.getElementById('notice')?.textContent,document.getElementById('count')?.textContent])"))}
        val monitor=inst.addMonitor(BatchLabActivity::class.java.name,null,false)
        var lab:Activity?=null
        try {
            wait(home,"typeof nativeBack==='function' && !!document.querySelector('#importHosts').onclick")
            js(home,"document.querySelector('#burger').click();[...document.querySelectorAll('#drawer button')].find(b=>b.textContent==='Offline Batch Lab').click()")
            val a=requireNotNull(inst.waitForMonitorWithTimeout(monitor,5000));lab=a
            wait(a,"document.querySelector('#count')?.textContent==='3'")
            assertEquals("true",js(a,"typeof Client==='undefined' && typeof PreviewFiles.exportList==='function'"))
            js(a,"document.querySelector('#ssh').click();receiveLabList('a.example\\nA.example\\nb.example\\nbad:port');document.querySelector('#run').click()")
            assertEquals("true",js(a,"['count','dups','bad'].map(k=>document.getElementById(k).textContent).join(',')==='2,1,1' && document.querySelector('details').textContent.includes('Host: a.example')"))
            inst.runOnMainSync{a.finish()};lab=null
            wait(home,"document.querySelector('#home').hidden===false")
        } finally {inst.removeMonitor(monitor);inst.runOnMainSync{lab?.finish();home.finish()}}
    }
}
