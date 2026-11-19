package com.example.live

import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.hardware.display.DisplayManager
import android.os.Build
import android.provider.Settings
import android.view.*
import android.widget.TextView
import kotlin.math.abs
import kotlin.math.roundToInt

/** Main-thread only. The window is an optional shortcut, not the owner of the tunnel. */
internal object FloatingShortcut {
    const val KEY="floatingVpnIcon"
    private var connected=false
    private var visibleScreens=0
    private var badge:TextView?=null
    private var wm:WindowManager?=null
    val shown get()=badge?.isAttachedToWindow==true
    fun enabled(c:Context)=c.getSharedPreferences("display",0).getBoolean(KEY,false) && Settings.canDrawOverlays(c)
    fun screen(c:Context,visible:Boolean){visibleScreens=(visibleScreens+if(visible)1 else -1).coerceAtLeast(0);refresh(c)}
    fun tunnel(c:Context,online:Boolean){connected=online;refresh(c)}
    fun refresh(c:Context){
        if(!connected || visibleScreens>0 || !enabled(c)){remove();return}
        if(shown)return
        try{attach(c.applicationContext)}catch(_:RuntimeException){remove();LiveState.log("DISPLAY","Floating icon unavailable; VPN continues in background")}
    }
    private fun remove(){val v=badge;badge=null;if(v!=null)runCatching{wm?.removeViewImmediate(v)};wm=null}
    @Suppress("DEPRECATION") private fun attach(app:Context){
        val type=if(Build.VERSION.SDK_INT>=26)WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY else WindowManager.LayoutParams.TYPE_PHONE
        val display=app.getSystemService(DisplayManager::class.java).getDisplay(Display.DEFAULT_DISPLAY)?:return
        val dc=app.createDisplayContext(display);val ctx=if(Build.VERSION.SDK_INT>=30)dc.createWindowContext(type,null) else dc
        val manager=ctx.getSystemService(WindowManager::class.java);val metrics=ctx.resources.displayMetrics
        val size=(48*metrics.density).roundToInt();val params=WindowManager.LayoutParams(size,size,type,WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,PixelFormat.TRANSLUCENT)
        params.gravity=Gravity.TOP or Gravity.LEFT;params.title="Scanner VPN shortcut"
        val prefs=app.getSharedPreferences("display",0)
        fun place(x:Int,y:Int){params.x=x.coerceIn(0,(metrics.widthPixels-size).coerceAtLeast(0));params.y=y.coerceIn(0,(metrics.heightPixels-size).coerceAtLeast(0))}
        place(prefs.getInt("bubbleX",metrics.widthPixels-size),prefs.getInt("bubbleY",metrics.heightPixels/3))
        val v=TextView(ctx).apply{
            text="VPN";gravity=Gravity.CENTER;textSize=13f;typeface=Typeface.DEFAULT_BOLD;setTextColor(Color.WHITE)
            background=GradientDrawable().apply{shape=GradientDrawable.OVAL;setColor(0xff007e93.toInt());setStroke((2*metrics.density).roundToInt(),Color.WHITE)}
            contentDescription="Open Scanner VPN";setOnClickListener{runCatching{app.startActivity(Intent(app,LiveActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP))}}
        }
        val slop=ViewConfiguration.get(ctx).scaledTouchSlop;var x0=0f;var y0=0f;var px=0;var py=0;var drag=false
        v.setOnTouchListener{_,e->when(e.actionMasked){
            MotionEvent.ACTION_DOWN->{x0=e.rawX;y0=e.rawY;px=params.x;py=params.y;drag=false;true}
            MotionEvent.ACTION_MOVE->{val dx=e.rawX-x0;val dy=e.rawY-y0;if(abs(dx)>slop || abs(dy)>slop)drag=true
                if(drag){place(px+dx.roundToInt(),py+dy.roundToInt());runCatching{manager.updateViewLayout(v,params)}};true}
            MotionEvent.ACTION_UP->{if(drag)prefs.edit().putInt("bubbleX",params.x).putInt("bubbleY",params.y).apply() else v.performClick();true}
            MotionEvent.ACTION_CANCEL->true;else->false}}
        manager.addView(v,params);wm=manager;badge=v
    }
}
